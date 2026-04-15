# Coding Conventions — GearShow Backend

패키지 구조·설계 원칙·도메인 모델 생성·안티패턴을 다룬다.
예외 규칙과 테스트 규칙은 별도 파일로 분리되어 있다.

- 예외 규칙 : `exception-rules.md`
- 테스트 규칙 : `test-rules.md`

---

## 1. Package Structure (헥사고날)

```
{domain}/                         # 하나의 Bounded Context
├── domain/                       # 핵심 비즈니스 로직 (외부 의존성 0)
│   ├── model/                    # 엔티티 (식별자 보유, 비즈니스 규칙 포함)
│   ├── vo/                       # 값 객체 (불변)
│   ├── repository/               # 도메인 관점 저장소 인터페이스
│   ├── policy/                   # 여러 엔티티에 걸친 규칙
│   └── exception/                # 도메인 예외
├── application/
│   ├── port/
│   │   ├── in/                   # inbound port (유스케이스 정의)
│   │   └── out/                  # outbound port (외부 시스템 추상화)
│   ├── service/                  # 유스케이스 구현체
│   ├── dto/                      # Command / Result
│   └── exception/                # 유스케이스 예외
├── adapter/
│   ├── in/web/                   # Controller
│   │   └── dto/                  # Request / Response
│   └── out/
│       ├── persistence/          # JPA Entity / Repository 구현
│       └── {external}/{provider}/ # 외부 연동 (toss, tripo 등)
│           └── exception/
└── infrastructure/
    └── config/                   # Bean·Client·Kafka·S3 설정
```

실제 GearShow Bounded Context: `showcase`, `user`, `catalog`, `platform.outbox`, `platform.idempotency`, `health`, `common`.

## 2. Strict Rules

- **Pure Domain**: `domain/`은 Spring·JPA 의존 금지 (Lombok `@Getter`, `@Builder`만 예외)
- **Constructor Injection**: `@Autowired` 필드 주입 금지, `@RequiredArgsConstructor` 사용
- **DTO 필수**: Entity를 Controller에서 직접 반환 금지, `record` 타입 사용
- **입력 검증 필수**: Request DTO에 Bean Validation 적용, Controller에서 `@Valid` 필수, 메시지는 한글
- **단일 책임**: 메서드는 한 가지 일만, 최대 20줄

## 3. SOLID

| 원칙 | 요지 | 위반 예시 |
|---|---|---|
| SRP | 하나의 책임 | `validateAndCreate()` |
| OCP | 확장 O, 수정 X | if-else 분기 대신 다형성 |
| LSP | 하위 타입은 상위 대체 가능 | — |
| ISP | 클라이언트별 인터페이스 분리 | 불필요한 메서드 의존 |
| DIP | 추상화에 의존 | Port/Adapter 패턴 |

## 4. Domain Model 생성 규칙

- **정적 팩토리 메서드 + Builder**: 생성자 직접 호출 금지
- **null 주입 금지**: 필수 필드는 반드시 값 제공
- **불변 객체 선호**: `final` 필드, setter 지양

```java
@Getter
public class Showcase {
    @Builder
    private Showcase(String title, int price, ShowcaseStatus status) {
        this.title = title;
        this.price = price;
        this.status = status;
    }

    public static Showcase create(String title, int price) {
        validate(title, price);
        return Showcase.builder()
            .title(title)
            .price(price)
            .status(ShowcaseStatus.PENDING)
            .build();
    }

    private static void validate(String title, int price) {
        if (title == null || title.isBlank()) {
            throw new InvalidShowcaseException();
        }
    }
}
```

> 예외 클래스 작성 상세는 `exception-rules.md` 참조.

## 5. Anti-Patterns (금지)

### OOP & Clean Code
- `domain/`이 Spring/JPA 의존 (ArchUnit이 차단)
- `@Autowired` 필드 주입
- Entity를 Controller에서 직접 반환
- 메서드 20줄 초과 / 책임 다수
- DTO에 Lombok `@Data` 사용 (세터 자동 생성)

### Architecture
- Controller에 비즈니스 로직
- Domain 계층에 인프라(JPA, HTTP) 의존 (ArchUnit이 차단)
- Port 인터페이스 없이 Adapter 직접 참조 (ArchUnit이 차단)
- `main`/`master` 브랜치 메인 디렉토리에서 소스 직접 수정 (훅이 차단)

### Agent 행동
- Intake 단계 건너뛰고 바로 구현
- 테스트를 나중에 몰아서 작성 (→ `test-rules.md`)
- 자가수정에서 `--no-verify`, `-DskipTests`로 우회
- 실패 원인 분석 없이 brute-force 재시도
- 에스컬레이션 조건 충족인데 계속 혼자 진행

### Common Pitfalls
- Fetch Join/Batch Size 고려 없는 연관관계 조회 (N+1)
- `throw new RuntimeException()` 직접 사용 (→ `exception-rules.md`)
- 생성자 직접 호출 (Domain Model 생성 규칙 위반)
- null을 명시적으로 주입
