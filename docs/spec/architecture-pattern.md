# 아키텍처 패턴

---

## 핵심 원칙

의존성 방향은 항상 안쪽으로만 흘러야 한다: **Adapter → Application → Domain**.
도메인 레이어가 핵심이며, 외부 의존성은 반드시 0이어야 한다. 어떤 상황에서도 이 방향을 위반해서는 안 된다.

---

## 1. 레이어 정의와 제약

### 도메인 레이어

위치: `{domain}/domain/`

도메인 레이어는 순수 비즈니스 로직과 도메인 개념을 담는다. 이 레이어는 프레임워크와 인프라 기술로부터 완전히 격리되어야 한다.

**허용**: 순수 Java POJO, Lombok `@Getter`와 `@Builder`만 허용.

**금지**: Spring 어노테이션, JPA 어노테이션, Lombok `@Data`/`@Setter`/`@RequiredArgsConstructor`, 모든 프레임워크 의존성, Application 또는 Adapter 레이어 참조.

도메인 레이어는 다섯 개의 하위 패키지로 구성된다:
- `model/` — 식별자와 비즈니스 규칙을 가진 엔티티
- `vo/` — 불변 값 객체
- `repository/` — DDD 패턴을 따르는 저장소 인터페이스
- `policy/` — 여러 엔티티에 걸친 검증 로직
- `exception/` — 도메인 규칙 위반 예외

### 애플리케이션 레이어

위치: `{domain}/application/`

애플리케이션 레이어는 도메인 객체를 조율하여 유스케이스를 수행한다. 이 레이어는 도메인 레이어에만 의존할 수 있다.

**허용**: Spring `@Service`, `@Transactional`, `@RequiredArgsConstructor`만 허용.

**금지**: JPA 어노테이션, HTTP 관련 코드, 외부 API 직접 호출, Adapter 레이어 참조.

애플리케이션 레이어는 다섯 개의 하위 패키지로 구성된다:
- `port/in/` — 유스케이스를 정의하는 인바운드 포트 인터페이스
- `port/out/` — 외부 시스템을 추상화하는 아웃바운드 포트 인터페이스
- `service/` — 유스케이스 구현체
- `dto/` — Command(입력)와 Result(출력) record 타입 DTO
- `exception/` — 유스케이스 실행 실패 예외

### 어댑터 레이어

위치: `{domain}/adapter/`

어댑터 레이어는 애플리케이션과 외부 세계를 연결한다. 이 레이어는 애플리케이션과 도메인 레이어 모두에 의존할 수 있다.

**허용**: Spring MVC, JPA, 외부 라이브러리 통합을 포함한 모든 프레임워크 어노테이션.

어댑터 레이어는 인바운드와 아웃바운드로 나뉜다:
- `adapter/in/web/` — 컨트롤러와 웹 DTO (Request / Response)
- `adapter/out/persistence/` — JPA 엔티티, 매퍼, 저장소 구현체
- `adapter/out/{external}/{provider}/` — 벤더별로 구성된 외부 시스템 구현체

### 인프라 레이어

위치: `{domain}/infrastructure/`

프레임워크 설정만 포함한다.
- `config/` — Spring 설정 클래스, 클라이언트 설정

---

## 2. Aggregate 경계와 FK 전략

### 같은 Aggregate (FK, 동일 라이프사이클)

같은 Aggregate 내의 엔티티는 라이프사이클을 공유하며 FK로 연결된다.
JPA `@ManyToOne` / `@OneToMany`는 **사용하지 않는다** — `@Column`으로 ID만 참조한다.

### 다른 Aggregate (논리적 참조, ID만)

다른 Aggregate 간에는 ID 값으로만 참조한다. JPA 관계 매핑은 사용하지 않는다.
이를 통해 도메인 ↔ JPA 엔티티 매핑 시 불필요한 엔티티 로딩을 방지한다.

