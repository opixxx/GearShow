---
name: code-review
description: PR 코드리뷰를 수행한다. CLAUDE.md 규칙 기반으로 아키텍처, 보안, 테스트, 성능을 검토하고 GitHub PR에 리뷰를 등록한다.
user_invocable: true
---

# GearShow 코드리뷰

이 스킬은 GitHub PR의 코드를 리뷰하고, 피드백을 GitHub PR 코멘트로 등록한다.

## 실행 방법

```
/code-review          → 현재 브랜치의 PR을 리뷰
/code-review 2        → PR #2를 리뷰
```

---

## 리뷰 절차

### Step 1: PR 컨텍스트 파악

```bash
# PR 정보 확인
gh pr view {PR번호} --json title,body,files,additions,deletions

# PR diff 확인
gh pr diff {PR번호}

# PR에 포함된 커밋 확인
gh pr view {PR번호} --json commits
```

확인할 것:
- PR 설명과 변경 목적
- 변경 파일 수와 규모 (400줄 초과 시 분할 권고)
- CI 통과 여부

---

### Step 2: 아키텍처 검토

CLAUDE.md와 architecture-pattern.md 기준으로 검토한다.

#### 계층 의존성 방향

```
Adapter → Application → Domain (이 방향만 허용)
```

| 체크 항목 | 확인 내용 |
|:---------|:---------|
| Domain 순수성 | `domain/` 패키지에 Spring, JPA 어노테이션 없는지 (Lombok @Getter, @Builder만 허용) |
| Port/Adapter 패턴 | Port 인터페이스 없이 Adapter 직접 참조하지 않는지 |
| DTO 분리 | Entity가 Controller에서 직접 반환되지 않는지, record 타입 사용하는지 |
| 의존성 주입 | @Autowired 필드 주입 없이 @RequiredArgsConstructor 사용하는지 |
| 트랜잭션 위치 | @Transactional이 Application Service에만 있는지 |
| Cross-Aggregate | 다른 Aggregate 간 JPA 관계 매핑(@ManyToOne 등) 없이 ID 참조만 사용하는지 |

---

### Step 3: 코드 품질 검토

#### 도메인 모델

| 체크 항목 | 확인 내용 |
|:---------|:---------|
| 정적 팩토리 메서드 | public 생성자 대신 create() 등 의미 있는 팩토리 메서드 사용하는지 |
| 불변 객체 | final 필드, setter 없는지 |
| 검증 로직 | 팩토리 메서드 내에서 validate 수행하는지 |
| null 주입 금지 | 필수 필드에 null 전달하지 않는지 |
| 상태 전이 | 유효하지 않은 상태 전이를 도메인에서 방어하는지 |

#### 네이밍

| 대상 | 규칙 | 예시 |
|:----|:----|:----|
| UseCase (Inbound Port) | `{Action}UseCase` | `CreateShowcaseUseCase` |
| Outbound Port | `{Entity}Port` | `ShowcasePort` |
| Service | `{Action}Service` | `CreateShowcaseService` |
| Controller | `{Entity}Controller` | `ShowcaseController` |
| JPA Entity | `{Entity}JpaEntity` | `ShowcaseJpaEntity` |
| Domain Exception | `{Reason}{Entity}Exception` | `NotFoundShowcaseException` |
| ErrorCode | `{DOMAIN}_{REASON}` | `SHOWCASE_NOT_FOUND` |

#### 메서드 규칙

- 단일 책임: 한 가지 일만 수행
- 최대 20줄
- BDD 스타일 테스트: Given-When-Then 주석

#### 한글 규칙

- 주석, Javadoc: **한글**
- 로그 메시지: **한글**
- 예외 메시지 (ErrorCode): **한글**
- Bean Validation 메시지: **한글**
- 변수/메서드명: **영문**

---

### Step 4: 보안 검토

| 체크 항목 | 확인 내용 |
|:---------|:---------|
| 인증/인가 | 보호 엔드포인트에 인증 확인이 있는지 |
| 입력 검증 | 사용자 입력에 @Valid, @NotBlank 등 Validation 적용되는지 |
| SQL Injection | JPA 파라미터 바인딩 사용하는지 (문자열 연결 쿼리 금지) |
| 민감 정보 노출 | .env, API 키, 비밀번호가 코드에 하드코딩되지 않는지 |
| 에러 메시지 | 스택 트레이스나 내부 정보가 API 응답에 노출되지 않는지 |
| JWT | 토큰 검증 로직이 올바른지, 만료 확인이 되는지 |

