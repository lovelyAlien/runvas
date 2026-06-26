# Backend Stack Design

## Context

Runvas is a docs-first monorepo with shared product, API, data model, geo, and GPX conventions in `docs/`.
Backend and mobile implementations must follow the documented API contract before adding fields or changing behavior.

The backend implementation has not been scaffolded yet.
The selected backend direction is Spring Boot with Java because it is familiar to the project owner and suitable for a structured REST API with authentication, persistence, validation, and testing.

## Decision

Runvas backend will use:

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- JUnit 5, MockMvc, and Testcontainers

The application will start as a modular monolith with domain-oriented packages.
Spring Data JPA is the default data access layer.
Native queries may be used for bounds search, future PostGIS integration, or other queries where SQL clarity matters.

## Architecture

The backend starts as one deployable Spring Boot application.
Feature packages should own their controller, service, repository, DTO, and domain types when needed.
Cross-cutting behavior belongs in `global`.

Initial package direction:

```text
com.runvas
  global
    config
    error
    security
  auth
  user
  course
  post
  comment
  like
  bookmark
```

Packages should be created as features are implemented, not prefilled with empty classes.

## Auth Design

Kakao login follows the existing `docs/api-contract.md` contract:

1. Mobile obtains `authorizationCode` from the Kakao SDK.
2. Mobile sends `provider`, `authorizationCode`, and `redirectUri` to `POST /api/auth/kakao`.
3. Backend exchanges the authorization code with Kakao.
4. Backend fetches Kakao user info.
5. Backend finds or creates a Runvas user by `provider = KAKAO` and internal `providerUserId`.
6. Backend returns a Runvas JWT as `accessToken`, plus `user` and `isNewUser`.

Kakao access tokens, Kakao refresh tokens, client secrets, and Kakao provider user IDs must not be exposed in Runvas API responses.

## Error Handling

Global error handling should map Spring and domain exceptions to the common API error shape in `docs/api-contract.md`.
Validation, authentication, authorization, not found, conflict, and internal errors should use the documented codes.

## Testing

The first backend implementation should include:

- MockMvc tests for `POST /api/auth/kakao`
- MockMvc tests for `GET /api/me` after JWT authentication exists
- Testcontainers-backed persistence tests for user creation and lookup
- Unit tests around Kakao API client behavior using a fake or mock client

## Documentation

The discoverable project-level backend architecture document is `docs/backend-architecture.md`.
If implementation changes external API behavior, update `docs/api-contract.md` and `docs/data-model.md` first.
