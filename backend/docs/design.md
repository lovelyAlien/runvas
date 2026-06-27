# Runvas Backend 설계

이 문서는 `../docs/api-contract.md`, `data-model.md`, `geo-conventions.md`, `collaboration.md`를
Spring Boot로 구현하기 위한 설계다. **필드명·타입·에러 코드는 항상 `../docs/`가 기준**이고,
이 문서는 그걸 어떤 패키지/테이블/클래스로 구현할지만 다룬다 (`docs/collaboration.md`의
변경 절차: docs 먼저 수정 → 구현 수정).

## 기술 스택

- **Spring Boot 3.3.x, Java 17** — 신규 프로젝트라 LTS 최신 조합 사용.
- **Spring Data JPA + MySQL 8.0** — `data-model.md`가 명시하길 "DB 테이블명, 인덱스명, ORM 엔티티
  구조는 backend에서 별도 결정 가능"이라고 해서 MVP 개발 속도를 위해 JPA를 선택했다. DB는
  사용자 요청에 따라 MySQL로 결정 (초기 스캐폴드 때는 H2/PostgreSQL로 시작했다가 교체함).
  로컬 개발은 다른 프로젝트의 MySQL과 분리된 전용 컨테이너를 쓴다:
  ```bash
  docker run -d --name runvas-mysql \
    -e MYSQL_ROOT_PASSWORD=runvas_dev_root \
    -e MYSQL_DATABASE=runvas -e MYSQL_USER=runvas -e MYSQL_PASSWORD=runvas_dev_password \
    -p 3308:3306 mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
  ```
  (시스템에 이미 3306 포트로 다른 MySQL이 떠 있을 수 있어 3308로 분리했다 — `application-dev.yml` 참고.)
  (참고: 사용자의 다른 회사 프로젝트 `kicm-be`는 MyBatis만 쓰는 컨벤션이지만, 그건 그
  멀티모듈 프로젝트의 별도 컨벤션이고 Runvas는 독립된 신규 프로젝트라 따르지 않는다.)
- **Spring Security + JWT**(자체 발급) — 카카오 로그인 자체는 OAuth, 그 이후 클라이언트가 쓰는
  토큰은 `docs/api-contract.md`가 "서버는 자체 `accessToken`을 발급"한다고 명시했으므로 자체 JWT.
- **Lombok** — `@RequiredArgsConstructor` 생성자 주입.
- **Gradle**.

## 패키지 구조

```
com.runvas.backend
├── RunvasBackendApplication.java
├── common/
│   ├── ApiError.java, ApiErrorDetail.java   # docs/api-contract.md 공통 에러 응답 형식
│   ├── ApiException.java                     # code + http status를 들고 던지는 런타임 예외
│   ├── GlobalExceptionHandler.java            # @RestControllerAdvice, ApiException → {"error": {...}}
│   ├── PageInfo.java                          # { nextCursor }
│   └── GeoPoint.java, GeoBounds.java          # 공통 좌표 타입 (latitude/longitude, lat/lng 축약 금지)
├── config/
│   ├── SecurityConfig.java                    # 인증 정책별(None/Optional/Required) 필터 체인
│   └── JacksonConfig.java                     # ISO-8601 UTC 직렬화
├── auth/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java           # Authorization: Bearer 파싱, Optional 엔드포인트는
│   │                                            토큰 없거나 무효해도 통과(principal만 비움)
│   ├── kakao/KakaoOAuthClient.java            # 카카오 토큰 교환 + 사용자 정보 조회
│   ├── dto/KakaoLoginRequest.java, AuthResponse.java
│   └── AuthController.java                    # POST /auth/kakao
├── user/
│   ├── User.java (JPA Entity), UserRepository.java
│   ├── dto/UserResponse.java, PublicProfileResponse.java, UpdateMeRequest.java
│   └── MeController.java                      # GET/PATCH /me
├── course/
│   ├── Course.java (JPA Entity), CourseRepository.java
│   ├── RoutePointListConverter.java           # List<RoutePoint> ↔ JSON 컬럼 (AttributeConverter)
│   ├── dto/CourseResponse.java, CourseSummaryResponse.java,
│   │       CreateCourseRequest.java, UpdateCourseRequest.java
│   ├── CourseValidator.java                   # sequence 연속성, 좌표 개수/범위, 거리 제한, bounds 포함 검증
│   ├── CourseService.java
│   └── CourseController.java                  # POST/GET/PATCH/DELETE /courses, GET /courses/{id}/gpx
├── bookmark/
│   ├── CourseBookmark.java, CourseBookmarkRepository.java
│   └── BookmarkController.java                # POST/DELETE /courses/{id}/bookmarks, GET /me/bookmarked-courses
└── community/
    ├── Post.java, Comment.java, Like.java (JPA Entities)
    ├── PostRepository.java, CommentRepository.java, LikeRepository.java
    ├── dto/...
    ├── PostController.java, CommentController.java, LikeController.java
```

