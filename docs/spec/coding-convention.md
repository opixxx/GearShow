# GearShow Coding Convention

---

## 1. Language

All code must be written in **English only**.

| Target | Language | Example |
|:-------|:---------|:--------|
| Comments / Javadoc | English | `/** Creates a new showcase. */` |
| Log messages | English | `log.info("Showcase created: showcaseId={}", id)` |
| Exception messages | English | `"Showcase not found: " + showcaseId` |
| Variable / Method names | English | `findByOwnerId`, `catalogItemId` |
| Bean Validation messages | English | `@NotBlank(message = "Title is required")` |

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

> Domain package (`domain/`) prohibits Spring and JPA dependencies.
> **Exception**: Lombok `@Getter` and `@Builder` are allowed for domain models only.

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

> All DTOs must be Java `record` types. Lombok `@Data` is prohibited.

### 2-7. Exception

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Base Exception | `CustomException` | `CustomException` |
| Domain Exception | `{Reason}{Entity}Exception` | `NotFoundShowcaseException`, `InvalidShowcaseException` |
| Application Exception | `{Reason}{Action}Exception` | `FailedLoginException` |
| External Exception | `{Provider}{Reason}Exception` | `TripoGenerationFailedException` |

> All exceptions must extend `CustomException` via `ErrorCode`. Direct `RuntimeException` usage is prohibited.

### 2-8. ErrorCode

| Pattern | Example |
|:--------|:--------|
| `{DOMAIN}_{REASON}` | `AUTH_EXPIRED_TOKEN`, `SHOWCASE_NOT_FOUND` |
| `{DOMAIN}_{ENTITY}_{REASON}` | `USER_DUPLICATE_NICKNAME`, `CATALOG_ITEM_NOT_FOUND` |
| `{DOMAIN}_{DETAIL}_{REASON}` | `SHOWCASE_MODEL_ALREADY_GENERATING`, `SHOWCASE_MIN_IMAGE_REQUIRED` |

> - ErrorCode message is written in **English**. The frontend maps error codes to Korean messages for end users.
> - Prefix must start with the domain the error belongs to.
> - Use `{DOMAIN}_{REASON}` for simple cases, `{DOMAIN}_{ENTITY}_{REASON}` when the target needs to be explicit.

```java
// Good
public enum ErrorCode {
    // AUTH
    AUTH_INVALID_CODE(400, "Invalid authorization code"),
    AUTH_EXPIRED_TOKEN(401, "Token has expired"),

    // USER
    USER_NOT_FOUND(404, "User not found"),
    USER_DUPLICATE_NICKNAME(400, "Nickname already in use"),

    // SHOWCASE
    SHOWCASE_NOT_FOUND(404, "Showcase not found"),
    SHOWCASE_NOT_OWNER(403, "Only the showcase owner can modify or delete"),
    SHOWCASE_MIN_IMAGE_REQUIRED(400, "At least one image is required"),
    SHOWCASE_MODEL_ALREADY_GENERATING(400, "3D model is already being generated");

    private final int status;
    private final String message;
}

// Bad - Korean message
SHOWCASE_NOT_FOUND(404, "쇼케이스를 찾을 수 없습니다")

// Bad - no domain prefix
NOT_FOUND(404, "Not found")
EXPIRED_TOKEN(401, "Token has expired")
```

### 2-9. Test

| Type | Pattern | Example |
|:-----|:--------|:--------|
| Unit Test | `{Class}Test` | `ShowcaseTest`, `CreateShowcaseServiceTest` |
| Integration Test | `{Class}IntegrationTest` | `ShowcaseControllerIntegrationTest` |

---

## 3. Code Style

### 3-1. Log Messages

```java
// Good
log.info("Showcase created: showcaseId={}", showcaseId);
log.warn("3D model generation failed: showcaseId={}, reason={}", showcaseId, reason);
log.error("Failed to fetch catalog item: catalogItemId={}", catalogItemId, ex);

// Bad
log.info("쇼케이스 생성됨: showcaseId={}", showcaseId);
log.info("Showcase created: " + showcaseId);  // string concatenation
```

### 3-2. Exception Messages

```java
// Good - via ErrorCode
public enum ErrorCode {
    SHOWCASE_NOT_FOUND(404, "Showcase not found"),
    USER_DUPLICATE_NICKNAME(400, "Nickname already in use"),
    AUTH_EXPIRED_TOKEN(401, "Token has expired");
}

// Bad - direct message in exception
throw new RuntimeException("Showcase not found");
throw new CustomException(404, "Showcase not found");
```

### 3-3. Comments / Javadoc

```java
// Good
/**
 * Creates a new showcase with the given command.
 * If modelSourceImages are provided, triggers async 3D model generation.
 *
 * @param command showcase creation command
 * @return created showcase ID and optional 3D model status
 */
public CreateShowcaseResult createShowcase(CreateShowcaseCommand command) { ... }

// Bad
/** 쇼케이스를 생성합니다. */
public CreateShowcaseResult createShowcase(CreateShowcaseCommand command) { ... }
```

### 3-4. Bean Validation Messages

```java
// Good
public record CreateShowcaseRequest(
    @NotNull(message = "Catalog item ID is required")
    Long catalogItemId,

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    String title,

    @NotNull(message = "Condition grade is required")
    ConditionGrade conditionGrade
) {}

// Bad
public record CreateShowcaseRequest(
    @NotNull(message = "카탈로그 아이템 ID는 필수입니다")
    Long catalogItemId
) {}
```

### 3-5. DTO

```java
// Good - record type
public record ShowcaseDetailResponse(
    Long showcaseId,
    String title,
    String ownerNickname,
    String conditionGrade,
    boolean isForSale
) {}

// Bad - Lombok @Data
@Data
public class ShowcaseDetailResponse {
    private Long showcaseId;
    private String title;
}
```

### 3-6. Domain Model

```java
// Good - static factory method + Builder
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
     * Creates a new Showcase in ACTIVE status.
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

// Bad - public constructor, no validation
public class Showcase {
    public Showcase(String title) {
        this.title = title;
    }
}
```

### 3-7. Dependency Injection

```java
// Good - constructor injection via @RequiredArgsConstructor
@Service
@RequiredArgsConstructor
public class CreateShowcaseService implements CreateShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final CatalogItemPort catalogItemPort;
}

// Bad - field injection
@Service
public class CreateShowcaseService {

    @Autowired
    private ShowcasePort showcasePort;
}
```

### 3-8. Test (BDD Style)

```java
@Test
@DisplayName("Should create showcase successfully with valid command")
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
@DisplayName("Should throw exception when catalog item not found")
void createShowcase_catalogItemNotFound() {
    // Given
    var command = new CreateShowcaseCommand(999L, "Invalid", ConditionGrade.A);
    given(catalogItemPort.findById(999L)).willReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> createShowcaseService.createShowcase(command))
        .isInstanceOf(NotFoundCatalogItemException.class);
}
```
