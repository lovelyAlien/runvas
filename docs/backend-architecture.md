# 백엔드 아키텍처 기준

이 문서는 Runvas 백엔드 구현의 기술 스택과 기본 아키텍처 기준을 정의합니다.
API 요청/응답 필드, 에러 코드, 인증 필요 여부는 `api-contract.md`를 우선 기준으로 합니다.

## 기술 스택

Runvas 백엔드는 아래 스택으로 구현합니다.

| 항목 | 선택 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.x |
| HTTP API | Spring Web |
| 인증/인가 | Spring Security |
| 요청 검증 | Spring Validation |
| 데이터 접근 | Spring Data JPA |
| 데이터베이스 | PostgreSQL |
| 마이그레이션 | Flyway |
| 테스트 | JUnit 5, MockMvc, Testcontainers |

SQL 제어가 필요한 지도 bounds 검색, 집계, PostGIS 연동 후보 기능은 Spring Data JPA의 native query를 사용할 수 있습니다.
기본 CRUD와 도메인 관계는 JPA 엔티티와 repository를 우선 사용합니다.

## 애플리케이션 구조

백엔드는 하나의 Spring Boot 애플리케이션으로 시작하는 모듈형 모놀리스로 구현합니다.
패키지는 기능 도메인 기준으로 나누고, 전역 설정과 공통 관심사는 `global` 아래에 둡니다.

```text
backend/
  src/main/java/com/runvas/
    RunvasApplication.java
    global/
      config/
      error/
      security/
    auth/
      controller/
      service/
      dto/
    user/
      domain/
      repository/
      dto/
    course/
      domain/
      repository/
      controller/
      service/
      dto/
    post/
    comment/
    like/
    bookmark/
```

도메인 패키지는 필요해질 때 추가합니다.
MVP 구현 초기에 모든 패키지를 빈 상태로 먼저 만들지는 않습니다.

## API 기준

- 모든 Runvas API는 `/api` base path를 사용합니다.
- API 요청과 응답은 `docs/api-contract.md`의 필드명, 타입, 상태 코드, 에러 코드를 따릅니다.
- API 응답 모델은 `docs/data-model.md`의 노출 범위를 따릅니다.
- 구현 편의를 위해 문서에 없는 응답 필드를 임의로 추가하지 않습니다.
- 인증이 필요한 API는 `Authorization: Bearer <accessToken>` 헤더를 요구합니다.

## 인증 기준

MVP 인증은 카카오 소셜 로그인과 Runvas 자체 JWT로 구성합니다.

1. 모바일 앱은 카카오 SDK로 `authorizationCode`를 받습니다.
2. 모바일 앱은 `POST /api/auth/kakao`로 `authorizationCode`, `redirectUri`, `provider`를 전달합니다.
3. 백엔드는 카카오 토큰 API에서 카카오 액세스 토큰을 발급받습니다.
4. 백엔드는 카카오 사용자 정보 API를 호출합니다.
5. 백엔드는 `provider = KAKAO`, 카카오 사용자 ID 기준으로 Runvas 사용자를 조회하거나 생성합니다.
6. 백엔드는 Runvas API용 JWT를 `accessToken`으로 발급합니다.

카카오 액세스 토큰, 카카오 refresh token, client secret은 API 응답에 포함하지 않습니다.
카카오 사용자 ID는 내부 `providerUserId`로 저장하지만 API 응답에는 노출하지 않습니다.

## 데이터 기준

- PostgreSQL을 기본 데이터베이스로 사용합니다.
- Flyway 마이그레이션을 통해 스키마 변경을 추적합니다.
- MVP 초기에는 JPA 엔티티와 DB 테이블을 가깝게 유지합니다.
- 공개 API 모델과 JPA 엔티티를 직접 공유하지 않고, controller 응답 DTO로 변환합니다.
- `providerUserId`처럼 내부 저장 전용 필드는 응답 DTO에 포함하지 않습니다.

## 에러 처리

공통 에러 응답은 `docs/api-contract.md`의 형식을 사용합니다.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request body",
    "details": [
      {
        "field": "path",
        "message": "Path must contain 2-5000 points"
      }
    ]
  }
}
```

전역 예외 처리는 `global/error`에서 관리합니다.
Spring Validation 오류, 인증 실패, 권한 실패, 리소스 없음, 내부 오류는 문서의 공통 에러 코드로 변환합니다.

## 테스트 기준

- controller 테스트는 MockMvc로 요청/응답 계약을 검증합니다.
- repository 또는 DB 의존 테스트는 Testcontainers PostgreSQL을 사용합니다.
- 인증 테스트는 JWT 유효성, 누락, 만료, 잘못된 토큰 케이스를 포함합니다.
- `POST /api/auth/kakao`는 카카오 API 클라이언트를 테스트 대역으로 분리해 성공, 인증 실패, 필수 필드 누락을 검증합니다.

## 운영 설정

환경별 설정값은 Spring profile과 환경 변수로 주입합니다.
민감 정보는 저장소에 커밋하지 않습니다.

필수 설정 후보:

| 환경 변수 | 설명 |
| --- | --- |
| `DATABASE_URL` | PostgreSQL 연결 URL |
| `DATABASE_USERNAME` | PostgreSQL 사용자명 |
| `DATABASE_PASSWORD` | PostgreSQL 비밀번호 |
| `JWT_SECRET` | Runvas JWT 서명 키 |
| `KAKAO_REST_API_KEY` | 카카오 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 client secret. 설정 여부는 카카오 앱 설정에 맞춤 |

## 이후 변경 원칙

백엔드 구현 중 API 계약, 데이터 모델 노출 범위, 인증 방식, 상태 코드가 바뀌면 구현보다 먼저 `docs/` 문서를 수정합니다.
Spring 내부 구조만 바뀌고 외부 계약이 유지되는 경우에는 이 문서 또는 `backend/README.md`를 업데이트합니다.
