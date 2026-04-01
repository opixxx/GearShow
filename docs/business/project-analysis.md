# GearShow 프로젝트 현황 분석

> 작성일: 2026-03-31
> 분석 기준: 커밋 `b184a7c` (main 브랜치)

---

## 1. 프로젝트 개요

축구 장비(축구화, 유니폼 등)를 3D 모델 기반으로 시각화하고, 사용자 경험 데이터를 함께 제공하는 쇼케이스 플랫폼이다. 사용자 간 직거래 및 안전거래를 지원한다.

**기술 스택**: Java 21, Spring Boot 3.5.0, Spring Data JPA, MySQL 8.0, Gradle

---

## 2. 현재 프로젝트 구조

```
GearShow/
├── CLAUDE.md                              # 개발 가이드라인
├── docker-compose.yml                     # MySQL 8.0 컨테이너
├── backend/
│   ├── build.gradle                       # 의존성 관리
│   └── src/
│       ├── main/java/.../
│       │   ├── GearShowApplication.java   # Spring Boot 진입점
│       │   └── health/adapter/in/web/
│       │       └── HealthController.java  # 헬스체크 API (유일한 구현체)
│       ├── main/resources/
│       │   └── application.yml            # 메인 설정 (ddl-auto: validate)
│       └── test/
│           ├── java/.../
│           │   ├── CucumberIntegrationTest.java
│           │   ├── CucumberSpringConfiguration.java
│           │   ├── steps/HealthCheckStepDefinitions.java
│           │   └── support/
│           │       ├── TestApiClient.java
│           │       ├── TestResponse.java
│           │       └── ScenarioContext.java
│           └── resources/
│               ├── application-test.yml   # 테스트 설정 (Testcontainers)
│               └── features/health-check.feature
├── docs/
│   ├── business/
│   │   └── biz-logic.md                  # 비즈니스 로직 정의
│   ├── diagram/
│   │   └── schema.md                     # ERD (5개 Aggregate, 13개 테이블)
│   └── spec/
│       ├── api-spec.md                   # API 명세 (7개 도메인, 25+ 엔드포인트)
│       ├── architecture-pattern.md       # 헥사고날 아키텍처 규칙
│       └── coding-convention.md          # 코딩 컨벤션
└── frontend/                             # 비어 있음
```

---

## 3. 구현 현황

### 3-1. 완료 항목

| 영역 | 항목 | 상세 |
|:----|:-----|:-----|
| 프로젝트 설정 | Spring Boot 초기화 | Java 21, Spring Boot 3.5.0, Gradle |
| 인프라 | Docker Compose | MySQL 8.0, UTF8MB4, 영구 볼륨 |
| 인프라 | 메인/테스트 설정 파일 | `application.yml`, `application-test.yml` |
| API | 헬스체크 | `GET /api/v1/health` → `{"status": "UP"}` |
| 테스트 인프라 | Cucumber + Testcontainers | BDD 인수테스트 프레임워크 전체 구성 |
| 테스트 인프라 | 테스트 지원 유틸 | TestApiClient, TestResponse, ScenarioContext |
| 문서 | 개발 가이드라인 | CLAUDE.md (역할, 규칙, 금지사항) |
| 문서 | API 명세 | 7개 도메인, 25+ 엔드포인트 정의 |
| 문서 | ERD | 5개 Aggregate, 13개 테이블, 관계 정의 |
| 문서 | 아키텍처 패턴 | 헥사고날 아키텍처 계층별 규칙 |
| 문서 | 코딩 컨벤션 | 네이밍, 스타일, 언어 정책 |
| 문서 | 비즈니스 로직 | 도메인별 규칙, 상태 전이, 크로스 도메인 규칙 |

### 3-2. 미구현 항목

| 영역 | 항목 | 비고 |
|:----|:-----|:-----|
| 도메인 모델 | User, Showcase, CatalogItem 등 | 전체 미구현 |
| 애플리케이션 계층 | UseCase, Service | 전체 미구현 |
| 어댑터 계층 | Controller, JPA Entity, Repository | HealthController 외 전체 미구현 |
| 인증/인가 | OAuth2 + JWT | Spring Security 미설정 |
| 외부 연동 | 3D 모델 생성 (Tripo 등) | 미구현 |
| 외부 연동 | 결제 (Toss, Kakao) | 미구현 |
| 예외 처리 | CustomException, ErrorCode, GlobalExceptionHandler | 미구현 |
| DB | DDL 스크립트 / Flyway 마이그레이션 | 미구현 |
| 프론트엔드 | 전체 | 빈 디렉토리 |

