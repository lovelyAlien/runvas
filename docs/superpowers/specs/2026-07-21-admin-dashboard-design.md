# 운영자 전용 관리자 대시보드 설계

작성일: 2026-07-21
관련 문서: `docs/product-scope.md` (운영자 도구 섹션 추가 예정), `docs/admin-dashboard.md` (신규 작성 예정)

## 배경

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인할 수 있는 내부 전용 관리자 페이지가 없다.
`mobile/`이 아닌 `backend/` 단독으로 제공하는 내부 도구이며, 모바일과 공유하는 API 계약이 아니므로
`docs/api-contract.md`/`docs/data-model.md`는 변경하지 않는다.

## 범위

- 운영자 전용 세션 로그인으로 접근하는 읽기 전용 관리자 대시보드
- 회원/코스/게시글 요약 통계 + 최근 30일 일별 추이 라인 차트
- 회원/코스/게시글 목록 조회 (검색 + 페이지네이션)

## 범위 밖

- 회원 정지, 코스/게시글/댓글 삭제 등 관리 쓰기 액션
- API 요청 수/에러율/응답시간 등 트래픽 계측 (Actuator/Micrometer 도입은 별도 작업)
- 관리자 계정 셀프 서비스 생성/변경 UI (환경변수 시드로만 최초 계정 생성)
- 모바일 앱 변경

## 인증 설계

### AdminAccount 엔티티

새 패키지 `com.runvas.backend.admin`에 아래 엔티티를 추가한다 (Course/Post와 동일하게 UUID 문자열
PK, Lombok `@Getter`/`@Setter` 패턴).

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | `String` (UUID, `GenerationType.UUID`) | |
| `username` | `String`, unique, not null | 로그인 ID |
| `passwordHash` | `String`, not null | BCrypt 해시 |
| `createdAt` | `Instant` | |
| `lastLoginAt` | `Instant`, nullable | 로그인 성공 시 갱신 |

Flyway 마이그레이션 `V14__create_admin_accounts.sql`로 `admin_accounts` 테이블을 생성한다
(`username`에 unique index).

### 초기 계정 부트스트랩

`AdminAccountSeeder`(`ApplicationRunner`, `admin` 패키지)를 추가한다.

- 앱 시작 시 `admin_accounts` 테이블이 비어 있고, 환경변수 `ADMIN_SEED_USERNAME`/
  `ADMIN_SEED_PASSWORD`가 둘 다 설정되어 있으면 `BCryptPasswordEncoder`로 해시해 계정 1건을 생성한다.
- 둘 중 하나라도 없으면 아무 것도 하지 않는다 (프로덕션에서 시드가 없으면 로그인 불가능한 상태 —
  운영자가 직접 DB에 계정을 넣거나 다음 배포에 환경변수를 채워 넣는다).
- 테이블에 이미 계정이 있으면 스킵한다 (매 재기동마다 재생성하지 않음).

### Spring Security 필터 체인 분리

기존 `SecurityConfig`(`com.runvas.global.security`)는 `/api/**` 요청 전체를 대상으로 하는 단일
`SecurityFilterChain` 빈이다. 이를 그대로 두고, `/admin/**` 전용 체인을 별도 빈으로 추가하며
`@Order`로 우선순위를 명시한다 (Spring Security는 매칭되는 첫 체인을 사용하므로 `/admin/**` 체인이
`/api/**` 체인보다 먼저 평가돼야 한다).

- 기존 `securityFilterChain` 빈: `@Order(2)` + `.securityMatcher("/api/**")` 추가 (동작 변경 없음,
  매칭 범위만 명시).
- 신규 `adminSecurityFilterChain` 빈 (`com.runvas.backend.admin.AdminSecurityConfig`): `@Order(1)`,
  `.securityMatcher("/admin/**")`, 세션 기반 폼 로그인.
  - `formLogin`: 로그인 페이지 `/admin/login`, 로그인 처리 URL은 Spring Security 기본값 사용,
    성공 시 `/admin/dashboard`로 리다이렉트.
  - `logout`: `/admin/logout` 허용.
  - `sessionManagement`: 기본 정책(세션 사용, `STATELESS` 아님) — 폼 로그인이므로 JSESSIONID 쿠키 사용.
  - `authorizeHttpRequests`: `/admin/login` 정적 리소스는 `permitAll`, 나머지 `/admin/**`는
    `authenticated()`.
  - `csrf`: 기본 활성화 유지 (폼 기반이므로 JWT 체인과 달리 CSRF를 끄지 않는다).
