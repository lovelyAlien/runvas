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
- `TMAP_APP_KEY`

## Optional Environment

- `KAKAO_CLIENT_SECRET`
- `APPLE_BUNDLE_ID` (defaults to `com.runvas.mobile`)
- `APPLE_JWKS_URI` (defaults to `https://appleid.apple.com/auth/keys`)
- `APPLE_TEAM_ID` (Apple Developer Team ID; required for Apple token exchange/revoke to work — without it, Apple login still works but refresh tokens are never stored and account deletion never revokes Apple access)
- `APPLE_KEY_ID` (Key ID of the `.p8` Sign in with Apple private key)
- `APPLE_PRIVATE_KEY` (contents of the `.p8` private key; PEM format, literal `\n` for line breaks is accepted)
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `ADMIN_NOTIFICATION_EMAIL` — admin email
  notifications for new reports/blocks are silently disabled if these are left unset.

## 빌드 & 배포

전체 절차(GHCR 이미지 관리, VPS 배포 사전 준비물, 롤백 방법 등)는
[`../docs/deployment.md`](../docs/deployment.md)가 기준입니다. 요약하면:

1. `git tag backend-v{semver} && git push origin backend-v{semver}` — Docker 이미지를 빌드해
   GHCR(private)에 push하고, 이어서 운영 VPS에 SSH로 접속해 자동 배포합니다.
2. 배포 후 별도 헬스체크나 자동 롤백은 없습니다 — 문제가 생기면 이전 태그 이미지로 수동 롤백합니다.
3. DB 마이그레이션(Flyway)은 앱 기동 시 자동으로 수행되어 별도 스텝이 없습니다.

## Implemented MVP APIs

- `POST /api/auth/kakao`
- `POST /api/auth/apple`
- `GET /api/me`

`POST /api/auth/kakao` exchanges a Kakao authorization code on the backend and returns a Runvas JWT.
The Kakao access token and provider user ID are never returned in API responses.

`POST /api/auth/apple` verifies an Apple `identityToken` against Apple's JWKS and returns a Runvas
JWT. Using Sign in with Apple in production requires the "Sign in with Apple" capability to be
enabled for the app's bundle ID in the Apple Developer portal.