---

## 4. 아키텍처 설계 분석

### 4-1. 채택된 아키텍처

**헥사고날 아키텍처 (Ports & Adapters)**

```
Adapter (in/web)  →  Application (port/in, service)  →  Domain (model, vo, policy)
                     Application (port/out)           ←  Adapter (out/persistence, external)
```

- 의존 방향: `Adapter → Application → Domain` (안쪽으로만)
- Domain 계층은 Spring/JPA 의존 금지 (Lombok @Getter, @Builder만 허용)
- Aggregate 간 참조는 ID만 보유 (JPA 관계 매핑 금지)

### 4-2. 도메인 구조 (5개 Aggregate)

| Aggregate Root | 종속 엔티티 | 핵심 기능 |
|:--------------|:-----------|:---------|
| USER | AUTH_ACCOUNT | 소셜 로그인, 프로필, 휴대폰 인증 |
| CATALOG_ITEM | BOOTS_SPEC, UNIFORM_SPEC | 공식 장비 카탈로그 |
| SHOWCASE | SHOWCASE_IMAGE, SHOWCASE_3D_MODEL, MODEL_SOURCE_IMAGE, SHOWCASE_COMMENT | 사용자 쇼케이스 (핵심 도메인) |
| CHAT_ROOM | CHAT_MESSAGE | 1:1 채팅 |
| TRANSACTION | PAYMENT | 안전거래 (직거래는 상태 전이 없음) |

### 4-3. 설계 특이사항

- **커서 기반 페이징**: offset 방식 대신 커서 기반 페이징 채택 (대량 데이터 성능 고려)
- **거래 이원화**: 직거래(DIRECT)는 쇼케이스 상태 변경(`SOLD`)만, 안전거래(ESCROW)는 TRANSACTION + PAYMENT 상태 전이
- **3D 모델 비동기 생성**: 쇼케이스 등록 시 소스 이미지와 함께 비동기 요청, 외부 서비스 연동
- **소프트 삭제**: User, Showcase, Comment 모두 상태 기반 소프트 삭제

---

## 5. 테스트 전략 분석

### 5-1. 현재 테스트 인프라

| 구성 요소 | 기술 | 역할 |
|:---------|:-----|:-----|
| BDD 프레임워크 | Cucumber 7.22.0 | Gherkin 시나리오 기반 인수테스트 |
| 컨테이너 | Testcontainers (MySQL 8.0) | 실제 DB 환경 테스트 |
| HTTP 클라이언트 | TestApiClient (TestRestTemplate 래핑) | API 호출 추상화 |
| 상태 관리 | ScenarioContext (@ScenarioScope) | 시나리오 간 데이터 격리 |
| 응답 래핑 | TestResponse | 상태코드/바디/필드 추출 |

### 5-2. 테스트 확장 준비도

- **glue 패키지**가 `com.gearshow.backend`로 넓게 설정되어 StepDefinitions 추가만으로 확장 가능
- **TestApiClient**가 인증(Bearer) 지원을 포함하고 있어 인증 시나리오 테스트 가능
- **application-test.yml**에 `TC_REUSABLE=true` 설정으로 컨테이너 재사용 가능

### 5-3. 보완 필요 사항

- 단위 테스트 전략 미수립 (도메인 모델, 서비스 계층)
- 시나리오 간 데이터 정리 전략 필요 (`ddl-auto: create-drop`은 테스트 스위트 단위 초기화)
- 테스트 전략 문서(`test-strategy.md`) 미작성

---

## 6. 의존성 분석

### 6-1. 현재 build.gradle 의존성

```
[프로덕션]
├── spring-boot-starter-web          # REST API
├── spring-boot-starter-data-jpa     # JPA/Hibernate
├── spring-boot-starter-validation   # Bean Validation
├── mysql-connector-j                # MySQL 드라이버
└── lombok                           # 보일러플레이트 제거

[테스트]
├── spring-boot-starter-test         # JUnit 5, MockMvc 등
├── testcontainers (core, mysql, junit-jupiter)
├── cucumber (java, spring, junit-platform-engine) v7.22.0
└── junit-platform-suite
```

### 6-2. 향후 추가 필요 의존성 (예상)