```java
// Good — ID만 참조
@Column(name = "owner_id", nullable = false)
private Long ownerId;

// Bad — Aggregate 간 JPA 관계 매핑
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_id")
private UserJpaEntity owner;
```

---

## 3. 도메인 모델 규칙

- **정적 팩토리 메서드 + @Builder**로 생성한다. public 생성자를 외부에 노출하지 않는다.
- 팩토리 메서드 내부에서 불변 조건을 검증한다.
- `final` 필드를 선호하고 setter를 사용하지 않는다.
- 생성자에 `null`을 전달하지 않는다 — 필수 필드는 항상 값이 있어야 한다.

```java
@Getter
public class Entity {

    @Builder
    private Entity(String name, EntityStatus status) {
        this.name = name;
        this.status = status;
    }

    public static Entity create(String name) {
        validate(name);
        return Entity.builder()
            .name(name)
            .status(EntityStatus.ACTIVE)
            .build();
    }

    private static void validate(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidEntityException();
        }
    }
}
```

---

## 4. DTO 흐름

```
[Client] ←→ Request/Response ←→ [Controller] ←→ Command/Result ←→ [Service] ←→ [Domain Model]
             (adapter/in/web/dto)                 (application/dto)
```

| 레이어 | 입력 | 출력 | 타입 |
|:------|:-----|:----|:----|
| Adapter (Web) | `{Action}Request` | `{Action}Response` | record |
| Application | `{Action}Command` | `{Action}Result` | record |
| Domain | Domain Model | Domain Model | class |

**변환 책임:**
- Request → Command: Controller 또는 `request.toCommand()`
- Result → Response: Controller 또는 `Response.from(result)`
- Domain ↔ JPA Entity: Persistence Adapter 내부의 `{Entity}Mapper`

도메인 모델이나 JPA 엔티티를 API를 통해 직접 반환해서는 안 된다.

---

## 5. Persistence Adapter 패턴

JPA 엔티티와 도메인 모델은 **별도의 클래스**다. 매핑은 어댑터 내부에서만 수행된다.

```java
@Repository
@RequiredArgsConstructor
public class EntityPersistenceAdapter implements EntityPort {

    private final EntityJpaRepository jpaRepository;
    private final EntityMapper mapper;

    @Override
    public Entity save(Entity entity) {
        EntityJpaEntity jpaEntity = mapper.toJpaEntity(entity);
        EntityJpaEntity saved = jpaRepository.save(jpaEntity);
        return mapper.toDomain(saved);
    }
}
```

---

## 6. 외부 서비스 어댑터 패턴

외부 서비스 어댑터는 아웃바운드 포트 인터페이스를 구현하고, 벤더별 응답을 도메인 객체로 변환한다.

서비스 유형별로, 그 다음 벤더별로 구성한다. 벤더 고유 이름은 **어댑터 레이어에서만** 허용된다.

```
adapter/out/
├── {external}/
│   └── {provider}/
│       ├── {Provider}{Service}ClientAdapter.java
│       └── exception/
```

도메인과 애플리케이션 레이어는 **벤더 중립적** 이름을 사용해야 한다:

| 나쁜 예 (벤더 종속) | 좋은 예 (벤더 중립) |
|:-----------------|:----------------|
| `TripoModelPort` | `ModelGenerationClient` |
| `KakaoOAuthPort` | `OAuthClient` |
| `KakaoUserInfo` | `OAuthUserInfo` |

---

## 7. 트랜잭션 전략

- `@Transactional`은 **Application Service 레이어에만** 배치한다.
- 도메인 레이어와 어댑터 레이어에는 `@Transactional`을 **사용하지 않는다**.
- 읽기 전용 쿼리는 `@Transactional(readOnly = true)`를 사용한다.

### 외부 API 호출과 트랜잭션 범위

외부 시스템 호출(S3, Kafka, HTTP 등)은 반드시 `@Transactional` 범위 **밖**에서 수행해야 한다.
트랜잭션 안에서 외부 API가 지연되거나 실패하면 DB 커넥션이 불필요하게 점유된다.

