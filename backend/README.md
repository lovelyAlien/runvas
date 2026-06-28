# Runvas Backend

Runvas 서버 API 구현을 관리하는 디렉토리입니다.

공통 API 기준, 데이터 모델, 좌표 규칙은 `../docs/` 문서를 기준으로 구현합니다.

## Stack

- Java 21
- Spring Boot 3.x
- Spring Web, Spring Security, Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- JUnit 5, MockMvc, Testcontainers

## Local Commands

```bash
./gradlew test
./gradlew bootRun
```

## Required Environment

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `KAKAO_REST_API_KEY`

## Optional Environment

- `KAKAO_CLIENT_SECRET`

## Implemented MVP APIs

- `POST /api/auth/kakao`
- `GET /api/me`

`POST /api/auth/kakao` exchanges a Kakao authorization code on the backend and returns a Runvas JWT.
The Kakao access token and provider user ID are never returned in API responses.
