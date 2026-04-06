# GearShow Coding Convention

---

## 1. Language

코드의 **식별자(변수, 메서드, 클래스명)** 는 영문으로, **사람이 읽는 텍스트(주석, 로그, 메시지)** 는 **한글**로 작성한다.

| 대상 | 언어 | 예시 |
|:-------|:---------|:--------|
| 주석 / Javadoc | 한글 | `/** 새로운 쇼케이스를 생성한다. */` |
| 로그 메시지 | 한글 | `log.info("쇼케이스 생성 완료: showcaseId={}", id)` |
| 예외 메시지 (ErrorCode) | 한글 | `"쇼케이스를 찾을 수 없습니다"` |
| 변수 / 메서드명 | 영문 | `findByOwnerId`, `catalogItemId` |
| Bean Validation 메시지 | 한글 | `@NotBlank(message = "제목은 필수입니다")` |

---

## 2. Naming Convention

> Base package: `com.gearshow.backend`

### 2-1. Package

```
com.gearshow.backend.{domain}.{layer}.{sublayer}
```

| Example | Description |
|:--------|:-----------|
| `com.gearshow.backend.showcase.domain.model` | Showcase domain entity |
| `com.gearshow.backend.showcase.application.port.in` | Showcase inbound port |
| `com.gearshow.backend.showcase.adapter.out.persistence` | Showcase JPA adapter |
| `com.gearshow.backend.showcase.adapter.out.model3d.tripo` | 3D model external provider |

### 2-2. Port (Interface)

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Inbound Port (UseCase) | `{Action}UseCase` | `CreateShowcaseUseCase`, `LoginUseCase` |
| Outbound Port (Persistence) | `{Entity}Port` | `ShowcasePort`, `UserPort`, `CatalogItemPort` |
| Outbound Port (External) | `{Service}Client` | `TripoModelClient`, `KakaoOAuthClient` |

### 2-3. Adapter (Implementation)

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Controller | `{Entity}Controller` | `ShowcaseController`, `AuthController` |
| Persistence Adapter | `{Entity}PersistenceAdapter` | `ShowcasePersistenceAdapter` |
| JPA Repository | `{Entity}JpaRepository` | `ShowcaseJpaRepository` |
| JPA Entity | `{Entity}JpaEntity` | `ShowcaseJpaEntity`, `UserJpaEntity` |
| Entity Mapper | `{Entity}Mapper` | `ShowcaseMapper`, `UserMapper` |
| External Client Impl | `{Service}ClientAdapter` | `TripoModelClientAdapter`, `KakaoOAuthClientAdapter` |

### 2-4. Domain

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Entity | `{Name}` | `Showcase`, `User`, `CatalogItem` |
| Value Object | `{Name}` | `ConditionGrade`, `AngleType` |
| Domain Policy | `{Name}Policy` | `ShowcaseImagePolicy` |
| Domain Repository | `{Entity}Repository` | `ShowcaseRepository` |

> `domain/` 패키지는 Spring, JPA 의존 금지.
> **예외**: 도메인 모델에 한해 Lombok `@Getter`, `@Builder`만 허용.

### 2-5. Application

| Type | Pattern | Example |
|:-----|:--------|:--------|
| UseCase Impl | `{Action}Service` | `CreateShowcaseService`, `LoginService` |
| Command (input) | `{Action}Command` | `CreateShowcaseCommand`, `UpdateProfileCommand` |
| Result (output) | `{Action}Result` | `CreateShowcaseResult`, `LoginResult` |

### 2-6. Adapter DTO

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Request DTO | `{Action}Request` | `CreateShowcaseRequest`, `LoginRequest` |
| Response DTO | `{Action}Response` | `ShowcaseDetailResponse`, `ShowcaseListResponse` |

> 모든 DTO는 Java `record` 타입을 사용한다. Lombok `@Data`는 사용 금지.

### 2-7. Exception

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Base Exception | `CustomException` | `CustomException` |
| Domain Exception | `{Reason}{Entity}Exception` | `NotFoundShowcaseException`, `InvalidShowcaseException` |
| Application Exception | `{Reason}{Action}Exception` | `FailedLoginException` |
| External Exception | `{Provider}{Reason}Exception` | `TripoGenerationFailedException` |

> 모든 예외는 `ErrorCode`를 통해 `CustomException`을 상속해야 한다. `RuntimeException` 직접 사용 금지.

### 2-8. ErrorCode

