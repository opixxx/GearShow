# EXEC_PLAN: catalog-search-foundation

- **Type**: feature
- **Status**: completed (백엔드 한정 — crawler 작업은 후속 PR 로 분리)
- **Risk**: Caution (도메인 변경 다수 + DB 스키마 변경 + UniformSpec.kitType nullable 화)
- **Created**: 2026-05-04
- **Branch**: feature/catalog-search-foundation
- **Scope decision (2026-05-04)**: 옵션 1 — 본 PR 은 백엔드 + ADR-016 으로 한정. crawler 변경 (Step 6~8 + smoke) 은 PR #74 (Kream crawler) 머지 후 별도 후속 PR + ADR-017 로 분리. 이유: PR #74 가 main 미머지 상태라 본 PR 에 crawler 변경을 쌓으면 직선 의존이 발생 — 백엔드 PR 을 먼저 머지하는 편이 흐름 단순.

---

## 1. 목표

향후 도입할 Showcase 검색 기능(별도 PR)을 위한 **카탈로그 측 한국어 데이터 + crawler 유니폼 지원** 기반을 한 번에 구축한다. 사용자가 "머큐리얼" / "맨유" 같은 한국어 키워드로 검색해도 매칭 가능하도록 **catalog 측에 한국어 alias 컬럼을 추가하고 crawler 가 그것을 채우게** 한다.

## 2. 범위

### In
- `StudType` enum 확장: **MG, HG 추가** (기존 5개 → 7개)
- `KitType` 정책 변경: **nullable 화** (빈티지 유니폼 케이스 — Manchester United 1988/90 등)
- `CatalogItem` 도메인 + JpaEntity: `fullNameKo`, `fullNameEn` 컬럼 추가
- `BootsSpec` 도메인 + JpaEntity: `siloNameKo` 컬럼 추가
- `UniformSpec` 도메인 + JpaEntity: `clubNameKo` 컬럼 추가 + `kitType` nullable 화
- bulk-import API DTO/Command 확장 (위 신규 필드 받기)
- `CreateCatalogItemService` 흐름에서 신규 필드 전달
- crawler:
  - `--category uniform` 지원 (기존 `NotImplementedError` 해제)
  - `<meta name="keywords">` 파싱 → `[modelCode, 한국어 풀네임, 영문 풀네임]` 분리
  - 축구화/유니폼 둘 다 한국어 alias 추출
  - 사전: `silos.yaml` 보강 + `brands.yaml` 신규 + `clubs.yaml` 신규 (club ↔ 한국어 ↔ league)
- ADR-016 작성 (enum 확장 + 한국어 컬럼 + KitType nullable + crawler 유니폼)

### Out
- Showcase 도메인 변경 (search_text 컬럼 추가 등) — 후속 PR-3
- 검색 API (`?keyword=`) — 후속 PR-4
- 프론트 폼 단순화 (B-필수만) — 별도 Flutter PR
- ES/OpenSearch 도입 — Phase 3 결정 후

## 3. 변경 대상

### 백엔드 — 도메인 (수정)
- `catalog/domain/vo/StudType.java`
- `catalog/domain/vo/KitType.java` (변경 없음 — 도메인 enum 자체는 그대로, nullable 은 사용처 정책)
- `catalog/domain/model/CatalogItem.java` — fullNameKo/En 필드 + create() 시그니처
- `catalog/domain/model/BootsSpec.java` — siloNameKo 필드 + create()
- `catalog/domain/model/UniformSpec.java` — clubNameKo + kitType nullable 화 (validate 로직 수정)

### 백엔드 — Adapter Persistence (수정)
- `catalog/adapter/out/persistence/CatalogItemJpaEntity.java` — full_name_ko, full_name_en 컬럼
- `catalog/adapter/out/persistence/BootsSpecJpaEntity.java` — silo_name_ko 컬럼
- `catalog/adapter/out/persistence/UniformSpecJpaEntity.java` — club_name_ko 컬럼 + kit_type nullable
- `catalog/adapter/out/persistence/CatalogItemMapper.java`
- `catalog/adapter/out/persistence/BootsSpecMapper.java`
- `catalog/adapter/out/persistence/UniformSpecMapper.java`

