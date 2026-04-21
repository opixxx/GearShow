# ADR-008: chat 컨텍스트의 JWT 검증 경계 — VerifyJwtTokenPort 도입

- **상태**: Accepted
- **결정일**: 2026-04-22
- **관련 PR**: #32 (Phase 0), fix/pr32-phase0
- **결정 주체**: GearShow Backend

---

## 1. 배경 (Context)

채팅 WebSocket 엔드포인트(`/ws`)는 STOMP CONNECT 프레임 수신 시 Authorization 헤더의 JWT 를
검증해 참여자(Principal)를 식별해야 한다. 초기 구현 `WebSocketAuthInterceptor` 는 간결함을 위해
user 컨텍스트의 인프라 클래스 `user.infrastructure.security.JwtTokenProvider` 를 직접 주입받아
호출했다.

```java
// Before (PR #32 초기 구현)
package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;

public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;   // ← user BC infrastructure 직접 의존
    ...
}
```

리뷰 결과 이 의존은 세 가지 문제를 일으킨다.

1. **BC 격리 위반**: chat → user **infrastructure** 를 직접 참조한다.
   user 측 JWT 구현(`JwtTokenProvider` 시그니처, 예외 계약, 주입 방식)이 변경되면
   chat 의 WebSocket 어댑터가 컴파일/런타임 수준에서 즉시 깨진다.
2. **검증 계약 모호**: `JwtTokenProvider#validateToken(token)` 후 `getUserId(token)` 를 두 번
   호출하는 방식이라 "어떤 예외가 어느 단계에서 나오는가" 가 분산된다.
3. **테스트 부담**: chat 의 단위 테스트가 user infrastructure 의 동작을 모킹해야 해 테스트
   경계가 부풀어 오른다.

## 2. 결정 (Decision)

chat 컨텍스트에 **아웃바운드 포트** `VerifyJwtTokenPort` 를 도입한다. chat 어댑터 계층에
user 의 `JwtTokenProvider` 를 감싸는 **단일 구현체** `UserJwtVerifyAdapter` 를 두고, chat 패키지
내에서 user 인프라에 대한 의존은 **오직 이 한 파일** 로 제한한다.

```java
// chat/application/port/out/VerifyJwtTokenPort.java
public interface VerifyJwtTokenPort {
    /** 유효하면 사용자 ID, 실패(만료·서명오류·파싱실패)는 Optional.empty */
    Optional<Long> resolveUserId(String accessToken);
}

// chat/adapter/out/jwt/UserJwtVerifyAdapter.java
@Component
class UserJwtVerifyAdapter implements VerifyJwtTokenPort {
    private final JwtTokenProvider jwtTokenProvider;  // user infrastructure 허용 경계
    ...
}

// chat/adapter/in/websocket/WebSocketAuthInterceptor.java
private final VerifyJwtTokenPort verifyJwtTokenPort;  // 포트 경유
```

**양보 불가 규칙**

- `chat/**` 패키지에서 `com.gearshow.backend.user.infrastructure.**` import 는 `UserJwtVerifyAdapter`
  1 파일 외에 0건. ArchUnit 으로 보장한다.
- 반환 타입은 `Optional<Long>` — 호출부가 실패 경로를 놓칠 수 없도록 컴파일러에게 강제.
  `RuntimeException` throw 로 실패 표현 금지 (catch 누락 재발).

## 3. 고려한 대안 (Alternatives)

### A. user 컨텍스트의 application 인바운드 UseCase 제공

user BC 에 `VerifyAccessTokenUseCase.resolveUserId(token)` 를 신설하고 chat 이 이를 호출하는 방식.
- 장점: user 쪽에서도 토큰 검증이 "공식 공개 유스케이스" 가 된다.
- 단점: JWT 는 user 도메인의 핵심 관심사가 아니라 인프라성 유틸리티다. application UseCase 로
  노출하면 다른 컨텍스트에서 이를 "비즈니스 개념" 으로 오해할 수 있다. 또한 user 가 "타 컨텍스트의
  요구를 반영하는" 방향의 의존이라 user BC 설계가 오염된다.
- 판단: **기각**. JWT 는 chat 이 자신의 문제를 풀기 위해 필요한 기능이므로 chat 아웃바운드 포트로
  가져가는 편이 bounded context 원칙에 부합한다.

### B. `platform` 계층에 공용 JWT 모듈 신설

- 장점: chat 뿐 아니라 향후 다른 BC 도 재사용 가능.
- 단점: 현재는 user 만 JWT 발급을 책임진다. 조기 일반화는 불필요한 인프라 추상을 만든다.
- 판단: **유보**. 2개 이상의 BC 가 JWT 검증을 필요로 할 때 재논의.

### C. 현 상태 유지 + ArchUnit 예외 허용

- 장점: 코드 변경 최소.
- 단점: 이유 없이 BC 간 인프라 결합을 정식화한다. 장기 유지보수 부채.
- 판단: **기각**.

## 4. 결과 (Consequences)

### 긍정

- chat · user 간 의존 경계가 명확해진다 (chat 은 user 의 **인프라** 를 모른다).
- `WebSocketAuthInterceptor` 단위 테스트가 `VerifyJwtTokenPort` Mock 으로 3줄이면 충분.
- 향후 JWT 구현이 RSA/JWK 기반으로 교체되거나 Auth 서버 분리가 일어나도 chat 은 영향 없다.

### 부정

- chat 어댑터 계층에 파일 1개(`UserJwtVerifyAdapter`) 가 추가된다 (허용 경계 역할 — 불가피).
- `Optional<Long>` 반환에 맞춰 인터셉터의 예외 생성 방식이 `orElseThrow(...)` 로 바뀌어
  테스트 픽스처 수정이 수반된다.

### 검증

- ArchUnit `HexagonalArchitectureTest` 에 다음 규칙 유지/강화:
  - `chat/**` → `user.infrastructure.**` import 는 `chat.adapter.out.jwt.UserJwtVerifyAdapter`
    외에 존재하면 실패 (이미 포괄 규칙으로 차단되는 경우 추가 명시 없음).
- `WebSocketAuthInterceptorTest` 가 `VerifyJwtTokenPort` Mock 만으로 동작 (완료).

## 5. 참조

- PR #32 통합 리뷰 코멘트 (Phase 0 항목 P0-5)
- architecture-reviewer 에이전트 지적: "chat 이 user infrastructure 를 직접 import" (Major)
- code-reviewer 에이전트 지적: "ErrorCode 규약 위반(MessageDeliveryException 하드코딩)" 은
  Phase 1 에서 별도 처리 예정 (본 ADR 범위 아님).