## DB 스키마 (요지)

| 테이블 | 비고 |
| --- | --- |
| `users` | `(provider, provider_user_id)` 유니크. `provider_user_id`는 절대 API 응답에 노출 안 함 |
| `courses` | `path`는 JSON 컬럼(`RoutePointListConverter`)으로 전체 교체 저장. `bounds`는 `sw_lat/sw_lng/ne_lat/ne_lng` 4개 컬럼으로 분리 — `GET /courses`의 bounds 겹침 검색에 인덱스를 걸기 위함 |
| `course_tags` | `(course_id, tag)` — JSON 배열 대신 정규화 테이블로 둬서 `tag` 쿼리 파라미터 필터가 단순 `WHERE`로 가능하게 함 |
| `course_bookmarks` | PK `(user_id, course_id)` |
| `posts` | `attached_course_id` nullable FK |
| `post_tags` | `(post_id, tag)` |
| `comments` | `post_id`, `author_id` FK |
| `likes` | PK `(user_id, target_type, target_id)` — Course/Post 좋아요를 한 테이블로 통합 (`data-model.md`의 `LikeTargetType`) |

`path`를 JSON 컬럼으로 두는 이유: `data-model.md`가 "코스 수정 시 path는 전체 교체"라고 명시해서
포인트 단위 쿼리가 필요 없다. 포인트별 조회/수정이 필요해지면 그때 별도 테이블로 정규화한다(YAGNI).

## 인증 흐름

1. 모바일이 카카오 SDK로 받은 `authorizationCode`를 `POST /auth/kakao`로 전송.
2. `KakaoOAuthClient`가 카카오 토큰 교환 API(`https://kauth.kakao.com/oauth/token`) 호출 →
   카카오 액세스 토큰 획득 → 카카오 사용자 정보 API(`https://kapi.kakao.com/v2/user/me`) 호출 →
   `providerUserId`, `email` 획득.
3. `(provider='KAKAO', providerUserId)`로 `User` 조회, 없으면 생성(`isNewUser=true`).
4. `JwtTokenProvider`로 자체 `accessToken` 발급, 응답: `{ accessToken, user, isNewUser }`.
5. 이후 요청은 `Authorization: Bearer <accessToken>` → `JwtAuthenticationFilter`가 검증.
   - `Required` 엔드포인트: 토큰 없거나 무효하면 401.
   - `Optional` 엔드포인트: 토큰 없거나 무효해도 통과, 단 `principal`이 없으면 `likedByMe=false` 등
     비로그인 기본값 사용.
   - `None`: 필터를 아예 안 거침.

## 에러 처리

`GlobalExceptionHandler`가 `ApiException`(code, httpStatus, message, details)을
`docs/api-contract.md`의 공통 에러 응답으로 변환한다. Bean Validation 실패
(`MethodArgumentNotValidException`)는 `VALIDATION_ERROR`로, 도메인 검증 실패(`CourseValidator`
등)는 직접 `ApiException`을 던져 같은 핸들러로 모은다.

## 페이지네이션

