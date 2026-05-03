# ADR-014: Admin RBAC — 별도 BC 분리 + email/password 인증

- **Status**: Accepted
- **Date**: 2026-05-03
- **Deciders**: opix
- **Related**: PR #71 (catalog bulk-import API), ADR-008 (chat JWT verification port)

## Context

PR #71 에서 `POST /api/admin/catalog/bulk-import` 등 admin 전용 엔드포인트가 **가드 없이 머지**되었다 (사용자 결정 Q1-B — RBAC 가드를 후속 PR + ADR 로 분리). 운영 main 브랜치에 가드 없는 admin 엔드포인트가 노출된 상태이므로 단기간 내 가드 도입이 필요하다.

가드 도입 시 다음 설계 결정이 필요했다:

1. admin 정보를 어디에 둘 것인가 — 기존 `User` 도메인 확장 vs 별도 BC
2. 인증 방식 — OAuth 흐름 확장 vs 별도 인증 채널
3. 부트스트랩 — 첫 admin 을 어떻게 만들 것인가
4. JWT 토큰 — 기존 인프라 재사용 vs 분리

## 사전 발견 (조사 결과)

- **`User` 도메인에 email 필드 없음**: GearShow 의 정책은 "OAuth identity provider(카카오/애플) 에 email 신뢰를 위임" — 즉 email 을 GearShow 의 진실의 원천으로 두지 않음
- **`OAuthUserInfo` 에 email 필드 없음**: 카카오는 `nickname/profile_image` 만 추출, 애플은 id_token 의 email 을 nickname 으로 사용 (자리 없어 버려짐)
- **카카오 OAuth email 동의 항목**: 비즈채널 설정 활성화 + 사용자 동의 필요 — 항상 보장되지 않음
- **JWT 현재 claim**: `sub`(userId), `iat`, `exp` 만. authorities 미설정 (`List.of()`)
- **Flyway/Liquibase 미사용**: ddl-auto: update (운영) — 명시적 마이그레이션 추적 없음

## Decision

### D1. admin 은 별도 Bounded Context 로 분리

`backend/src/main/java/com/gearshow/backend/admin/` 패키지에 신설.

| 기준 | User | Admin |
|---|---|---|
| 인증 메커니즘 | OAuth (카카오/애플) | email + password |
| 식별자 신뢰 원천 | OAuth provider | GearShow 자체 |
| 라이프사이클 | 가입/탈퇴 (사용자 주도) | 부트스트랩/관리 (운영자 주도) |
| 변경 빈도 | 자주 (피처 추가) | 드묾 (보안 정책만) |

응집도 높음 + 결합도 낮음 → BC 분리의 교과서적 케이스. 또한 GearShow 가 이미 `user/`, `catalog/`, `showcase/`, `chat/` BC 분리를 적극 채택 중이라 일관성 유지.

**대안 검토**:
- (B) `User` 에 `role` + `password` 컬럼 추가: User 도메인이 OAuth 와 password 를 모두 알아야 해서 응집도 약화. "OAuth 위임" 정책 깨짐. ❌
- (C) `user/` 안에 admin sub-context: 디렉토리만 분리. 코드 의존이 자유로워 BC 격리 의미 없음. ❌

### D2. 인증 — email + password (BCrypt)

- Admin 은 OAuth 외부의 내부 운영 계정 → 자체 인증 채널 필요
- `POST /api/admin/auth/login` (email + password) → JWT 응답
- 비밀번호는 BCrypt 해시로 DB 저장 (`Spring Security PasswordEncoder` 사용)
- 식별자는 **email** — 사용자가 admin 식별자로 email 을 의도. 향후 비밀번호 reset 메일 확장 가능
- User 도메인에 email 컬럼이 없어 충돌 없음

**대안 검토**:
- API key (정적 토큰): 회수가 어렵고 비밀번호 변경 같은 표준 절차가 없음. ❌
- 2FA: 본 PR 스코프 외, 후속 PR

### D3. JWT — 기존 인프라 재사용 + `type` claim 분기

- 기존 `JwtTokenProvider` 의 secret/만료 정책을 그대로 사용
- claim 에 `type: "USER"` / `type: "ADMIN"` 추가
- `JwtAuthenticationFilter` 가 `type` 보고 `ROLE_USER` / `ROLE_ADMIN` authority 매핑
- **호환성**: 기존 토큰(type claim 없음)은 `null` → `ROLE_USER` 로 처리하여 PR #71 머지 후 발급된 토큰이 그대로 동작
- `SecurityConfig` 룰: `/api/admin/auth/login` permitAll, `/api/admin/**` `hasRole("ADMIN")`

