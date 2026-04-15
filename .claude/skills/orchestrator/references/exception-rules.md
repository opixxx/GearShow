# Exception Rules

> `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` 직접 사용 금지

모든 예외는 `CustomException`을 상속하고, HTTP status와 메시지는 `ErrorCode` enum에 통합 관리한다.
메시지는 한글로 작성한다.

## 구조

```
CustomException (extends RuntimeException)
└── 도메인별 구체 예외 (ex. NotFoundAccountException)
ErrorCode (enum)
└── HTTP status + 한글 message 통합 관리
```

## ErrorCode 작성

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // USER
    DUPLICATE_EMAIL(400, "이미 사용 중인 이메일입니다"),
    NOT_FOUND_USER(404, "사용자를 찾을 수 없습니다");

    private final int status;
    private final String message;
}
```

### 네이밍 규칙
- 코드명은 `<동작/상태>_<대상>` 형식 (예: `NOT_FOUND_USER`, `DUPLICATE_EMAIL`)
- 같은 컨텍스트끼리 주석으로 구분 (`// USER`, `// SHOWCASE`)

## CustomException 작성

```java
@Getter
public class CustomException extends RuntimeException {
    private final int status;
    private final String message;

    public CustomException(ErrorCode errorCode) {
        this.status = errorCode.getStatus();
        this.message = errorCode.getMessage();
    }
}
```

## 구체 예외 작성

### Good
```java
public class NotFoundUserException extends CustomException {
    public NotFoundUserException() {
        super(ErrorCode.NOT_FOUND_USER);
    }
}
```

### Bad — ErrorCode 거치지 않고 직접 메시지 작성
```java
public class NotFoundUserException extends CustomException {
    public NotFoundUserException() {
        super(404, "유저가 없습니다.");
    }
}
```

**이유**: 예외 메시지·상태 코드가 여러 군데 분산되면 정책 변경(예: "NOT_FOUND 전부 410으로 변경") 시 누락 위험. ErrorCode에서 한 번만 관리.

## 예외 배치 위치

| 계층 | 경로 | 언제 |
|---|---|---|
| 도메인 예외 | `{context}/domain/exception/` | 불변식 위반, 상태 전이 오류 |
| 유스케이스 예외 | `{context}/application/exception/` | 권한·전제조건 실패 |
| 외부 연동 예외 | `{context}/adapter/out/{provider}/exception/` | 외부 API 호출 실패 |

## 금지 사항

- `throw new RuntimeException(...)` 직접 사용
- `throw new IllegalArgumentException(...)`, `throw new IllegalStateException(...)` 직접 사용
- 예외 메시지를 코드 내 하드코딩 (반드시 `ErrorCode` 경유)
- `catch (Exception e) { /* ignore */ }` — 에러 삼키기
- 예외를 로그만 남기고 swallow — 호출자가 실패를 인지 못 함

## 관련 규칙
- 헥사고날·SOLID·Domain Model : `coding-conventions.md`
- 테스트 시 예외 검증 : `test-rules.md`
