---
name: architecture-patterns
description: GearShow project Hexagonal Architecture guide. Java/Spring domain design and package structure definition.
---

# Hexagonal Architecture Guidelines for GearShow Project

You are an expert Java/Spring backend developer working on the Hoops project. You must follow hexagonal architecture (ports and adapters) with domain-driven design principles. These guidelines define WHERE components belong. For HOW to implement them, refer to `/clean-code` skill.

## Core Principles

The dependency direction must always flow inward: adapter → application → domain. The domain layer is the core and must have zero external dependencies. Never violate this direction under any circumstances.

## Layer Definitions and Constraints

### Domain Layer

Location: `{domain}/domain/`

The domain layer contains pure business logic and domain concepts. This layer must remain completely isolated from frameworks and infrastructure concerns.

**Permitted elements**: Pure Java POJOs, Lombok annotations limited to `@Getter`, `@Builder`, and `@AllArgsConstructor` only.

**Prohibited elements**: Spring annotations, JPA annotations, Lombok `@Data`, any framework dependencies, any reference to application or adapter layer.

The domain layer contains five subpackages:
- `model/` - Entities with identity
- `vo/` - Immutable value objects
- `repository/` - Repository interfaces following DDD pattern
- `policy/` - Cross-entity validation logic
- `exception/` - Domain rule violation exceptions

### Application Layer

Location: `{domain}/application/`

The application layer orchestrates domain objects to fulfill use cases. This layer may depend only on the domain layer.

**Permitted elements**: Spring `@Service` and `@Transactional` annotations only.

**Prohibited elements**: JPA annotations, HTTP-related code, direct external API calls, any reference to adapter layer.

The application layer contains five subpackages:
- `port/in/` - Inbound port interfaces defining use cases
- `port/out/` - Outbound port interfaces for external services
- `service/` - Use case implementations
- `dto/` - Command and response objects for use case input/output
- `exception/` - Use case failure exceptions

### Adapter Layer

Location: `{domain}/adapter/`

The adapter layer connects the application to the external world. This layer may depend on both application and domain layers.

**Permitted elements**: All framework annotations including Spring MVC, JPA, and external library integrations.

The adapter layer is divided into inbound and outbound sections:
- Inbound adapters in `adapter/in/web/` contain controllers and web DTOs
- Outbound adapters in `adapter/out/persistence/` contain JPA entities and repository implementations
- External service adapters in `adapter/out/{external}/{provider}/` contain vendor-specific implementations organized by provider

### Infrastructure Layer

Location: `{domain}/infrastructure/`

The infrastructure layer contains framework configuration only.
- `config/` - Spring configuration classes

## Naming Conventions

### Interface and Class Naming

| Type | Suffix | Location | Example |
|------|--------|----------|---------|
| Inbound ports | `*UseCase` | `application/port/in/` | `OAuthLoginUseCase`, `CreateMatchUseCase` |
| Outbound ports | `*Port` | `application/port/out/` | `OAuthPort`, `JwtTokenPort`, `NotificationPort` |
| Domain repositories | `*Repository` | `domain/repository/` | `AuthAccountRepository`, `MatchRepository` |
| Persistence adapters | `*JpaAdapter` | `adapter/out/persistence/` | `AuthAccountJpaAdapter` |
| JPA entities | `*JpaEntity` | `adapter/out/persistence/` | `AuthAccountJpaEntity`, `MatchJpaEntity` |

### Vendor-Neutral Naming

Domain and application layer objects must never contain vendor-specific names. Use generic names that describe the concept, not the implementation.

| Incorrect | Correct |
|-----------|---------|
| `KakaoUserInfo` | `OAuthUserInfo` |
| `KakaoOAuthPort` | `OAuthPort` |
| `GoogleTokenResponse` | `OAuthTokenInfo` |

Vendor-specific names are permitted only in the adapter layer under the appropriate provider directory.
Example: `adapter/out/oauth/kakao/KakaoOAuthAdapter`

## Exception Design

### Exception Hierarchy

All custom exceptions must extend from `BusinessException` which is abstract.
- `DomainException` extends `BusinessException` for domain rule violations
- `ApplicationException` extends `BusinessException` for use case failures

Never throw `RuntimeException`, `IllegalArgumentException`, or `IllegalStateException` directly. Always create specific exception classes.

### Exception Location Rules

| Location | Usage | Example |
|----------|-------|---------|
| `domain/exception/` | Domain rule violations | `InvalidNicknameException`, `MatchAlreadyFullException` |
| `application/exception/` | Use case failures | `DuplicateNicknameException`, `InvalidTempTokenException` |
| `adapter/out/{external}/{provider}/exception/` | Vendor-specific API failures | `KakaoApiException`, `InvalidAuthCodeException` |

### Error Code Naming Patterns

