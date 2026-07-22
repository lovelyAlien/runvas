# 운영자 관리자 대시보드

## 배경

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인할 수 있는 내부 전용 도구가 없었다.
`docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`에서 합의한 대로 읽기 전용
관리자 대시보드를 `backend/`에 단독 구현했다 (모바일 변경 없음).

## 설계 결정

- 기존 `/api/**` JWT 인증 체인(`com.runvas.global.security.SecurityConfig`, `@Order(2)`)과
  별개로 `/admin/**` 전용 세션 폼 로그인 체인(`com.runvas.backend.admin.AdminSecurityConfig`,
  `@Order(1)`)을 추가했다.
- 운영자 계정은 Runvas 사용자 계정과 완전히 분리된 `admin_accounts` 테이블로 관리하고, 최초
  계정은 `ADMIN_SEED_USERNAME`/`ADMIN_SEED_PASSWORD` 환경변수로 앱 시작 시 자동 생성한다
  (`AdminAccountSeeder`). 관리자 계정 셀프 서비스 생성 UI는 없다.
- 통계 추이(최근 30일)는 `UserRepository`/`CourseRepository`/`PostRepository`에 추가한
  `countDailySince(Instant)` 쿼리(JPQL `cast(... as date)` + `group by`) 결과를
  `AdminStatsService`가 빈 날짜를 0으로 채워 30개짜리 배열로 만든다.
- 화면은 Thymeleaf 서버사이드 렌더링이고, 추이 차트는 별도 프론트엔드 빌드 없이 Chart.js를
  CDN으로 불러와 그린다.
- 관리 쓰기 액션(삭제/정지)과 API 트래픽 계측(Actuator/Micrometer)은 이번 범위에서 제외했다.

## 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `db/migration/V14__create_admin_accounts.sql` | 신규 — `admin_accounts` 테이블 |
| `backend/admin/AdminAccount.java`, `AdminAccountRepository.java` | 신규 — 운영자 계정 엔티티/리포지토리 |
| `backend/admin/AdminAccountSeeder.java` | 신규 — 환경변수 기반 최초 계정 시드 |
| `backend/admin/AdminUserDetailsService.java`, `AdminSecurityConfig.java` | 신규 — `/admin/**` 세션 폼 로그인 |
| `global/security/SecurityConfig.java` | `@Order(2)` 추가 (동작 변경 없음 — `adminSecurityFilterChain`이 `@Order(1)`+`/admin/**` 매처로 먼저 가로채고, 매처 없는 이 체인이 나머지를 그대로 받는다) |
| `backend/admin/DailyCountProjection.java`, `AdminSummary.java`, `DailyTrendPoint.java`, `AdminStatsService.java` | 신규 — 통계 집계 |
| `user/repository/UserRepository.java`, `backend/course/CourseRepository.java`, `backend/community/PostRepository.java` | 관리자 조회용 검색/집계 쿼리 추가 |
| `backend/admin/AdminLoginController.java`, `AdminDashboardController.java`, `AdminUserController.java`, `AdminCourseController.java`, `AdminPostController.java` | 신규 — 화면 컨트롤러 |
| `backend/admin/AdminUserQueryService.java`, `AdminCourseQueryService.java`, `AdminPostQueryService.java` | 신규 — 목록 검색/페이지네이션 |
| `templates/admin/*.html` | 신규 — 로그인/대시보드/회원/코스/게시글 화면 |
| `application.yml` | `runvas.admin.seed-username`/`seed-password` 추가 |

## 참고

- 관련 스펙: `docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`