- `AdminUserDetailsService`(`UserDetailsService` 구현체)가 `AdminAccountRepository.findByUsername`으로
  조회해 Spring Security 인증에 연결한다. 로그인 성공 시 `AuthenticationSuccessHandler`에서
  `AdminAccount.lastLoginAt`을 갱신한다.

## 화면 구성

Thymeleaf 서버사이드 렌더링. 템플릿 위치: `backend/src/main/resources/templates/admin/`.
공통 레이아웃 fragment(`admin/layout.html`)에 상단 네비게이션(대시보드/회원/코스/게시글/로그아웃)을
둔다.

| 경로 | 설명 |
| --- | --- |
| `GET /admin/login` | 로그인 폼 |
| `GET /admin/dashboard` | 요약 통계 카드 + 최근 30일 추이 차트 |
| `GET /admin/users` | 회원 목록 (검색 + 페이지네이션) |
| `GET /admin/courses` | 코스 목록 (검색 + 공개범위 필터 + 페이지네이션) |
| `GET /admin/posts` | 게시글 목록 (검색 + 페이지네이션) |

### 대시보드 카드

- 전체 회원 수, 전체 코스 수(공개/비공개 각각), 전체 게시글 수, 전체 댓글 수(`Comment` +
  `CourseComment` 합산)

### 대시보드 추이 차트

- 최근 30일(오늘 포함) 일별: 신규 가입자 수, 신규 코스 수, 신규 게시글 수 — 3개 라인을 하나의
  Chart.js 라인 차트에 표시.
- Chart.js는 CDN `<script>` 태그로 템플릿에 직접 포함한다 (별도 프론트엔드 빌드 파이프라인 없음).
- 컨트롤러가 최근 30일 각 날짜의 카운트를 0으로 채운 배열로 미리 계산해 모델에 담고, 템플릿은 이
  배열을 JSON으로 인라인 렌더링해 Chart.js에 전달한다 (당일 데이터가 없는 날짜도 0으로 표시).

### 목록 페이지 공통 규칙

- 페이지네이션: Spring Data `Pageable`, 기본 페이지 크기 20.
- 검색: 회원(닉네임/이메일 부분 일치), 코스(제목 부분 일치), 게시글(제목 부분 일치) — 모두
  `LIKE '%q%'` 쿼리 메서드.
- 코스 목록의 공개범위 필터: 전체/`PUBLIC`/`PRIVATE` 드롭다운.
- 회원 목록에는 `deletedAt` 존재 여부로 "탈퇴" 배지를 표시한다 (하드 삭제 전 유예기간 사용자만 —
  하드 삭제된 사용자는 `users` 테이블에서 이미 제거된 상태이므로 목록에 나타나지 않는다).

## 데이터 조회 설계

새 리포지토리 메서드는 모두 읽기 전용이며 기존 리포지토리에 추가한다.

**`UserRepository`**
```java
long count();
Page<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(String nickname, String email, Pageable pageable);
@Query("select cast(u.createdAt as date) as day, count(u) as cnt from User u where u.createdAt >= :since group by cast(u.createdAt as date)")
List<DailyCountProjection> countDailySince(Instant since);
```

**`CourseRepository`**
```java
long countByVisibility(CourseVisibility visibility);
Page<Course> findByTitleContainingIgnoreCaseAndVisibility(String title, CourseVisibility visibility, Pageable pageable);
Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable); // 필터 없음(전체)
@Query("select cast(c.createdAt as date) as day, count(c) as cnt from Course c where c.createdAt >= :since group by cast(c.createdAt as date)")
List<DailyCountProjection> countDailySince(Instant since);
```