**적용 순서**: 외부 검증 → 트랜잭션 시작 → DB 작업 완료 → 트랜잭션 종료 → 외부 이벤트 발행

```java
// Good — S3 검증은 트랜잭션 밖, DB 저장만 트랜잭션 안
public CreateShowcaseResult create(CreateShowcaseCommand command, List<String> imageKeys) {
    validateKeysExist(imageKeys);           // 외부 호출 — 트랜잭션 밖
    return saveShowcase(command, imageKeys); // @Transactional 메서드
}

@Transactional
private CreateShowcaseResult saveShowcase(...) { ... }

// Bad — S3 호출이 @Transactional 안에 있음
@Transactional
public CreateShowcaseResult create(CreateShowcaseCommand command, List<String> imageKeys) {
    validateKeysExist(imageKeys);  // 외부 호출이 트랜잭션 범위 안 → 커넥션 낭비
    showcasePort.save(...);
}
```

**Facade 패턴 활용**: 트랜잭션 범위를 좁힐 때 Facade가 외부 호출과 트랜잭션 메서드를 조율한다.

```java
// Facade — @Transactional 없음, 흐름만 조율
public class CreateShowcaseFacade {
    public CreateShowcaseResult create(...) {
        validateKeysExist(imageKeys);       // 외부 호출 (트랜잭션 밖)
        CreateShowcaseResult result = createShowcaseService.create(...); // 트랜잭션
        modelGenerationPort.request(...);   // 외부 이벤트 발행 (트랜잭션 밖)
        return result;
    }
}
```

---

## 8. 소유권 검증 패턴

사용자가 소유한 리소스(쇼케이스, 댓글 등)에 대한 **생성 이외의 모든 조작(수정, 삭제, 하위 리소스 추가 등)**은
서비스 레이어에서 반드시 소유권을 검증해야 한다.

### 규칙

- 소유권 검증은 **Application Service 레이어**에서 수행한다. Controller에서 하지 않는다.
- 리소스가 존재하지 않으면 `NotFound` 예외, 소유자가 다르면 `NotOwner` 예외를 발생시킨다.
- 소유권 검증이 필요한 엔드포인트는 반드시 `Authentication`(또는 `ownerId`)을 파라미터로 받아야 한다.

### 적용 대상

경로 변수에 리소스 ID가 포함되고 인증이 필요한 모든 변경 요청이 해당된다.

| 엔드포인트 예시 | 검증 대상 |
|:--------------|:---------|
| `PATCH /showcases/{showcaseId}` | 쇼케이스 소유자 |
| `DELETE /showcases/{showcaseId}` | 쇼케이스 소유자 |
| `POST /showcases/{showcaseId}/images` | 쇼케이스 소유자 |
| `PATCH /showcases/{showcaseId}/comments/{commentId}` | 댓글 작성자 |
| `DELETE /showcases/{showcaseId}/comments/{commentId}` | 댓글 작성자 |

### 구현 패턴

```java
// Good — Service에서 소유권 검증
@Transactional
public void update(Long showcaseId, Long requesterId, UpdateShowcaseCommand command) {
    Showcase showcase = showcasePort.findById(showcaseId)
            .orElseThrow(NotFoundShowcaseException::new);
    if (!showcase.getOwnerId().equals(requesterId)) {
        throw new NotOwnerShowcaseException();
    }
    // 이후 비즈니스 로직
}

// Bad — Controller에서 소유권 검증 (Controller에 비즈니스 로직 금지)
@PatchMapping("/{showcaseId}")
public ApiResponse<ShowcaseIdResponse> update(...) {
    if (!showcase.getOwnerId().equals(ownerId)) { // Controller에서 하면 안 됨
        throw new NotOwnerShowcaseException();
    }
}
```

### 리소스에 종속된 엔드포인트도 포함