| Pattern | Example |
|:--------|:--------|
| `{DOMAIN}_{REASON}` | `AUTH_EXPIRED_TOKEN`, `SHOWCASE_NOT_FOUND` |
| `{DOMAIN}_{ENTITY}_{REASON}` | `USER_DUPLICATE_NICKNAME`, `CATALOG_ITEM_NOT_FOUND` |
| `{DOMAIN}_{DETAIL}_{REASON}` | `SHOWCASE_MODEL_ALREADY_GENERATING`, `SHOWCASE_MIN_IMAGE_REQUIRED` |

> - ErrorCode 메시지는 **한글**로 작성한다.
> - 접두사는 반드시 해당 도메인으로 시작한다.
> - 단순한 경우 `{DOMAIN}_{REASON}`, 대상을 명시해야 할 때 `{DOMAIN}_{ENTITY}_{REASON}`을 사용한다.

```java
// Good
public enum ErrorCode {
    // AUTH
    AUTH_INVALID_CODE(400, "유효하지 않은 인가 코드입니다"),
    AUTH_EXPIRED_TOKEN(401, "토큰이 만료되었습니다"),

    // USER
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    USER_DUPLICATE_NICKNAME(400, "이미 사용 중인 닉네임입니다"),

    // SHOWCASE
    SHOWCASE_NOT_FOUND(404, "쇼케이스를 찾을 수 없습니다"),
    SHOWCASE_NOT_OWNER(403, "쇼케이스 소유자만 수정 또는 삭제할 수 있습니다"),
    SHOWCASE_MIN_IMAGE_REQUIRED(400, "최소 1개의 이미지가 필요합니다"),
    SHOWCASE_MODEL_ALREADY_GENERATING(400, "3D 모델이 이미 생성 중입니다");

    private final int status;
    private final String message;
}
```

### 2-9. Test

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Unit Test | `{Class}Test` | `ShowcaseTest`, `CreateShowcaseServiceTest` |
| Integration Test | `{Class}IntegrationTest` | `ShowcaseControllerIntegrationTest` |

---

## 3. Code Style

### 3-1. 로그 메시지

```java
// Good
log.info("쇼케이스 생성 완료: showcaseId={}", showcaseId);
log.warn("3D 모델 생성 실패: showcaseId={}, reason={}", showcaseId, reason);
log.error("카탈로그 아이템 조회 실패: catalogItemId={}", catalogItemId, ex);

// Bad
log.info("쇼케이스 생성 완료: " + showcaseId);  // 문자열 연결 금지
```

### 3-2. 예외 메시지

```java
// Good - ErrorCode를 통해 관리
public enum ErrorCode {
    SHOWCASE_NOT_FOUND(404, "쇼케이스를 찾을 수 없습니다"),
    USER_DUPLICATE_NICKNAME(400, "이미 사용 중인 닉네임입니다"),
    AUTH_EXPIRED_TOKEN(401, "토큰이 만료되었습니다");
}

// Bad - 예외에 직접 메시지 작성
throw new RuntimeException("쇼케이스를 찾을 수 없습니다");
throw new CustomException(404, "쇼케이스를 찾을 수 없습니다");
```

### 3-3. 주석 / Javadoc

```java
// Good
/**
 * 주어진 커맨드로 새로운 쇼케이스를 생성한다.
 * modelSourceImages가 포함되면 비동기로 3D 모델 생성을 요청한다.
 *
 * @param command 쇼케이스 생성 커맨드
 * @return 생성된 쇼케이스 ID 및 3D 모델 상태
 */
public CreateShowcaseResult createShowcase(CreateShowcaseCommand command) { ... }

// Bad - 영문 주석
/** Creates a new showcase. */
public CreateShowcaseResult createShowcase(CreateShowcaseCommand command) { ... }
```

### 3-4. Bean Validation 메시지

```java
// Good
public record CreateShowcaseRequest(
    @NotNull(message = "카탈로그 아이템 ID는 필수입니다")
    Long catalogItemId,

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    String title,

    @NotNull(message = "상태 등급은 필수입니다")
    ConditionGrade conditionGrade
) {}

// Bad - 영문 메시지
public record CreateShowcaseRequest(
    @NotNull(message = "Catalog item ID is required")
    Long catalogItemId
) {}
```

### 3-5. DTO

```java
// Good - record 타입 사용
public record ShowcaseDetailResponse(
    Long showcaseId,
    String title,
    String ownerNickname,
    String conditionGrade,
    boolean isForSale
) {}

// Bad - Lombok @Data 사용
@Data
public class ShowcaseDetailResponse {
    private Long showcaseId;
    private String title;
}
```

### 3-6. 일급 컬렉션과 Value Object

#### 일급 컬렉션

컬렉션에 **검증, 계산, 필터링** 등 비즈니스 로직이 붙으면 일급 컬렉션으로 감싼다.