커서는 `created_at` 내림차순 기준으로 마지막 항목의 `id`를 base64 인코딩한 값이다. `id`는
시간순으로 정렬 가능한 ULID를 사용해 "다음 페이지 = `id < cursor`"가 `created_at` 정렬과
항상 일치하게 한다. `sort=distanceAsc` 등 `id` 순서와 무관한 정렬은 1차 구현 범위에서는
정렬만 적용하고 커서는 단순 offset처럼 마지막 페이지 크기를 기준으로 처리 — 정확한 키셋
페이지네이션은 실사용 데이터가 생긴 뒤 재검토(`docs/collaboration.md` 9번 항목과 동일한
"실제 구현은 나중" 원칙).

## 구현 우선순위 (`docs/collaboration.md` 1차 구현 순서를 그대로 따름)

1. 카카오 로그인 + 사용자 (`auth`, `user`) — **이번 스캐폴드에서 실제 구현**
2. 코스 생성/목록/상세 (`course`) — **이번 스캐폴드에서 실제 구현**
3. 게시글/댓글/좋아요/북마크 (`community`, `bookmark`) — 엔티티/리포지토리만 스캐폴드, 컨트롤러는
   다음 작업에서 구현 (지금 비워두면 빌드는 되지만 호출할 API가 없는 상태)

## 개발용 로그인 엔드포인트 (`docs/api-contract.md` 계약 아님)

`POST /auth/dev-login` (`auth/DevAuthController.java`)는 카카오 앱 키/SDK 연동 전까지 모바일이
실제로 서명된 JWT를 받아 인증이 필요한 API(`POST /courses` 등)를 테스트할 수 있게 하는 임시
엔드포인트다. `provider='DEV'`로 저장해 실제 카카오 사용자(`provider='KAKAO'`)와 절대 섞이지
않는다. 카카오 로그인이 실제로 연동되면 이 컨트롤러와 모바일의 `authApi.devLogin()` 호출부를
함께 제거해야 한다 (`mobile/docs/implementations/entry-screen-auth-gating.md` 참고).

## 보행자 경로 캐싱 (`routing` 패키지)

T-Map 보행자 경로 탐색 API는 무료 한도가 1,000회/일이고, 모바일이 직접 호출하면 앱 키가
클라이언트 번들에 노출된다. 그래서 `routing` 패키지(`TmapPedestrianClient`,
`RoutingService`, `RoutingController`)를 백엔드에 두고 모바일은 `POST /routes/pedestrian`만
호출하게 했다.

캐시는 전용 Redis 컨테이너(`runvas-redis`, 포트 6380 — 다른 프로젝트의 Redis와 분리,
`docker run -d --name runvas-redis -p 6380:6379 redis:7 redis-server --appendonly yes`)에
저장한다. `RoutingService`는 `@Cacheable` 대신 `CacheManager`를 직접 써서 히트/미스를
`log.info`로 명시적으로 남긴다 (`@Cacheable`은 히트 시 메서드 진입 자체를 건너뛰어 내부에서
로그를 찍을 수 없음). 캐시 키는 출발/도착 좌표를 소수 4자리(약 11m 격자)로 반올림한 문자열이다
— 처음엔 5자리(약 1.1m)였는데, 실사용 탭 좌표가 "거의 같은 지점"이어도 2~3m씩 차이가 나서
캐시 미스가 잦았다(실측 로그로 확인). TTL은 30일(보행자 도로가 자주 바뀌지 않으므로)이고,
`--appendonly yes`로 AOF를 켜서 백엔드/컨테이너
재시작에도 캐시가 남는다. T-Map 호출이 실패하면(한도 초과 등) 직선 2점으로 폴백한다.

## 다음 작업

- `community`, `bookmark` 패키지의 서비스/컨트롤러 구현
- bounds 겹침 검색 쿼리(`GET /courses`)에 공간 인덱스 또는 단순 사각형 비교 적용
- 좋아요/북마크 동시성(중복 요청 멱등 처리) 테스트
- 실제 카카오 앱 키로 `KakaoOAuthClient` 검증 후 `DevAuthController` 제거
- `accessToken`을 모바일에서 `expo-secure-store`로 영속화 (현재는 메모리에만 보관)