**대안 검토**:
- 별도 secret + 별도 토큰 인프라: 운영 secret 관리 부담 2배. 보안 이점 거의 없음 (같은 서비스 내). ❌

### D4. 부트스트랩 — 환경변수 기반 자동 INSERT

```yaml
app:
  bootstrap:
    admin:
      email: ${ADMIN_EMAIL:}
      password: ${ADMIN_PASSWORD:}
```

`ApplicationStartedEvent` 핸들러가:
1. 두 환경변수 모두 채워져 있을 때만 동작 (둘 중 하나라도 비어있으면 skip)
2. `admins` 테이블에 해당 email 이 이미 있으면 skip
3. 그 외엔 BCrypt 해시 후 INSERT 1회

운영 배포 시 두 환경변수를 1회 주입 → 부트스트랩 → 첫 로그인 후 비밀번호 변경(후속 PR) → 환경변수 제거.

**대안 검토**:
- 수동 SQL `INSERT INTO admins ...`: 가장 단순하지만 자동화 어려움. fallback 으로만 사용
- 임시 부트스트랩 API (`POST /api/admin/_bootstrap`): 임시 엔드포인트가 코드에 영구 잔존하는 위험. ❌

### D5. 마이그레이션 — Hibernate ddl-auto: update + 수동 ALTER 가이드

- `admins` 테이블은 신규 — Hibernate 가 ddl-auto: update 환경에서 자동 생성
- 운영 DB 적용 시 `SHOW TABLES;` 로 `admins` 생성 확인. 미생성 시 수동 `CREATE TABLE` (Entity 기반 DDL 추출)
- Flyway/Liquibase 도입은 별도 PR + ADR

## Consequences

### Positive

- **User 도메인 무손상** — OAuth 흐름, `OAuthUserInfo`, `User`, `AuthAccount` 모두 변경 없음
- **새 BC 격리** — admin 변경이 user/catalog 등에 파급 안 됨
- **테스트 용이** — admin 단독 통합 테스트 작성 가능. ArchUnit 으로 경계 강제
- **확장성** — 비밀번호 변경/2FA/admin 추가 회수 등 후속 PR 이 admin BC 안에서 완결

### Negative / Trade-off

- **인증 채널 2개** — 운영자가 별도 admin 로그인 페이지 사용. 사용자 경험은 분리되지만 본 시스템에서는 자연스러움 (운영자 ≠ 일반 사용자)
- **마이그레이션 리스크** — Flyway 부재로 운영 DB 에 `admins` 테이블이 자동 생성되지 않을 수 있음. 배포 시 수동 확인 단계 필요 (운영 체크리스트에 추가)
- **JWT type claim 호환성** — 기존 토큰은 `type=USER` 로 간주하지만, 향후 `type` 강제 검증 정책으로 변경 시 reissue 필요할 수 있음. 본 PR 시점에서는 호환성 유지

### Neutral

- crawler 등 외부 클라이언트는 **운영자 admin 토큰**을 환경변수로 받아 사용 (`KREAM_ADMIN_TOKEN`). 토큰은 만료가 있으므로 장기 운영 시 갱신 정책 필요 (후속 PR)

## Follow-up

본 PR 머지 후 즉시/단기간 진행 권장:

1. 비밀번호 변경 API (`PATCH /api/admin/me/password`) — 부트스트랩 후 즉시 비밀번호 회전
2. admin 자기조회 API (`GET /api/admin/me`)
3. Python Kream crawler — admin 토큰 환경변수로 받아 bulk-import 호출
4. Flyway/Liquibase 도입 + ADR
5. 기존 `POST /api/v1/catalogs` 단건 등록 가드 정책 결정 (별도 ADR)

## References

- PR #71 — catalog bulk-import API (가드 없이 머지)
- 기존 `User` / `AuthAccount` 도메인: `backend/src/main/java/com/gearshow/backend/user/domain/`
- 기존 `JwtTokenProvider`: `backend/src/main/java/com/gearshow/backend/user/infrastructure/security/JwtTokenProvider.java`