| 의존성 | 용도 | 시점 |
|:------|:-----|:-----|
| `spring-boot-starter-security` | 인증/인가 | User 도메인 구현 시 |
| `spring-boot-starter-oauth2-client` | 소셜 로그인 | User 도메인 구현 시 |
| `jjwt` 또는 `nimbus-jose-jwt` | JWT 발급/검증 | User 도메인 구현 시 |
| `spring-boot-starter-websocket` | 실시간 채팅 | Chat 도메인 구현 시 |
| `spring-cloud-starter-aws` 또는 S3 SDK | 이미지/파일 업로드 | Showcase 도메인 구현 시 |
| `flyway-core` 또는 `liquibase` | DB 마이그레이션 | 첫 도메인 구현 시 |

---

## 7. 문서 완성도 분석

| 문서 | 상태 | 커버리지 | 비고 |
|:----|:-----|:--------|:-----|
| CLAUDE.md | 완성 | 높음 | 역할, 규칙, 금지사항 상세 |
| api-spec.md | 초안 완성 | 높음 | 7개 도메인 엔드포인트 정의, Chat/Transaction API 미포함 |
| schema.md | 초안 완성 | 높음 | 13개 테이블, Aggregate 경계 명확 |
| architecture-pattern.md | 초안 완성 | 높음 | 계층별 규칙, 체크리스트 포함 |
| coding-convention.md | 초안 완성 | 보통 | 네이밍/스타일 정의, 실제 코드 예시 부족 |
| biz-logic.md | 초안 완성 | 보통 | TBD 13개 항목, Chat 섹션 미작성 |

---

## 8. 현재 단계 평가

### 8-1. 프로젝트 성숙도

```
[■■■■■□□□□□] 설계/문서화 단계 (50%)
[■□□□□□□□□□] 구현 단계 (5%)
[■□□□□□□□□□] 테스트 단계 (10% - 인프라만 구축)
[□□□□□□□□□□] 배포/운영 단계 (0%)
```

**현재 위치**: 설계와 문서화가 상당히 진행된 상태이며, 구현은 헬스체크와 테스트 인프라만 완료된 초기 단계이다.

### 8-2. 강점

- **문서 선행**: 구현 전에 API 명세, ERD, 아키텍처, 비즈니스 로직을 체계적으로 정의
- **아키텍처 일관성**: 헥사고날 아키텍처 규칙이 명확하고, CLAUDE.md를 통해 일관된 코드 품질 유지 가능
- **테스트 인프라 선구축**: Cucumber + Testcontainers 기반 인수테스트 프레임워크가 준비되어 즉시 TDD/BDD 개발 가능
- **Aggregate 설계**: DDD 기반 Aggregate 경계가 명확하여 도메인 간 결합도가 낮음

### 8-3. 리스크/개선 포인트

| 리스크 | 영향도 | 설명 |
|:------|:------|:-----|
| 비즈니스 규칙 미확정 (TBD 13건) | 중 | 구현 중 의사결정 지연 가능 |
| Chat 도메인 설계 미완 | 중 | 실시간 통신 아키텍처 결정 필요 (WebSocket vs SSE) |
| DB 마이그레이션 도구 미선정 | 중 | 스키마 변경 관리 전략 필요 |
| 인증/인가 미설계 | 높 | 대부분의 API가 인증 필요, 우선 구현 대상 |
| 외부 서비스 연동 미정의 | 중 | 3D 모델 생성, 결제 제공자 연동 인터페이스 정의 필요 |

---

## 9. 권장 구현 로드맵

### Phase 1 - 핵심 인프라 (공통)

```
1. CustomException + ErrorCode 체계 구축
2. GlobalExceptionHandler 구현
3. 공통 응답 래퍼 (ApiResponse) 구현
4. DB 마이그레이션 도구 도입 (Flyway 권장)
```

### Phase 2 - User 도메인

```
1. User Aggregate 구현 (도메인 모델 → JPA Entity → Repository)
2. OAuth2 소셜 로그인 (Kakao 우선)
3. JWT 발급/검증
4. 프로필 CRUD
```

### Phase 3 - Catalog + Showcase 도메인

```
1. CatalogItem Aggregate 구현
2. Showcase Aggregate 구현 (이미지 포함)
3. Showcase CRUD API
4. 댓글 기능
```

### Phase 4 - 거래 + 채팅

```
1. Chat 도메인 설계 확정 및 구현
2. 직거래 흐름 (SOLD 상태 전환)
3. 안전거래 + 결제 연동
```

### Phase 5 - 3D 모델 + 고도화

```
1. 3D 모델 생성 외부 서비스 연동
2. 이미지 업로드 (S3)
3. 검색/필터링 고도화
```