`POST /showcases/{showcaseId}/images/upload-urls`처럼 특정 리소스에 종속된 엔드포인트는
직접적인 데이터 변경이 없더라도 해당 리소스의 소유권을 검증해야 한다.
업로드 URL 발급도 결국 그 리소스에 데이터를 추가하는 행위이기 때문이다.

---

## 9. 외부 어댑터 예외 처리 전략

외부 시스템 어댑터(`adapter/out/{external}/`)에서 예외를 처리할 때는
**도메인 이유(비즈니스 상 존재하지 않음)**와 **인프라 이유(네트워크, 인증, 타임아웃)**를 반드시 분리해야 한다.

### 원칙

모든 예외를 `catch(Exception e)`로 묶어 단일 결과로 처리하면
인프라 장애가 클라이언트 오류(4xx)로 잘못 전달되거나 문제가 숨겨진다.

```java
// Good — 예외 원인에 따라 다르게 처리
@Override
public boolean exists(String s3Key) {
    try {
        s3Client.headObject(...);
        return true;
    } catch (NoSuchKeyException e) {
        // 도메인 이유: 해당 키가 S3에 없음 → false 반환 (정상 흐름)
        return false;
    } catch (SdkClientException e) {
        // 인프라 이유: 네트워크/인증/타임아웃 → 서버 예외로 전파
        log.error("S3 객체 존재 여부 확인 중 인프라 오류 발생: key={}", s3Key, e);
        throw new StorageAccessFailedException();
    }
}

// Bad — 모든 예외를 false로 처리
} catch (Exception e) {
    log.error("오류 발생: key={}", s3Key, e);
    return false;  // 인프라 장애가 "키 없음"으로 오인됨
}
```

### 외부 어댑터 예외 분류 기준

| 상황 | 처리 방법 | HTTP 결과 |
|:----|:---------|:---------|
| 리소스가 존재하지 않음 (`NoSuchKeyException`, 404 등) | 도메인 예외 또는 `false` 반환 | 4xx (클라이언트 오류) |
| 네트워크 장애, 타임아웃 | 로그 후 서버 예외 전파 | 5xx (서버 오류) |
| 인증/권한 오류 (403 등) | 로그 후 서버 예외 전파 | 5xx (서버 오류) |
| 외부 API 응답 파싱 실패 | 로그 후 서버 예외 전파 | 5xx (서버 오류) |

### 외부 예외 클래스 위치

외부 시스템 예외는 해당 어댑터 패키지 안에 위치한다.

```
adapter/out/{external}/{provider}/exception/
├── StorageAccessFailedException.java   // 인프라 오류 (5xx)
└── PresignFailedException.java          // Presigned URL 생성 실패 (5xx)
```

---

## 10. 예외 설계

### 계층 구조

```
CustomException (extends RuntimeException)
├── 도메인 예외     ({domain}/domain/exception/)
├── 애플리케이션 예외 ({domain}/application/exception/)
│   ├── ModelGenerationRetryableException     ← 일시적 장애, 재시도 가능
│   └── ModelGenerationNonRetryableException  ← 영구 실패, 즉시 FAILED
└── 외부 시스템 예외  ({domain}/adapter/out/{external}/{provider}/exception/)
    ├── TripoRetryableException     (→ ModelGenerationRetryableException 상속)
    └── TripoNonRetryableException  (→ ModelGenerationNonRetryableException 상속)
```

### 규칙

- 모든 예외는 `ErrorCode`를 통해 `CustomException`을 상속해야 한다.
- `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` 직접 사용은 **금지**한다.
- 예외 네이밍: `{Reason}{Entity}Exception`
- ErrorCode 네이밍: `{DOMAIN}_{REASON}` 또는 `{DOMAIN}_{ENTITY}_{REASON}`
- ErrorCode 메시지는 **한글**로 작성한다.

### 외부 API 에러 분류 규칙

외부 API (Tripo 등) 의 에러는 **Retryable / Non-retryable** 로 분류한다:

| 분류 | 예시 | 처리 |
|:----|:-----|:-----|
| **Retryable** (일시적) | 429 Rate Limit, 500 Server Error | 상태 유지, Recovery 가 자동 재시도 (retryCount 적용) |
| **Non-retryable** (영구) | 400 Invalid Param, 401 Auth, 403 No Credit | 즉시 FAILED. 크레딧 부족/인증 실패는 Alert 필수 |

Application 계층에 추상 예외를 정의하고, 어댑터 예외가 이를 상속한다.
이를 통해 **application 계층이 구체 어댑터(Tripo, Meshy 등)에 의존하지 않고** 재시도 여부를 판단할 수 있다.

---

## 11. 금지 패턴

- 도메인에서 애플리케이션 또는 어댑터 레이어 방향으로의 의존성 절대 금지.
- 도메인 레이어에서 `@Getter`, `@Builder` 외 프레임워크 어노테이션 사용 금지.
- `@Autowired` 필드 주입 금지. `@RequiredArgsConstructor` + `private final` 사용.
- `RuntimeException`, `IllegalArgumentException` 등 제네릭 예외 직접 throw 금지.
- Persistence Adapter 외부에서 JPA 엔티티 노출 금지.
- 도메인/애플리케이션 레이어에서 벤더 고유 이름 사용 금지.
- 포트 인터페이스 없이 외부 서비스 직접 호출 금지.
- 도메인/어댑터 레이어에 `@Transactional` 사용 금지.
- Aggregate 간 JPA 관계 매핑(`@ManyToOne`, `@OneToMany`) 사용 금지.
- Controller에서 소유권 검증 로직 구현 금지 (Service에서 수행).
- `@Transactional` 메서드 안에서 외부 API 호출 금지.
- 외부 어댑터에서 모든 예외를 catch해 단일 결과로 처리 금지.

---

## 12. 패키지 구조

```
{domain}/
├── domain/
│   ├── model/
│   ├── vo/
│   ├── repository/
│   ├── policy/
│   └── exception/
├── application/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   ├── service/
│   ├── dto/
│   └── exception/
├── adapter/
│   ├── in/
│   │   └── web/
│   │       └── dto/
│   └── out/
│       ├── persistence/
│       └── {external}/
│           └── {provider}/
│               └── exception/
└── infrastructure/
    └── config/
```

---

## 13. 구현 체크리스트

새로운 도메인 기능을 만들 때 각 항목을 확인한다.

### 도메인 레이어
- [ ] `domain/model/`에 정적 팩토리 메서드가 있는 도메인 모델
- [ ] 필요한 경우 `domain/vo/`에 값 객체
- [ ] `domain/repository/`에 저장소 인터페이스
- [ ] 엔티티 간 검증이 필요한 경우 `domain/policy/`에 Policy
- [ ] `domain/exception/`에 도메인 예외

### 애플리케이션 레이어
- [ ] `application/port/in/`에 UseCase 인터페이스
- [ ] 외부 시스템이 필요한 경우 `application/port/out/`에 아웃바운드 포트
- [ ] `application/service/`에 `@Transactional` Service 구현체
- [ ] `application/dto/`에 Command / Result record 타입 DTO
- [ ] 외부 API 호출이 있으면 `@Transactional` 범위 밖으로 분리 (섹션 7 참고)
- [ ] 소유자 있는 리소스 수정/삭제/하위 추가 시 소유권 검증 포함 (섹션 8 참고)

### 어댑터 레이어
- [ ] `adapter/in/web/`에 UseCase에 위임하는 Controller
- [ ] `adapter/in/web/dto/`에 Request / Response record 타입 DTO
- [ ] `adapter/out/persistence/`에 JPA 엔티티 + Mapper + Adapter
- [ ] 필요한 경우 `adapter/out/{external}/{provider}/`에 외부 어댑터
- [ ] 외부 어댑터에서 도메인 오류 / 인프라 오류 구분하여 예외 처리 (섹션 9 참고)

### 인프라 레이어
- [ ] 필요한 경우 `infrastructure/config/`에 설정 클래스