| HTTP Status | Pattern | Example |
|-------------|---------|---------|
| 404 | `*_NOT_FOUND` | `MATCH_NOT_FOUND`, `USER_NOT_FOUND` |
| 409 | `ALREADY_*`, `DUPLICATE_*` | `ALREADY_PARTICIPATING`, `DUPLICATE_NICKNAME` |
| 403 | `NOT_*` | `NOT_HOST`, `NOT_PARTICIPANT` |
| 400 | `INVALID_*`, `*_EXCEEDED` | `INVALID_MATCH_TIME`, `CANCEL_TIME_EXCEEDED` |

## DTO Design Rules

### DTO Types and Locations

| Type | Suffix | Location | Example |
|------|--------|----------|---------|
| Use case input | `*Command` | `application/dto/` | `CreateMatchCommand`, `SignupCommand` |
| Use case output | `*Result` | `application/dto/` | `OAuthCallbackResult`, `MatchDetailResult` |
| HTTP request | `*Request` | `adapter/in/web/dto/` | `CreateMatchRequest`, `SignupRequest` |
| HTTP response | `*Response` | `adapter/in/web/dto/` | `MatchDetailResponse`, `AuthUrlResponse` |

### DTO Implementation Rules

- All DTOs must be implemented as Java record types for immutability and conciseness.
- Never expose domain entities directly from controllers. Always convert to response DTOs.
- Never accept domain entities as controller parameters. Always use request DTOs and convert to commands.

## Dependency Injection Rules

- Use constructor injection exclusively. Never use `@Autowired` field injection or setter injection.
- Apply `@RequiredArgsConstructor` from Lombok to generate constructors.
- Declare all injected dependencies as `private final` fields.

## Adapter Implementation Patterns

### Persistence Adapter Pattern

Persistence adapters must implement domain repository interfaces and handle all JPA entity conversions internally.

```java
@Repository
@RequiredArgsConstructor
public class AuthAccountJpaAdapter implements AuthAccountRepository {
    private final SpringDataAuthAccountRepository jpaRepository;

    @Override
    public AuthAccount save(AuthAccount authAccount) {
        AuthAccountJpaEntity entity = toJpaEntity(authAccount);
        AuthAccountJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private AuthAccountJpaEntity toJpaEntity(AuthAccount domain) { /* ... */ }
    private AuthAccount toDomain(AuthAccountJpaEntity entity) { /* ... */ }
}
```

### External Service Adapter Pattern

External service adapters must implement outbound port interfaces and convert vendor-specific responses to domain value objects.

Organize adapters by external service type and then by provider.
Example: `adapter/out/oauth/kakao/`, `adapter/out/oauth/google/`, `adapter/out/payment/toss/`

```java
@Component
@RequiredArgsConstructor
public class KakaoOAuthAdapter implements OAuthPort {
    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        KakaoUserResponse response = callKakaoApi(accessToken);
        return toOAuthUserInfo(response);
    }

    private OAuthUserInfo toOAuthUserInfo(KakaoUserResponse response) { /* ... */ }
}
```

## Prohibited Patterns Summary

- Never allow dependencies to flow outward from domain to application or adapter layers.
- Never use framework annotations in the domain layer except permitted Lombok annotations.
- Never use `@Autowired` field injection anywhere in the codebase.
- Never throw generic exceptions like `RuntimeException` or `IllegalArgumentException`.
- Never expose JPA entities outside the persistence adapter.
- Never use vendor-specific names in domain or application layers.
- Never call external services directly from domain or application layers without going through ports.
- Never place `@Transactional` in domain layer or adapter layer.

## Package Structure Reference

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

## Implementation Checklist

When creating a new domain feature, verify each component exists in the correct location.

### Domain layer checklist
- [ ] Domain model in `domain/model/` with factory methods
- [ ] Value objects in `domain/vo/` as records
- [ ] Repository interface in `domain/repository/`
- [ ] Policy classes in `domain/policy/` if cross-entity validation needed
- [ ] Domain exceptions in `domain/exception/`

### Application layer checklist
- [ ] UseCase interface in `application/port/in/`
- [ ] Outbound port interface in `application/port/out/` if external service needed
- [ ] Service implementation in `application/service/`
- [ ] Command and result DTOs in `application/dto/`
- [ ] Application exceptions in `application/exception/`

### Adapter layer checklist
- [ ] Controller in `adapter/in/web/`
- [ ] Request and response DTOs in `adapter/in/web/dto/`
- [ ] JPA entity and adapter in `adapter/out/persistence/`
- [ ] External service adapter in `adapter/out/{external}/{provider}/` if needed

### Infrastructure layer checklist
- [ ] Configuration class in `infrastructure/config/` if needed

## Related Skills

For implementation patterns and code quality guidelines, refer to:
- `/clean-code` - Self-validating entities, Value Object patterns, Transaction strategies, Tell Don't Ask principle