### 백엔드 — Application (수정)
- `catalog/application/dto/CreateCatalogItemCommand.java` — fullNameKo/En, BootsSpecCommand.siloNameKo, UniformSpecCommand.clubNameKo + kitType nullable
- `catalog/application/service/CreateCatalogItemService.java` — 신규 필드 전달

### 백엔드 — Adapter Web (수정)
- `catalog/adapter/in/web/dto/CreateCatalogItemRequest.java` — fullNameKo/En + spec request 확장 + UniformSpec.kitType `@NotNull` 제거

### 백엔드 — 테스트 (수정/신규)
- `catalog/domain/model/CatalogItemTest.java`
- `catalog/domain/model/BootsSpecTest.java` — MG/HG 케이스 + siloNameKo
- `catalog/domain/model/UniformSpecTest.java` — kitType null 허용 + clubNameKo
- `catalog/application/service/CatalogItemServiceIntegrationTest.java` — 신규 필드 검증

### 백엔드 — 신규 ADR
- `docs/architecture/adr/ADR-016-catalog-search-foundation.md`

### crawler — Python
- `tools/kream-crawler/kream_crawler/product_parser.py` — keywords 파싱 + 한국어 추출
- `tools/kream-crawler/kream_crawler/normalizer.py` — 축구화/유니폼 분기 + 한국어 alias 매핑
- `tools/kream-crawler/kream_crawler/cli.py` — `--category uniform` 지원, NotImplementedError 해제
- `tools/kream-crawler/kream_crawler/dictionaries/silos.yaml` — 보강 (alias 미흡 항목 추가)
- `tools/kream-crawler/kream_crawler/dictionaries/brands.yaml` — 신규
- `tools/kream-crawler/kream_crawler/dictionaries/clubs.yaml` — 신규 (club ↔ 한국어 alias ↔ league)
- `tools/kream-crawler/tests/test_normalizer.py` — 유니폼 케이스 추가
- `tools/kream-crawler/tests/test_product_parser.py` — keywords 파싱 케이스
- `tools/kream-crawler/tests/fixtures/sample_uniform_product.html` — 신규
- `tools/kream-crawler/README.md` — 유니폼 사용법 추가

## 4. 접근

### 도메인 변경 — 마이그레이션 / 호환성

**ddl-auto: update** 환경에서 신규 컬럼 자동 추가. 단:
- `uniform_spec.kit_type` — 기존 `NOT NULL` 제약을 nullable 로 변경하는 것은 Hibernate 가 자동으로 안 할 수도. **운영 적용 시 수동 ALTER 필요**:
  ```sql
  ALTER TABLE uniform_spec MODIFY kit_type VARCHAR(20) NULL;
  ```
- 운영 체크리스트 항목으로 ADR-016 에 명시

### CreateCatalogItem 흐름 변경

기존:
```java
CatalogItem.create(category, brand, modelCode, officialImageUrl)
BootsSpec.create(catalogItemId, studType, siloName, releaseYear, surfaceType, extraSpecJson)
UniformSpec.create(catalogItemId, clubName, season, league, kitType, extraSpecJson)
```

변경 후:
```java
CatalogItem.create(category, brand, modelCode, officialImageUrl, fullNameKo, fullNameEn)
BootsSpec.create(catalogItemId, studType, siloName, siloNameKo, releaseYear, surfaceType, extraSpecJson)
UniformSpec.create(catalogItemId, clubName, clubNameKo, season, league, kitType, extraSpecJson)
                                                                       ↑ nullable
```

### crawler 한국어 추출 — keywords 메타 파싱

```python
# 입력 예시 (실측):
# "AT5889-174,나이키 프리미어 3 FG 화이트 메탈릭 골드,Nike Premier 3 FG White Metallic Gold"
def parse_keywords(keywords: str) -> tuple[str, str | None, str | None]:
    parts = [p.strip() for p in keywords.split(",")]
    if len(parts) >= 3:
        return parts[0], parts[1], parts[2]   # modelCode, name_ko, name_en
    return parts[0] if parts else None, None, None
```

### 사전 — clubs.yaml 구조

