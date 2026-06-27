# 2026-06-28 작업 기록 — 저장한 코스 상세 보기 + waypoints/path 분리

## 배경

이전 세션에서 "저장한 코스" 목록과 백엔드 연동까지는 끝났지만, 목록 항목을 눌러도 아무 일도
일어나지 않았다. 코스를 탭하면 실제 경로(지도)와 상세 정보를 볼 수 있게 만들고, 그 과정에서
"좌표 2개만 찍었는데 저장된 포인트가 56개로 나온다"는 혼란도 같이 해결했다.

## 1. 테스트 계정 단일화

로그인 모달에 "고정 계정"/"매번 신규 가입" 버튼이 둘 있어서, 테스트할 때마다 다른 사용자로
코스가 쪼개져 저장됐다. `AuthContext.mockLogin()`이 항상 고정 닉네임(`demo_user`)으로만
로그인하도록 단순화하고, 모달 버튼도 하나로 줄였다(`mobile/src/contexts/AuthContext.tsx`,
`mobile/src/components/LoginPromptModal.tsx`).

## 2. 백엔드: 내가 만든 코스 목록 (`GET /courses/mine`)

기존 `GET /courses`는 `visibility=PUBLIC` + bounds 필수라서, 앱이 기본값으로 저장하는
`PRIVATE` 코스가 전혀 안 보였다. `CourseRepository.findByAuthorIdOrderByCreatedAtDesc` +
`CourseService.listMine()` + `CourseController`의 `GET /courses/mine`을 추가해 본인 코스를
visibility 무관하게 전부 조회하게 했다. `SecurityConfig`에 인증 필요 규칙 추가,
`docs/api-contract.md`에 계약 문서화.

## 3. 지연 로딩(LazyInitializationException) 버그 3건

`tags`가 `@ElementCollection`(지연 로딩)인데, `CourseService`의 조회 메서드들이
`@Transactional`이 아니어서 Jackson이 HTTP 응답을 직렬화하는 시점엔 이미 트랜잭션/세션이
끝나 있었다. `listMine()`을 구현하며 처음 발견했고, 같은 패턴을 쓰는 `getById()`/`list()`도
동일하게 깨져 있어서 같이 고쳤다:
- `CourseService`의 `getById()`/`list()`/`listMine()`에 `@Transactional(readOnly = true)` 추가.
- `CourseResponse`/`CourseSummaryResponse`의 `from()`에서 `Set.copyOf(course.getTags())`로
  트랜잭션 안에서 즉시 복사.

## 4. 저장한 코스 상세 보기 화면

- `KakaoMapView`에 읽기 전용 표시용 메시지 추가: `MAP_READY`(지도 로드 완료 신호 — RN이 이걸
  받기 전에 `addRouteSegment`를 보내면 `map` 변수가 없어 에러 가능), `FIT_BOUNDS`(카메라를
  코스 전체가 보이게 맞춤).
- 네비게이션을 `Tab.Navigator` 단일 구조에서 `Stack.Navigator(Tabs, CourseDetail)`로 확장
  (`@react-navigation/native-stack`, 이미 설치돼 있던 패키지). `RootStackParamList.Tabs`는
  `NavigatorScreenParams<RootTabParamList>`로 선언해야 중첩 네비게이션이 타입 에러 없이 동작.
- `courseApi.getCourse()` 추가, `CourseDetailScreen` 신설(뒤로가기 헤더 + 읽기 전용 지도 +
  `RouteStatsBar`, GPX 내보내기는 기존 `exportGpx` 재사용).
- `SavedRoutesScreen`의 행을 탭 가능하게 변경(삭제 버튼은 별도 터치 영역).

## 5. path/waypoints 분리

상세 화면을 만들다 보니 "지도에서 2지점만 찍었는데 저장된 코스 포인트가 56개"라는 문제가
드러났다. 원인: `path`는 보행 경로 탐색 API 응답의 상세 좌표(폴리라인용)고, 실제 탭한 지점
수는 `useRoute.ts`의 별도 상태(`waypoints`)였는데 저장 시점엔 `path`만 서버로 보내서 탭
지점 정보가 사라졌다. `path`만으로는 추후 "코스 수정"(지점 추가/삭제/이동 후 경로 재탐색)
기능도 만들 수 없다.

- `Course.waypoints` 필드 추가 — 처음부터 `columnDefinition = "LONGTEXT"`로 선언했다
  (`path`가 `tinytext`로 잘렸던 이전 세션의 전례를 반복하지 않기 위해).
- `CourseValidator`의 개수/sequence 연속성 검사를 필드명 파라미터를 받게 리팩터링해서
  `path`/`waypoints` 양쪽에 재사용.
- `CreateCourseRequest`/`UpdateCourseRequest`/`CourseResponse`에 `waypoints` 추가,
  `docs/data-model.md`/`docs/api-contract.md` 갱신.
- 모바일 `useRoute.toWaypointPoints()` 추가, `MapScreen` 저장 시 같이 전송,
  `CourseDetailScreen`은 "포인트 개수"를 `waypoints.length`로 표시 + 탭했던 지점에 번호
  마커도 그려서 그릴 때 모습을 재현.

### 배포 중 사고: 기존 행 마이그레이션

`waypoints` 컬럼을 `ddl-auto: update`로 추가하니, 기존 행은 기본값 없는 `NOT NULL LONGTEXT`
컬럼에 빈 문자열이 채워졌다. 이후 `GET /courses/mine`이 그 행을 읽으면서 JSON 역직렬화가
`MismatchedInputException("No content to map due to end-of-input")`로 500을 냈다. 운영
DB가 아니라 로컬 테스트 데이터라 `UPDATE courses SET waypoints = '[]' WHERE waypoints = ''`로
직접 백필해서 해결했다 — 실제 사용자 데이터가 쌓인 뒤 컬럼을 추가할 때는 마이그레이션 스크립트로
기본값을 명시해야 한다는 교훈.

## 검증

- `./gradlew compileJava`, `npx tsc --noEmit` 매 단계 통과.
- curl로 `path` 3개 + `waypoints` 2개 저장 → 응답/재조회 모두 정확히 분리되어 돌아옴 확인.
- `GET /courses/mine` 인증 없이 401, 로그인 시 본인 코스(PRIVATE 포함) 전부 조회 확인.
- 기존 행 백필 후 `GET /courses/mine` 200 정상화 확인.
