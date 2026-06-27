# 저장한 코스 상세 보기 화면

## 배경

"저장한 코스" 목록에서 항목을 눌러도 아무 반응이 없었다. 코스를 탭하면 실제 경로(지도)와
거리/시간을 볼 수 있게 만든다.

## 변경 내용

- `KakaoMapView`에 읽기 전용 표시용 메시지 두 개 추가:
  - `MAP_READY`: `kakao.maps.load()` 완료 시 WebView → RN으로 알림. RN이 지도 로드 전에
    `addRouteSegment`/`fitBounds`를 보내면 `map` 변수가 아직 없어 JS 에러가 날 수 있어서,
    이 신호를 받은 뒤에만 그리도록 했다(`Props.onMapReady`).
  - `FIT_BOUNDS`: `kakao.maps.LatLngBounds` + `map.setBounds(...)`로 카메라를 코스 전체가
    보이게 맞춤(`KakaoMapViewRef.fitBounds`).
- 네비게이션 구조를 `Tab.Navigator` 단일 구조에서 `Stack.Navigator(Tabs, CourseDetail)`로
  바꿨다(`@react-navigation/native-stack`, 이미 설치돼 있던 패키지). `RootStackParamList.Tabs`는
  `NavigatorScreenParams<RootTabParamList>`로 선언해 중첩 네비게이션(`navigate('Tabs', { screen:
  'Board' })`)이 타입 에러 없이 동작하게 했다.
- `courseApi.getCourse(courseId, accessToken?)` 추가 — `GET /courses/{courseId}`(Auth Optional,
  PRIVATE 코스는 백엔드가 로그인+본인 확인을 이미 처리).
- `CourseDetailScreen` 신설: 뒤로가기 헤더 + 읽기 전용 지도 + `RouteStatsBar`(GPX 내보내기는
  기존 `exportGpx` 그대로 재사용, 거의 공짜로 됨).
- `SavedRoutesScreen`의 각 행을 `TouchableOpacity`로 감싸 탭 시 `CourseDetail`으로 이동. 삭제
  아이콘은 형제 `TouchableOpacity`라 이벤트가 분리돼 충돌하지 않는다.

## 검증

- `npx tsc --noEmit` 통과 (처음엔 `RootStackParamList.Tabs: undefined`로 선언해서 중첩 네비게이션
  타입 에러가 났고, `NavigatorScreenParams`로 고침).
- `./gradlew compileJava` 통과(백엔드 변경 없음, 기존 `GET /courses/{courseId}` 그대로 사용).
- Expo 번들(`/index.bundle?platform=ios&dev=true`) 200 확인.

## 추가: path/waypoints 분리 저장 (2026-06-27)

지도에서 2지점만 탭해서 저장했는데 실제 저장된 코스의 포인트 수가 56개로 나와 혼란이 있다는
피드백을 받았다. 원인: `path`는 보행 경로 탐색 API 응답의 상세 좌표(폴리라인용)이고, 사용자가
실제 탭한 지점 수는 `useRoute.ts`의 `waypoints`(별도 상태)였는데, 저장 시점엔 `path`만 서버로
보내서 "탭한 지점이 몇 개였는지"가 사라졌다.

`waypoints`를 `path`와 별도 필드로 추가했다(백엔드 `Course.waypoints`, 처음부터
`columnDefinition = "LONGTEXT"`로 선언 — `path`가 `tinytext`로 잘렸던 전례를 반복하지 않음).
`CourseValidator`의 개수/sequence 연속성 검사를 `path`/`waypoints` 양쪽에 재사용 가능하게
필드명 파라미터를 받도록 리팩터링했다. `useRoute.ts`에 `toWaypointPoints()`를 추가해
`toRoutePoints()`와 같은 패턴으로 변환한다.

`CourseDetailScreen`의 "포인트 개수" 표시를 `course.path.length` → `course.waypoints.length`로
바꿔서, 그릴 때 봤던 "2개"와 동일하게 보이게 했다. 지도에는 `path`로 곡선을 그리고, 그 위에
`waypoints` 각 지점에 번호 마커(`addWaypoint`)도 추가해서 그릴 때 모습을 그대로 재현한다.

### 검증

- curl로 `path` 3개 + `waypoints` 2개로 저장 → 응답과 `GET /courses/{id}` 재조회 모두 정확히
  3개/2개로 분리돼 돌아옴 확인.
- `npx tsc --noEmit`, `./gradlew compileJava` 통과.