```yaml
- canonical: "Manchester United"
  aliases: ["맨체스터 유나이티드", "맨유"]
  league: "EPL"
- canonical: "Liverpool"
  aliases: ["리버풀"]
  league: "EPL"
- canonical: "Korea"
  aliases: ["대한민국", "한국"]
  league: null   # 국가대표 — league 없음
- canonical: "England"
  aliases: ["잉글랜드"]
  league: null
# ... 30~40개 시작
```

### 사전 — brands.yaml 구조

```yaml
- canonical: "Nike"
  aliases: ["나이키"]
- canonical: "Adidas"
  aliases: ["아디다스"]
- canonical: "Puma"
  aliases: ["푸마"]
- canonical: "Mizuno"
  aliases: ["미즈노"]
- canonical: "New Balance"
  aliases: ["뉴발란스"]
- canonical: "Asics"
  aliases: ["아식스"]
- canonical: "Umbro"
  aliases: ["엄브로"]
- canonical: "Diadora"
  aliases: ["디아도라"]
```

### crawler 유니폼 normalize 로직

```python
def to_uniform_item(raw: RawProduct, clubs: list[Club], brands: list[Brand]) -> CatalogItem:
    name_en = raw.get("name_en") or raw.get("name") or ""
    name_ko = raw.get("name_ko") or ""
    
    # 클럽 매칭
    club = match_club(name_en, name_ko, clubs)
    
    # 시즌 추출 (정규식)
    season_match = re.search(r"(\d{2,4})/(\d{2,4})", name_en)
    season = f"{season_match.group(1)}/{season_match.group(2)}" if season_match else None
    
    # 킷 타입 추출 (영문/한국어 모두 시도)
    kit_type = extract_kit_type(name_en) or extract_kit_type_ko(name_ko)
    
    return CatalogItem(
        category="UNIFORM",
        brand=raw.get("brand"),
        modelCode=raw.get("style_code"),
        fullNameKo=name_ko,
        fullNameEn=name_en,
        uniformSpec={
            "clubName": club.canonical if club else None,
            "clubNameKo": club.aliases[0] if club and club.aliases else None,
            "season": season,
            "league": club.league if club else None,
            "kitType": kit_type,   # nullable
        },
    )
```

## 5. 단계 (Steps)

### Step 1: domain-stud-type-and-fields

**작업**:
- `StudType` enum 에 MG, HG 추가
- `CatalogItem` 도메인에 `fullNameKo`, `fullNameEn` 필드 + create() 시그니처 변경
- `BootsSpec` 도메인에 `siloNameKo` 필드 + create()
- `UniformSpec` 도메인에 `clubNameKo` 필드 + `kitType` nullable 화 (validate 메서드에서 kitType null 검증 제거)
- `Builder` 갱신

**AC**:
```bash
cd backend
./gradlew compileJava
```

### Step 2: persistence-and-mapper

**작업**:
- JpaEntity 3개 갱신 (full_name_ko/en, silo_name_ko, club_name_ko 컬럼 추가, kit_type nullable=true)
- Mapper 3개 갱신
- `@Column` 길이/제약 적절히 설정 (full_name_ko/en: VARCHAR(255), nullable=true)

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew archTest
```

### Step 3: application-and-adapter-dto

**작업**:
- `CreateCatalogItemCommand` 확장 (fullNameKo/En, BootsSpecCommand.siloNameKo, UniformSpecCommand.clubNameKo + kitType nullable)
- `CreateCatalogItemRequest` 확장 (req DTO 동일 필드 추가, `UniformSpecRequest.kitType` 의 `@NotNull` 제거)
- `CreateCatalogItemService` 흐름에서 신규 필드 전달
- 단위 테스트 갱신

**AC**:
```bash
cd backend
./gradlew test --tests "*CreateCatalogItemServiceTest*"
```

### Step 4: domain-tests

**작업**:
- `BootsSpecTest`: MG/HG 케이스 + siloNameKo 검증
- `UniformSpecTest`: kitType null 허용 (정상 동작) + clubNameKo 검증
- `CatalogItemTest`: fullNameKo/En 검증

**AC**:
```bash
cd backend
./gradlew test --tests "*Test"
```

### Step 5: integration-tests-and-bulk-import

**작업**:
- `CatalogItemServiceIntegrationTest`: 신규 필드 포함한 등록 → 조회 흐름 검증
- `BulkImportCatalogItemServiceIntegrationTest`: 새 필드 포함 페이로드 검증
- 기존 통합 테스트 회귀 없음 확인

**AC**:
```bash
cd backend
./gradlew test
```

### Step 6: crawler-keywords-extraction

**작업**:
- `product_parser.py` — `parse_keywords()` 헬퍼 + `RawProduct.name_ko`, `name_en` 채우기
- 단위 테스트 (fixture HTML 추가)

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_product_parser.py
```

