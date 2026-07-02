# 지도 범위 기반 코스 조회 (Map 화면) 설계

작성일: 2026-07-02
관련 문서: `docs/api-contract.md` §Course APIs `GET /courses`, `docs/product-scope.md` (지도 범위 기반 코스 탐색), `mobile/CLAUDE.md`

## 배경

`product-scope.md`의 MVP 범위에는 "지도 범위 기반 코스 탐색"이 이미 포함되어 있고,
`GET /courses`는 이미 `swLat`/`swLng`/`neLat`/`neLng` bounds 쿼리로 공개 코스 목록을 반환하도록
문서화·구현되어 있다 (`docs/api-contract.md` §GET /courses).

이 작업은 그 계약을 그대로 사용해 Map 화면에 "코스 조회" 진입점을 추가하는 **모바일 전용** 기능이다.
요청/응답 필드, 인증 방식, 상태 코드 어느 것도 바뀌지 않으므로 `docs/api-contract.md`,
`docs/data-model.md` 변경은 필요 없다.

## 목표

- Map 화면 우측 상단에 원형 "코스 조회" 버튼 추가 (기존 우측 하단 FAB과 동일한 크기·모양)
- 버튼을 누르면 **현재 지도에 보이는 영역**(카카오맵 SDK의 `map.getBounds()`) 기준으로
  `GET /courses`를 호출해 공개 코스 목록을 가져온다
- 결과를 지도 위 바텀시트로 보여준다
- 목록에서 코스를 선택하면 같은 지도 위에 해당 코스 경로를 바로 그려서 보여준다 (화면 전환 없음)

## 범위 밖

- 반경(radius) 기반 검색 — 사용자가 명시적으로 "맵에 보이는 범위"로 확정함
- 검색어(`q`)/태그(`tag`)/정렬(`sort`) 필터 UI — 이번 작업은 bounds 조회만 다룸
- 좋아요, 북마크 액션 — 목록에는 표시하지 않음 (조회 전용)
- 페이지네이션 — MVP 범위에서는 첫 페이지(`GET /courses` 기본 `limit=20`)만 사용, "더 보기" 없음

## 데이터 흐름

1. 사용자가 우측 상단 "코스 조회" 버튼을 누른다.
2. `MapScreen`이 `KakaoMapView`의 신규 명령형 메서드 `getBounds(): Promise<GeoBounds>`를 호출한다.
3. `KakaoMapView`는 WebView에 `{ type: 'GET_BOUNDS' }` 메시지를 보내고, WebView 스크립트는
   `map.getBounds()`로 `southWest`/`northEast`를 계산해 `{ type: 'BOUNDS_RESULT', swLat, swLng, neLat, neLng }`
   를 RN으로 되돌려 보낸다. RN 쪽은 대기 중인 Promise를 이 메시지로 resolve한다.
4. `MapScreen`은 받은 bounds로 신규 `courseApi.getCourses(bounds, accessToken?)`를 호출해
   `CourseSummary[]`를 받는다 (`GET /courses`는 `Optional` 인증이므로 비로그인도 호출 가능).
5. 결과를 `CourseSearchSheet`(신규 바텀시트 컴포넌트)에 전달해 연다.
6. 사용자가 목록에서 코스를 탭하면 바텀시트를 닫고, 기존 `getCourse(courseId, accessToken?)`로
   상세(`path`, `bounds` 포함)를 조회한다.
7. `KakaoMapView`의 신규 메서드 `showCourse(path, bounds)`를 호출해 지도에 미리보기 폴리라인을
   그리고 `fitBounds`로 카메라를 이동한다. 기존에 그려진 미리보기가 있으면 새로 그리기 전에 지운다.

## KakaoMapView 변경

`KakaoMapViewRef` 인터페이스에 메서드 2개 추가:

```ts
getBounds: () => Promise<GeoBounds>;
showCourse: (path: RoutePoint[], bounds: GeoBounds) => void;
```

WebView HTML 스크립트에 메시지 핸들러 추가:

- `GET_BOUNDS` (RN→WebView): `map.getBounds()`를 읽어 `BOUNDS_RESULT`로 응답
- `SHOW_COURSE` (RN→WebView): 기존 `previewPolyline`이 있으면 `setMap(null)` 후 제거, 새 경로로
  주황 점선(`strokeColor: '#F97316', strokeStyle: 'shortdash'`) 폴리라인을 그려 사용자가 직접
  그리는 경로(파란 실선, `routePolyline`/`segmentPolylines`)와 시각적으로 구분한다. 이어서
  기존 `FIT_BOUNDS` 로직과 동일하게 카메라를 이동한다.

`getBounds()`는 RN 쪽에서 메시지 id 없이 단일 pending Promise로 구현한다 (동시에 여러 번 호출되지
않는 단순 요청/응답이므로 큐잉 불필요 — YAGNI).

## courseApi.ts 변경

```ts
export async function getCourses(bounds: GeoBounds, accessToken?: string): Promise<CourseSummary[]>
```

`GET /api/courses?swLat=&swLng=&neLat=&neLng=` 호출, `Authorization` 헤더는 `accessToken`이 있을
때만 포함 (`getCourse`와 동일한 패턴). 응답의 `pageInfo`는 이번 범위에서 사용하지 않는다.

## 신규 컴포넌트: CourseSearchSheet

- `Modal(transparent, animationType="slide")` 기반 바텀시트, 화면 하단에서 지도 위로 올라옴
- Props: `visible`, `courses: CourseSummary[]`, `isLoading`, `onSelectCourse(courseId)`, `onClose`
- 목록 행 스타일은 `SavedRoutesScreen`의 `row`/`rowTitle`/`rowMeta` 패턴 재사용
  (제목 + `formatDistance` + `formatDuration`, 태그가 있으면 함께 표시)
- 빈 목록: "주변에 표시된 코스가 없습니다" 텍스트
- 로딩 중: `ActivityIndicator`

## MapScreen 변경

- 신규 상태: `isCourseSheetOpen`, `nearbyCourses: CourseSummary[]`, `isLoadingCourses`
- 우측 상단에 새 원형 버튼 (`position: absolute, right: 16, top: 16`), 기존 `FAB` 컴포넌트 재사용
  (46×46, `borderRadius: 23`), 아이콘은 `search-outline`
- `handleOpenCourseSearch`: `getBounds()` → `getCourses()` → 시트 오픈. 실패 시 기존 패턴대로 `Alert.alert`
- `handleSelectCourse(courseId)`: 시트 닫기 → `getCourse()` → `mapRef.current?.showCourse(...)`.
  실패 시 `Alert.alert`

## 사용자가 그리던 경로와의 관계

미리보기로 표시된 코스는 사용자가 그리는 중인 경로(`waypoints`/`routeCoords`, undo/clear 대상)와
완전히 분리된 상태다. 기존 "초기화"(trash) 버튼은 미리보기 폴리라인에 영향을 주지 않는다 — 새 코스를
선택하면 이전 미리보기만 자동으로 교체된다. 별도의 "미리보기 닫기" UI는 이번 범위에 포함하지 않는다
(YAGNI, 필요성이 확인되면 후속 작업으로 추가).

## 에러 처리

- bounds 조회 실패(WebView 응답 없음), `GET /courses` 실패, `GET /courses/{courseId}` 실패는
  모두 기존 패턴과 동일하게 `Alert.alert`로 사용자에게 알린다.

## 검증 계획

- `npx tsc --noEmit`
- `npx expo start` 백그라운드 기동 후 bundle 200 확인 (기존 `CLAUDE.md` 검증 규칙)
- 실기기/시뮬레이터에서: 코스 조회 버튼 → 바텀시트 표시 → 코스 선택 → 지도에 주황 점선으로 표시되는지 확인
- 완료 후 `mobile/docs/implementations/map-course-search.md`에 설계 요약 기록
