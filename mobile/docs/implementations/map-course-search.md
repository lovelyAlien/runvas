# 지도 범위 기반 코스 조회

## 배경

`docs/product-scope.md` MVP 범위의 "지도 범위 기반 코스 탐색"을 Map 화면에 실제로 붙이는 작업.
`GET /courses`가 이미 bounds 쿼리를 지원해서 백엔드/`docs/` 변경 없이 모바일만으로 구현했다.

## 변경 내용

- `KakaoMapView`에 `getBounds()`(현재 지도 뷰포트를 Kakao Maps SDK `map.getBounds()`로 조회해
  Promise로 반환)와 `showCourse(path, bounds)`(선택한 공개 코스를 주황 점선 폴리라인으로 지도에
  그리고 카메라를 맞춤)를 추가했다. 새 WebView 메시지 타입은 `GET_BOUNDS`/`BOUNDS_RESULT`,
  `SHOW_COURSE`.
- `courseApi.getCourses(bounds, accessToken?)`를 추가해 `GET /courses`를 bounds 쿼리로 호출한다.
- `CourseSearchSheet` 바텀시트 컴포넌트를 추가했다 (`SavedRoutesScreen`의 목록 행 스타일 재사용).
- `MapScreen` 우측 상단에 "코스 조회" 원형 버튼을 추가하고, 버튼 → bounds 조회 → 목록 표시 →
  선택 시 상세 조회 → 지도 표시 흐름을 연결했다. 조회 중 연속 탭으로 `getBounds()`가 겹쳐
  호출되는 걸 막기 위해 버튼은 조회 중(`isLoadingCourses`) 비활성화된다.
- 사용자가 직접 그리는 경로(`waypoints`/`routeCoords`)와 조회한 코스 미리보기는 완전히 분리된
  상태다 — 미리보기는 새로 선택할 때만 교체되고, 별도의 "미리보기 닫기" UI는 만들지 않았다 (YAGNI).

## 구현 중 발견해 함께 고친 문제

수동 검증 중, Map 화면에서 저장한 코스가 항상 `PRIVATE`로 저장된다는 걸 발견했다
(`buildCreateCourseRequest`가 `visibility`를 못 받아 기본값만 쓰던 상태). `GET /courses`는
공개 코스만 반환하므로, 이 상태로는 코스 조회 기능이 찾을 수 있는 코스를 앱에서 만들 방법이
없었다. `visibility`는 `docs/api-contract.md`에 이미 정의된 필드라 문서 변경 없이, 저장 모달에
"공개"/"비공개" 토글만 추가해서 해결했다. 기본값은 기존 동작과 호환되도록 `PRIVATE`로 유지.

## 검증

- `npx tsc --noEmit` 통과.
- `npx expo start` 번들 200 확인.
- 실기기에서 직접 확인:
  - 우측 상단 원형 "코스 조회" 버튼이 다른 FAB과 동일한 크기로 표시됨
  - 버튼 클릭 → 바텀시트에 현재 지도 범위의 공개 코스 목록 표시
  - 공개 코스가 없는 위치에서는 빈 상태 문구 표시
  - 저장 모달에서 "공개"로 저장한 코스가 조회 목록에 뜨고, 선택 시 지도에 주황 점선으로 표시되며
    카메라가 해당 코스 영역으로 이동
  - 사용자가 그리던 경로(파란 실선)가 있어도 미리보기 표시 후에도 사라지지 않음
  - 다른 공개 코스를 다시 선택하면 이전 미리보기가 사라지고 새 코스만 표시됨
  - 비로그인 상태에서도 버튼과 목록 조회 동작함
