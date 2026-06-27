# 테스트 계정 단일화 + 백엔드 저장 코스 보기

## 배경

테스트 로그인 버튼이 "고정 계정"/"매번 신규 가입" 두 개라 테스트할 때마다 다른 사용자로 코스가
쪼개져 저장됐고, "저장한 코스" 화면은 기기 로컬 저장(`LocalSavedRoute`)만 보여줘서 백엔드에
실제로 저장됐는지 확인할 길이 없었다.

## 변경 내용

- `AuthContext.mockLogin()`이 더 이상 `forceNewUser` 파라미터를 받지 않고 항상 고정 닉네임으로
  로그인한다. `LoginPromptModal`의 버튼도 하나로 줄였다.
- 백엔드에 `GET /courses/mine`을 추가했다 — 기존 `GET /courses`는 `visibility=PUBLIC` + bounds
  필수라서, 앱이 기본값으로 저장하는 `PRIVATE` 코스가 전혀 안 보이는 문제가 있었다.
- `SavedRoutesScreen`을 로컬 저장(`localRoutesStorage.ts`) 대신 `courseApi.getMyCourses`/
  `deleteCourse`로 백엔드 연동했다. `MapScreen`의 저장도 로컬 저장 없이 `postCourse` 하나만
  호출한다(저장 버튼은 이미 `requireAuth()`로 비로그인을 막고 있어서, 로컬 저장이 "비로그인 폴백"
  역할을 한 적이 없었다 — 그냥 중복이었다). `localRoutesStorage.ts`/`LocalSavedRoute`는 삭제.

## 디버깅 중 발견한 백엔드 버그

`GET /courses/mine` 구현 중 `tags`(지연 로딩 컬렉션) 접근 시 500이 났다. `CourseService`의
조회 메서드가 `@Transactional`이 아니어서, JPA 리포지토리 호출이 끝나는 순간 트랜잭션도 같이
끝나버리고, 그 뒤 Jackson이 HTTP 응답을 직렬화하는 시점엔 이미 세션이 닫혀 있어
`LazyInitializationException`이 났다. `CourseService.listMine()`에 `@Transactional(readOnly = true)`를
붙이고, `CourseSummaryResponse`/`CourseResponse`의 `from()`에서 `Set.copyOf(course.getTags())`로
트랜잭션 안에서 즉시 복사하도록 고쳤다. (`GET /courses`/`GET /courses/{courseId}`도 같은 패턴을
쓰고 있어서 동일하게 깨져 있었을 가능성이 높다 — 이번에 같이 고쳤다.)

## 검증

- `npx tsc --noEmit` 통과.
- curl로 고정 테스트 계정 로그인 → `POST /courses`(PRIVATE) 2개 저장 → `GET /courses/mine`에서
  둘 다(이전 세션에 저장했던 것 포함 총 4~5개) 조회됨 확인.
- `DELETE /courses/{id}` → 204, 이후 목록에서 빠짐 확인.
- 인증 없이 `GET /courses/mine` 호출 → 401 확인.