```java
// Bad — 검증이 Service에 산재
private void validateImages(List<UploadFile> images, int primaryImageIndex) {
    if (images == null || images.isEmpty()) { throw new MinImageRequiredException(); }
    if (primaryImageIndex < 0 || primaryImageIndex >= images.size()) { throw new PrimaryImageRequiredException(); }
}

// Good — 일급 컬렉션이 불변식을 보장
public class ShowcaseImages {

    private final List<UploadFile> files;
    private final int primaryIndex;

    public static ShowcaseImages create(List<UploadFile> files, int primaryIndex) {
        validateNotEmpty(files);
        validatePrimaryIndex(files, primaryIndex);
        return new ShowcaseImages(files, primaryIndex);
    }

    public UploadFile primaryImage() {
        return files.get(primaryIndex);
    }
}
```

**적용 기준**: 컬렉션에 아래 중 하나라도 해당하면 일급 컬렉션 후보다.
- 컬렉션 자체에 검증 규칙이 있음 (최소 N개, 중복 금지 등)
- 컬렉션에서 특정 요소를 꺼내는 로직이 반복됨 (대표 이미지, 첫 번째 항목 등)
- 컬렉션에 대한 계산이 Service에 산재 (합산, 중복 체크, 필터링 등)

#### Value Object (VO) vs Primitive

비즈니스 규칙이 있는 값은 VO로 감싼다. 단순 식별자/크기는 primitive가 적절하다.

```java
// primitive가 적절한 경우 — 비즈니스 규칙 없음
void delete(Long showcaseId)
List<Showcase> findRecent(int size)

// VO가 필요한 경우 — 검증/계산/비교 로직 존재
WearCount wearCount = WearCount.of(5);  // 음수 불가 규칙 캡슐화
```

| 기준 | primitive | VO |
|:-----|:---------:|:--:|
| 검증 로직 있음 (음수 불가, 범위 제한 등) | | O |
| 단위/의미가 있음 (가격, 횟수, 등급 등) | | O |
| 같은 검증이 여러 곳에서 반복됨 | | O |
| 단순 식별자, 크기, 인덱스 | O | |

### 3-7. 도메인 모델

```java
// Good - 정적 팩토리 메서드 + Builder
@Getter
public class Showcase {

    private final Long id;
    private final Long ownerId;
    private final String title;
    private final ConditionGrade conditionGrade;
    private final ShowcaseStatus status;

    @Builder
    private Showcase(Long id, Long ownerId, String title,
                     ConditionGrade conditionGrade, ShowcaseStatus status) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.conditionGrade = conditionGrade;
        this.status = status;
    }

    /**
     * ACTIVE 상태의 새로운 쇼케이스를 생성한다.
     */
    public static Showcase create(Long ownerId, String title, ConditionGrade conditionGrade) {
        validate(title);
        return Showcase.builder()
            .ownerId(ownerId)
            .title(title)
            .conditionGrade(conditionGrade)
            .status(ShowcaseStatus.ACTIVE)
            .build();
    }

    private static void validate(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidShowcaseException();
        }
    }
}

// Bad - public 생성자, 검증 없음
public class Showcase {
    public Showcase(String title) {
        this.title = title;
    }
}
```

### 3-8. 의존성 주입

```java
// Good - @RequiredArgsConstructor를 통한 생성자 주입
@Service
@RequiredArgsConstructor
public class CreateShowcaseService implements CreateShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final CatalogItemPort catalogItemPort;
}

// Bad - 필드 주입
@Service
public class CreateShowcaseService {

    @Autowired
    private ShowcasePort showcasePort;
}
```

### 3-9. 테스트 (BDD 스타일)

```java
@Test
@DisplayName("유효한 커맨드로 쇼케이스를 성공적으로 생성한다")
void createShowcase_success() {
    // Given
    var command = new CreateShowcaseCommand(1L, "Nike Mercurial Review", ConditionGrade.A);
    given(catalogItemPort.findById(1L)).willReturn(Optional.of(catalogItem));

    // When
    var result = createShowcaseService.createShowcase(command);

    // Then
    assertThat(result.showcaseId()).isNotNull();
    then(showcasePort).should().save(any(Showcase.class));
}

@Test
@DisplayName("카탈로그 아이템이 존재하지 않으면 예외를 던진다")
void createShowcase_catalogItemNotFound() {
    // Given
    var command = new CreateShowcaseCommand(999L, "Invalid", ConditionGrade.A);
    given(catalogItemPort.findById(999L)).willReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> createShowcaseService.createShowcase(command))
        .isInstanceOf(NotFoundCatalogItemException.class);
}
```
