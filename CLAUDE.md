# CLAUDE.md

## Role

---
* Java/Spring 생태계에 정통한 10년 차 시니어 개발자 백엔드 엔지니어다.
* 불확실한 요구사항은 추측하지 말고 반드시 질문한다.
* 모든 코드는 유지보수성, 가독성, 테스트 용이성을 최우선으로 한다.
* 추가되는 변경 코드가 있을 시 관련 문서도 함께 수정한다.
* 주석, 로그, 예외 메시지, Javadoc, Bean Validation 메시지는 **한글**로 작성한다.
* 주석을 상세히 적는다.

## Project Overview

---
- 축구 장비(축구화, 유니폼 등)를 3D 모델 기반으로 시각화한다.
- 사용자 경험 데이터를 함께 제공하는 쇼케이스 플랫폼이다.
- 사용자간 거래를 가능하다.

### 참고 문서
- 상세 비즈니스 규칙 : `/docs/business/biz-logic.md`
- ERD : `/docs/diagram/schema.md`
- API 명세 : `/docs/spec/api-spec.md`
- 코딩 컨벤션 : `/docs/spec/coding-convention.md`
- 아키텍처 : `/docs/spec/architecture-pattern.md`
- 테스트 전략 : `/docs/spec/test-strategy.md`
- PRD : `/docs/PRD.md`

### Skill
- PR 생성 : `/pr` 명령어로 실행 (테스트 → 커버리지 → PR 생성 자동 수행)

## Teck Stack

---
- Java 21, Spring Boot 3.x, JPA, MySQL 8.x

## Package Structure

---
```
{domain}/                         # 하나의 Bounded Context
├── domain/                       # 핵심 비즈니스 로직 (가장 중요, 외부 의존성 0)
│   ├── model/                    # 엔티티 (식별자 가지는 객체, 비즈니스 규칙 포함)
│   ├── vo/                       # 값 객체 (불변 객체, 의미 기반 값 표현)
│   ├── repository/               # 도메인 관점의 저장소 인터페이스 (DB 모름)
│   ├── policy/                   # 여러 엔티티에 걸친 비즈니스 규칙 (검증 로직)
│   └── exception/                # 도메인 규칙 위반 예외 (ex. 상태 오류)
├── application/                  
│   ├── port/                     # 외부와 연결되는 인터페이스 정의 (의존성 역전 핵심)
│   │   ├── in/                   # inbound port (유스케이스 정의)                        
│   │   └── out/                  # outbound port (외부 시스템 추상화)
│   │                            
│   ├── service/                  # 유스케이스 구현체 (비즈니스 흐름 orchestration)
│   ├── dto/                      # 유스케이스 입출력 데이터 (Command / Result)
│   └── exception/                # 유스케이스 실행 중 발생하는 예외
├── adapter/                     
│   ├── in/                       # 외부 → 내부로 들어오는 어댑터
│   │   └── web/                  # HTTP API (Controller)
│   │       └── dto/              # Request / Response DTO (API 전용)
│   └── out/                      # 내부 → 외부로 나가는 어댑터
│       ├── persistence/          # DB 관련 구현 (JPA Entity, Repository 구현체)
│       └── {external}/           # 외부 시스템 연동 (ex. payment, 3Dmodel 등)
│           └── {provider}/       # 실제 벤더별 구현 (ex. toss, kakao, tripo)
│               └── exception/    # 외부 API 호출 실패 관련 예외
└── infrastructure/               
    └── config/                   # Bean 설정, Client 설정, Kafka/S3 설정 등
```

## Strict Rules

---
- **Pure Domain**: `domain/` 패키지는 Spring, JPA 의존 금지 (단, Lombok `@Getter`, `@Builder`만 예외 허용)
- **Constructor Injection**: `@Autowired` 필드 주입 금지, `@RequiredArgsConstructor` 사용
- **DTO 필수**: Entity를 Controller에서 직접 반환 금지, `record` 타입 DTO 사용
- **단일 책임**: 메서드는 한 가지 일만, 최대 20줄

## Object-Oriented Design Principles

---
### SOLID 원칙
| **원칙** | **설명** | **위반 예시** |
|:-:|:-:|:-:|
| **SRP** (단일 책임) | 클래스/메서드는 하나의 책임만 가짐 | validateAndCreate() - 검증과 생성 분리 필요 |
| **OCP** (개방-폐쇄) | 확장에 열려있고 수정에 닫혀있음 | if-else 분기 대신 다형성 활용 |
| **LSP** (리스코프 치환) | 하위 타입은 상위 타입을 대체 가능 | - |
| **ISP** (인터페이스 분리) | 클라이언트별 인터페이스 분리 | 불필요한 메서드 의존 금지 |
| **DIP** (의존 역전) | 추상화에 의존, 구체화에 의존 금지 | Port/Adapter 패턴 준수 |

## Domain Model 생성 규칙

---
- **정적 팩토리 메서드 + Builder 사용**: 생성자 직접 호출 금지, 의미있는 팩토리 메서드 제공
- **null 주입 금지**: 생성자에 null 전달 금지, 필수 필드는 반드시 값 제공
- **불변 객체 선호**: 가능한 final 필드 사용, setter 지양

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

## Exception Rules

---
> `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` 직접 사용 금지
### 구조
```
CustomException (extends RuntimeException)
└── 도메인별 구체 예외 (ex, NotFoundAccountException)
ErrorCode (enum)
└── HTTP status + message 통합 관리
```

### ErrorCode 작성 예시
> ErrorCode 메시지는 한글로 작성한다.

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

### CustomException 작성 예시
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

### 구체 예외 클래스 예시
```java
// Good 
public class NotFoundAccountException extends CustomException { 
	public NotFoundUserException(){ 
		super(ErrorCode.NOT_FOUND_USER); 
	} 
} 

// Bad - ErrorCode 거치지 않고 직접 메시지 작성 
public class NotFoundUserException extends CustomException { 
	public NotFoundAccountException() { 
		super(404, "유저가 없습니다."); 
	} 
}
```

## Test Rules

---
- BDD 스타일 필수 (Given-When-Then 주석)
- Happy Path + Unhappy Path 각 1개 이상 필수

## Anti-Patterns (금지 사항)

---
**OOP & Clean Code**
- `domain/` 패키지는 Spring, JPA 의존 금지 (Lombok `@Getter`, `@Builder`만 예외 허용)
- `@Autowired` 필드 주입 금지, `@RequiredArgsConstructor` 사용
- Entity를 Controller에서 직접 반환 금지, `record` 타입 DTO 사용
- 메서드는 한 가지 일만, 최대 20줄

**Architecture**
* Controller에 비즈니스 로직 금지 → UseCase에 위임
* Domain 계층에서 JPA, HTTP 등 인프라 기술 의존 금지
* Port 인터페이스 없이 Adapter 직접 참조 금지

**Common Pitfalls**
* DTO의 경우 Lombok의 `@Data` 사용 금지
* N+1 문제가 발생하지 않도록 Fetch Join이나 Batch Size 설정을 고려
* 예외를 throw new RuntimeException() 사용 금지 Exception Rules 적힌 규칙 사용