---

### Step 5: 테스트 검토

test-strategy.md 기준으로 검토한다.

| 체크 항목 | 확인 내용 |
|:---------|:---------|
| 인수 테스트 | 신규 기능에 Cucumber 시나리오가 있는지 |
| 통합 테스트 | 신규/변경된 Adapter에 통합 테스트가 있는지 |
| Happy + Unhappy | Happy Path와 Edge Case 모두 있는지 |
| 테스트 격리 | 시간 의존 로직, 공유 상태 없는지 |
| BDD 스타일 | Given-When-Then 주석이 있는지 |
| @DisplayName | 한글로 테스트 목적이 명확한지 |

---

### Step 6: 성능 검토

| 체크 항목 | 확인 내용 |
|:---------|:---------|
| N+1 문제 | 연관 엔티티 조회 시 Fetch Join이나 Batch Size 고려했는지 |
| 불필요한 조회 | 사용하지 않는 데이터까지 조회하지 않는지 |
| 페이징 | 대량 데이터 조회에 커서 기반 페이징 적용했는지 |
| 트랜잭션 범위 | 트랜잭션이 불필요하게 길지 않은지 |

---

### Step 7: 리뷰 작성

#### 심각도 레이블

| 레이블 | 의미 | 예시 |
|:------|:----|:----|
| 🔴 `[blocking]` | 머지 전 반드시 수정 | 보안 취약점, 버그, 아키텍처 위반 |
| 🟡 `[important]` | 수정 권장, 사유 있으면 유지 가능 | 성능 우려, 네이밍 개선 |
| 🟢 `[nit]` | 사소한 개선, 머지에 영향 없음 | 코드 스타일, 주석 보완 |
| 💡 `[suggestion]` | 대안 제시, 선택 사항 | 다른 패턴/접근법 |
| 🎉 `[praise]` | 잘한 점 칭찬 | 좋은 설계, 깔끔한 코드 |

#### 피드백 규칙

```
❌ "이거 잘못됨"
✅ "🔴 [blocking] 이 쿼리는 SQL Injection에 취약합니다. 
    파라미터 바인딩을 사용해주세요:
    @Query("SELECT u FROM User u WHERE u.nickname = :nickname")"

❌ "왜 이 패턴 안 씀?"
✅ "💡 [suggestion] Repository 패턴을 사용하면 테스트가 
    더 쉬워질 수 있습니다. 어떻게 생각하시나요?"

❌ "변수명 바꿔"
✅ "🟢 [nit] `uc` 대신 `userCount`가 더 명확할 것 같습니다."
```

#### 질문형 피드백 활용

```
❌ "빈 리스트 처리 안 됨"
✅ "items가 빈 배열이면 어떻게 동작하나요?"

❌ "에러 핸들링 없음"
✅ "API 호출이 실패하면 어떻게 처리되나요?"
```

---

### Step 8: GitHub PR에 리뷰 등록

```bash
# 전체 리뷰 코멘트 등록
gh pr review {PR번호} --comment --body "리뷰 내용"

# 승인
gh pr review {PR번호} --approve --body "리뷰 내용"

# 변경 요청
gh pr review {PR번호} --request-changes --body "리뷰 내용"
```

#### 리뷰 본문 형식

```markdown
## 코드리뷰 결과

### 요약
[변경 사항 요약 및 전체 평가]

### 잘한 점
🎉 [칭찬할 부분]

### 수정 필요
🔴 [blocking 항목]
🟡 [important 항목]

### 제안
💡 [suggestion 항목]
🟢 [nit 항목]

### 체크리스트
- [ ] 아키텍처 패턴 준수
- [ ] 보안 이슈 없음
- [ ] 테스트 충분
- [ ] 성능 우려 없음
- [ ] 네이밍/컨벤션 준수

### 판정
✅ Approve / 🔄 Request Changes / 💬 Comment
```

---

## 판정 기준

| 판정 | 조건 |
|:----|:----|
| ✅ **Approve** | blocking 없음, 전체적으로 양호 |
| 💬 **Comment** | blocking 없음, nit/suggestion만 있음 |
| 🔄 **Request Changes** | blocking이 1개 이상 존재 |
