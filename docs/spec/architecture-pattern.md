# Architecture Pattern

---

## Core Principles

The dependency direction must always flow inward: **Adapter → Application → Domain**.
The domain layer is the core and must have zero external dependencies. Never violate this direction under any circumstances.

---

## 1. Layer Definitions and Constraints

### Domain Layer

Location: `{domain}/domain/`

The domain layer contains pure business logic and domain concepts. This layer must remain completely isolated from frameworks and infrastructure concerns.

**Permitted**: Pure Java POJOs, Lombok `@Getter` and `@Builder` only.

**Prohibited**: Spring annotations, JPA annotations, Lombok `@Data`/`@Setter`/`@RequiredArgsConstructor`, any framework dependencies, any reference to application or adapter layer.

The domain layer contains five subpackages:
- `model/` — Entities with identity and business rules
- `vo/` — Immutable value objects
- `repository/` — Repository interfaces following DDD pattern
- `policy/` — Cross-entity validation logic
- `exception/` — Domain rule violation exceptions

### Application Layer

Location: `{domain}/application/`

The application layer orchestrates domain objects to fulfill use cases. This layer may depend only on the domain layer.

**Permitted**: Spring `@Service`, `@Transactional`, `@RequiredArgsConstructor` only.

**Prohibited**: JPA annotations, HTTP-related code, direct external API calls, any reference to adapter layer.

The application layer contains five subpackages:
- `port/in/` — Inbound port interfaces defining use cases
- `port/out/` — Outbound port interfaces for external systems
- `service/` — Use case implementations
- `dto/` — Command (input) and Result (output) objects as records
- `exception/` — Use case failure exceptions

### Adapter Layer

Location: `{domain}/adapter/`

The adapter layer connects the application to the external world. This layer may depend on both application and domain layers.

**Permitted**: All framework annotations including Spring MVC, JPA, and external library integrations.

The adapter layer is divided into inbound and outbound:
- `adapter/in/web/` — Controllers and web DTOs (Request / Response)
- `adapter/out/persistence/` — JPA entities, mappers, and repository implementations
- `adapter/out/{external}/{provider}/` — Vendor-specific implementations organized by provider

### Infrastructure Layer

Location: `{domain}/infrastructure/`

Contains framework configuration only.
- `config/` — Spring configuration classes, client setup

---

## 2. Aggregate & FK Strategy

### Same Aggregate (FK, same lifecycle)

Entities within the same aggregate share a lifecycle and are connected via FK.
JPA `@ManyToOne` / `@OneToMany` is **not used** — only ID reference with `@Column`.

### Cross-Aggregate (Logical reference, ID only)

Different aggregates reference each other by ID value only. No JPA relationship mapping.
This avoids unnecessary entity fetching during domain ↔ JPA entity mapping.

```java
// Good — ID reference only
@Column(name = "owner_id", nullable = false)
private Long ownerId;

// Bad — JPA relationship across aggregates
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_id")
private UserJpaEntity owner;
```

---

## 3. Domain Model Rules

- Create via **static factory method + @Builder**. Never expose public constructor.
- Validate invariants inside factory method.
- Prefer `final` fields, no setters.
- Never pass `null` to constructor — required fields must always have values.

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

## 4. DTO Flow

```
[Client] ←→ Request/Response ←→ [Controller] ←→ Command/Result ←→ [Service] ←→ [Domain Model]
             (adapter/in/web/dto)                 (application/dto)
```

| Layer | Input | Output | Type |
|:------|:------|:-------|:-----|
| Adapter (Web) | `{Action}Request` | `{Action}Response` | record |
| Application | `{Action}Command` | `{Action}Result` | record |
| Domain | Domain Model | Domain Model | class |

**Conversion responsibility:**
- Request → Command: in Controller or `request.toCommand()`
- Result → Response: in Controller or `Response.from(result)`
- Domain ↔ JPA Entity: in Persistence Adapter via `{Entity}Mapper`

Never expose domain models or JPA entities directly through the API.

---

## 5. Persistence Adapter Pattern

JPA Entity and Domain Model are **separate classes**. Mapping occurs inside the adapter only.

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

## 6. External Service Adapter Pattern

External service adapters implement outbound port interfaces and convert vendor-specific responses to domain objects.

Organize by service type, then by provider. Vendor-specific names are permitted **only** in adapter layer.

```
adapter/out/
├── {external}/
│   └── {provider}/
│       ├── {Provider}{Service}ClientAdapter.java
│       └── exception/
```

Domain and application layers must use **vendor-neutral** names:

| Bad (vendor-specific) | Good (vendor-neutral) |
|:---------------------|:---------------------|
| `TripoModelPort` | `ModelGenerationClient` |
| `KakaoOAuthPort` | `OAuthClient` |
| `KakaoUserInfo` | `OAuthUserInfo` |

---

## 7. Transaction Strategy

- `@Transactional` is placed **only** in the Application Service layer.
- Domain layer and Adapter layer must **not** have `@Transactional`.
- Read-only queries use `@Transactional(readOnly = true)`.

---

## 8. Exception Design

### Hierarchy

```
CustomException (extends RuntimeException)
├── Domain exceptions     ({domain}/domain/exception/)
├── Application exceptions ({domain}/application/exception/)
└── External exceptions   ({domain}/adapter/out/{external}/{provider}/exception/)
```

### Rules

- All exceptions must extend `CustomException` via `ErrorCode`.
- Direct `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` is **prohibited**.
- Exception naming: `{Reason}{Entity}Exception`
- ErrorCode naming: `{DOMAIN}_{REASON}` or `{DOMAIN}_{ENTITY}_{REASON}`
- ErrorCode messages are written in **Korean**.

---

## 9. Prohibited Patterns

- Never allow dependencies to flow outward from domain to application or adapter layers.
- Never use framework annotations in the domain layer except `@Getter` and `@Builder`.
- Never use `@Autowired` field injection. Use `@RequiredArgsConstructor` + `private final`.
- Never throw generic exceptions like `RuntimeException` or `IllegalArgumentException`.
- Never expose JPA entities outside the persistence adapter.
- Never use vendor-specific names in domain or application layers.
- Never call external services directly without going through port interfaces.
- Never place `@Transactional` in domain or adapter layer.
- Never use JPA relationship mapping (`@ManyToOne`, `@OneToMany`) across aggregates.

---

## 10. Package Structure Reference

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

## 11. Implementation Checklist

When creating a new domain feature, verify each component:

### Domain Layer
- [ ] Domain model in `domain/model/` with static factory method
- [ ] Value objects in `domain/vo/` if needed
- [ ] Repository interface in `domain/repository/`
- [ ] Policy in `domain/policy/` if cross-entity validation needed
- [ ] Domain exceptions in `domain/exception/`

### Application Layer
- [ ] UseCase interface in `application/port/in/`
- [ ] Outbound port in `application/port/out/` if external system needed
- [ ] Service implementation in `application/service/` with `@Transactional`
- [ ] Command / Result DTOs in `application/dto/` as records

### Adapter Layer
- [ ] Controller in `adapter/in/web/` delegating to UseCase
- [ ] Request / Response DTOs in `adapter/in/web/dto/` as records
- [ ] JPA entity + Mapper + Adapter in `adapter/out/persistence/`
- [ ] External adapter in `adapter/out/{external}/{provider}/` if needed

### Infrastructure Layer
- [ ] Configuration in `infrastructure/config/` if needed