### Step 7: crawler-dictionaries

**작업**:
- `brands.yaml` 신규 — 8개 브랜드
- `clubs.yaml` 신규 — 30~40개 club + 국가대표 (Manchester United/Liverpool/Real Madrid/Barcelona/Bayern Munich/Korea/England/Brazil/Argentina 등)
- `silos.yaml` 보강 — 운영 후 누락 항목 (Mizuno Alpha 등) 추가

### Step 8: crawler-uniform-support

**작업**:
- `cli.py` — `--category uniform` 지원, NotImplementedError 해제
- `normalizer.py` — `to_bulk_import_item` 분기 (BOOTS / UNIFORM), `to_uniform_item()` 신규
- 시즌 정규식 + kitType 영/한 추출 헬퍼
- 단위 테스트 — 유니폼 케이스 (Manchester United Home/Away, 빈티지 1988/90)

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest
.venv/bin/python -m kream_crawler --category uniform --limit 5 --output /tmp/uniform.json
# 결과 확인: brand/modelCode 100%, clubName 매칭률 ≥ 70%, season 추출률 ≥ 80%
```

### Step 9: adr-and-final-build

**작업**:
- ADR-016 작성
- 운영 체크리스트 (kit_type nullable ALTER, 신규 컬럼 확인)

**AC**:
```bash
cd backend
./gradlew build
```

## 6. 테스트 계획

- **단위 테스트** (백엔드): StudType MG/HG, kitType null 허용, 신규 필드 검증
- **단위 테스트** (crawler): keywords 파싱, club/brand 사전 매칭, 유니폼 normalize
- **통합 테스트**: bulk-import API 신규 필드 포함, BOOTS/UNIFORM 둘 다
- **End-to-End** (crawler): 실제 Kream 호출 30건 (축구화 + 유니폼 각 15건) — 매칭률 측정
- **추가 검증**: ArchUnit (도메인 ← Spring/JPA 의존 0건 유지), Cucumber 회귀 없음

## 7. 완료 기준

```bash
cd backend
./gradlew build
cd ../tools/kream-crawler
.venv/bin/pytest
```

추가:
- [ ] code-reviewer Critical 0건
- [ ] architecture-reviewer Critical 0건
- [ ] database-optimizer Critical 0건
- [ ] ADR-016 작성 완료
- [ ] crawler 실제 호출 smoke — 축구화 silo 매칭 ≥ 70%, 유니폼 club 매칭 ≥ 70%
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략

- DB 스키마: 신규 컬럼 + kit_type nullable 변경 — `git revert` 후 운영 DB 에서 컬럼 DROP / kit_type 다시 NOT NULL (사전 데이터 모두 NOT NULL 인지 확인 필수)
- crawler 변경: revert 로 충분 (외부 영향 없음)
- 운영 적용 시 사전 준비:
  - [ ] `ALTER TABLE uniform_spec MODIFY kit_type VARCHAR(20) NULL;` 실행
  - [ ] `SHOW COLUMNS FROM catalog_item;` / `boots_spec` / `uniform_spec` 으로 신규 컬럼 자동 생성 확인 (ddl-auto: update)

## 9. 후속 PR

- **PR-3**: `showcase.search_text` 컬럼 + Showcase 등록 시 채우기 (catalog 데이터 + 사전 매칭 한국어 alias 모두 합침)
- **PR-4**: `?keyword=` 검색 API + LIKE 쿼리 + Cucumber
- **별도 Flutter PR**: 직접 입력 폼 단순화 (B-필수만 — 카테고리 + 브랜드 + title + description)
- **운영 후**: 사일로/클럽 사전 보강, MySQL FULLTEXT(n-gram) 인덱스, ES 도입 결정