**`PostRepository`**
```java
long count();
Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);
@Query("select cast(p.createdAt as date) as day, count(p) as cnt from Post p where p.createdAt >= :since group by cast(p.createdAt as date)")
List<DailyCountProjection> countDailySince(Instant since);
```

**`CommentRepository`/`CourseCommentRepository`**: `long count()` 각각 추가해 합산.

`DailyCountProjection`은 `admin` 패키지에 두는 공용 인터페이스 프로젝션
(`java.time.LocalDate getDay(); long getCnt();`)으로, User/Course/Post 세 곳에서 재사용한다.

`AdminStatsService`가 위 리포지토리들을 조합해 카드 수치와 30일 배열(빈 날짜는 0 채움)을 만든다.
`AdminUserQueryService`/`AdminCourseQueryService`/`AdminPostQueryService`는 목록 페이지의 검색+
페이지네이션 위임만 담당한다 (Course 모듈의 서비스 계층 분리 패턴과 동일).

## Controller 구성

`admin` 패키지 하위:

- `AdminDashboardController` — `GET /admin/dashboard`
- `AdminUserController` — `GET /admin/users`
- `AdminCourseController` — `GET /admin/courses`
- `AdminPostController` — `GET /admin/posts`
- `AdminLoginController` — `GET /admin/login` (로그인 폼 렌더링만, 실제 인증 처리는 Spring
  Security `formLogin`이 담당)

모두 `Model`에 뷰 데이터를 담아 Thymeleaf 템플릿 이름을 반환하는 일반적인 Spring MVC 컨트롤러다
(JSON을 반환하는 기존 `@RestController`들과 다른 패턴이므로 신규 코드에서 명확히 구분한다).

## 의존성 변경

`backend/build.gradle`에 추가:
```
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
```

## 문서 변경

- `docs/product-scope.md`에 "운영자 도구" 섹션을 짧게 추가한다: 내부 전용 관리자 대시보드가
  존재하며 사용자 흐름(핵심 사용자 흐름 1~8)과는 무관하고 `backend/` 책임임을 명시한다. MVP
  범위/제외 범위 표에는 사용자 대상 기능이 아니므로 추가하지 않는다.
- 신규 `docs/admin-dashboard.md`: 접근 방법(세션 로그인, 계정 부트스트랩 방식), 화면 목록,
  통계 항목, 명시적 제외 범위(관리 액션 없음, 트래픽 계측 없음)를 기록한다.
- `docs/api-contract.md`/`docs/data-model.md`/`docs/geo-conventions.md`/`docs/gpx-export.md`는
  변경하지 않는다 (모바일과 공유하는 계약이 아님).

## 테스트 계획

- `AdminSecurityConfigTest` (MockMvc): 미인증 상태로 `/admin/dashboard` 접근 시 `/admin/login`으로
  리다이렉트, 잘못된 자격증명 로그인 실패, 올바른 자격증명 로그인 성공 후 대시보드 접근 가능.
- `AdminAccountSeederTest`: 환경변수 있을 때 계정 생성, 없을 때 미생성, 이미 계정 있을 때 미생성.
- `AdminStatsServiceTest` (단위): 특정 날짜에 데이터가 없는 경우 0으로 채워지는지, 30일 경계값
  (오늘 포함 30일치)이 정확한지 검증.
- `AdminUserController`/`AdminCourseController`/`AdminPostController` MockMvc 테스트: 검색어/필터
  적용, 페이지네이션 파라미터 반영, 인증 세션 없이 접근 시 리다이렉트.

## 완료 기준

- 운영자가 `/admin/login`으로 로그인해 대시보드에서 회원/코스/게시글/댓글 총계와 최근 30일 추이
  차트를 확인할 수 있다.
- 회원/코스/게시글 목록 페이지에서 검색과 페이지네이션이 동작한다.
- 관리 쓰기 액션, API 트래픽 계측 기능은 존재하지 않는다 (범위 밖 확인).
- `docs/product-scope.md`, `docs/admin-dashboard.md` 변경이 구현과 함께 커밋된다.
- 완료 후 `backend/docs/implementations/`에 구현 기록을 남긴다 (레포 관례).
