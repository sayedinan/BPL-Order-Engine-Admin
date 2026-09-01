---
name: dto-validation
description: v0.3 — DTOs are the API contract. Request and response DTOs, jakarta.validation, the "entity is not a DTO" rule, and what must never appear in a response (passwords, hashes, encrypted columns).
---

# DTOs and validation (v0.3)

The JPA entity is the persistence model. The DTO is the API contract.
These are different things, and conflating them is the most common
backend bug in a Spring Boot project. This skill pins the rules.

## The rule

**The JPA entity is never serialized to the client.** Not as a
return type, not as a field on a DTO, not as a `Map<String, Object>`.
Always map to a DTO in the service layer or the controller.

The DTO is what the client sees. The DTO is what the client sends.
The DTO has validation. The DTO is the source of truth for "what
this endpoint accepts and returns."

## Request DTOs (what the client sends)

- `record`s, immutable.
- Annotated with `jakarta.validation` constraints.
- The controller method takes the DTO with `@Valid @RequestBody`.
- Validation errors → 400 with the standard error envelope.

```java
public record CreateUserRequest(
    @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String username,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotNull RoleType roleType,
    @NotNull Set<@NotBlank String> assignedEngineCodes
) {}
```

Note: `password` lives on the request DTO but **never** on the response DTO. The entity stores the BCrypt hash; the request DTO carries the plaintext; the response DTO carries nothing password-related.

## Response DTOs (what the client receives)

- `record`s, immutable.
- **No sensitive fields.** Specifically:
  - `UserResponse` does not include `passwordHash`.
  - `EngineResponse` does not include `serverPassword` (not even the encrypted form).
  - `AuditLogResponse` does not include any field that could be a credential.
- `Instant` fields serialized as ISO-8601 UTC (Jackson's default with `JavaTimeModule`).
- Enum fields serialized as their `.name()` (default Jackson behavior for enums on a record).

```java
public record UserResponse(
    UUID id,
    String username,
    String email,                       // optional, can be null
    RoleType role,
    Set<String> assignedEngineCodes,   // codes, not full Engine objects
    boolean mustChangePassword,         // for the redirect logic
    Instant createdAt,
    Instant updatedAt
) {}

public record EngineResponse(
    UUID id,
    String code,
    String name,
    EngineMode mode,
    String serverIp,
    String serverUsername,             // not a secret
    String startScript,
    String stopScript,
    String logScript,
    EngineStatus status,
    Instant lastTransitionAt,
    Instant createdAt,
    Instant updatedAt
) {
    // No serverPassword. Not "redacted"; the field does not exist.
}
```

## Validation surface

| Annotation | Where it goes | What it means |
|---|---|---|
| `@NotNull` | Required object fields | The field must be present (can be blank for strings — use `@NotBlank` for that) |
| `@NotBlank` | Required string fields | The field must be present and not all whitespace |
| `@Size(min, max)` | Strings, collections, arrays | Length bounds |
| `@Pattern(regexp)` | Strings | Regex check (e.g. `^[A-Z0-9_]{2,16}$` for engine code) |
| `@Email` | Email fields | Standard email regex |
| `@Min`, `@Max` | Numeric fields | Value bounds |
| `@Past`, `@Future` | Date/time fields | Temporal bound |
| `@Valid` | Cascading validation (e.g. a request DTO with nested DTOs) | Validate the nested DTO too |

The controller method:

```java
@PostMapping
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
    UserResponse response = userService.create(req);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

A missing field or a too-short password produces a 400 with the standard error envelope. The `MethodArgumentNotValidException` is caught by `ApiExceptionHandler` (see `error-envelope` skill) and translated to a 400 with the validation messages in `details`.

## What the controller never returns

- `User` (entity)
- `Engine` (entity)
- `AuditLog` (entity)
- `Map<String, Object>` ad-hoc shapes
- `ResponseEntity<Object>` with the body being a `User` or `Engine`

If you see any of these in a controller return type, the code is wrong.

## What the DTO never contains

- A password, plaintext or hashed. The request DTO has `password` (plaintext, one-shot). The response DTO has nothing.
- An encrypted column. `Engine.serverPassword` is read from the DB, decrypted by Jasypt, used in the SSH session, and never returned. The response DTO does not have a `serverPassword` field at all — not "redacted," not "omitted for security." The field does not exist.
- A `User.assignedEngines` as a `Set<Engine>`. The response uses `Set<String>` of engine codes. The client doesn't need (and shouldn't get) the full engine rows.
- Hibernate's lazy-loading proxies. If a DTO field is a `Set<Engine>` and Hibernate has not fetched the join, the response will fail to serialize. Always populate DTOs from a fully-fetched read.

## The DTO folder layout

```
manager/
├── user/
│   ├── User.java                       # entity
│   ├── UserRepository.java
│   ├── UserService.java
│   ├── UserController.java
│   └── dto/
│       ├── CreateUserRequest.java
│       ├── UpdateUserRolesRequest.java
│       └── UserResponse.java
├── engine/
│   ├── EngineEntity.java
│   ├── ...
│   └── dto/
│       ├── CreateEngineRequest.java
│       ├── UpdateEngineSshRequest.java
│       ├── EngineResponse.java
│       ├── EngineStatusResponse.java
│       ├── EngineActionResponse.java
│       └── LogPageResponse.java
└── audit/
    ├── AuditLog.java
    ├── ...
    └── dto/
        └── AuditLogResponse.java
```

`dto/` is a per-package subfolder. The DTOs for `user` live in `user/dto/`, not in a flat `dto/` at the root.

## Anti-patterns

- **Don't return the entity from a controller.** Even "just for now." It puts the schema in the API contract.
- **Don't put validation on the entity.** Validation is a request-side concern. The entity has `@Column(nullable = false)` for the DB; the request DTO has `@NotBlank` for the API.
- **Don't use `Map<String, Object>` as a "flexible DTO."** Pick a real record. `Map` has no validation and no documentation value.
- **Don't use Lombok `@Data` on DTOs.** DTOs are records; they don't need setters.
- **Don't reuse the request DTO as the response DTO.** Different shapes, different validation, different fields. Even if 80% of the fields are the same, two records.
- **Don't put the BCrypt hash on a response DTO "for debugging."** It's not debuggable through the API. The DB is the source; the response DTO has no password-related field.
- **Don't use `@JsonIgnore` on entity fields to "fix" serialization issues.** The fix is to use a DTO. `@JsonIgnore` on the entity is a code smell.
