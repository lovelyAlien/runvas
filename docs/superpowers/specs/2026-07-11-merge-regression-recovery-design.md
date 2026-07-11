# 병합 회귀로 소실된 기능 복구 설계

작성일: 2026-07-11
관련 문서: `docs/superpowers/specs/2026-07-02-map-course-search-design.md`,
`docs/superpowers/specs/2026-07-07-course-detail-review-buttons-design.md`,
`mobile/docs/implementations/map-course-search.md`,
`mobile/docs/implementations/map-nearby-courses-button-distinction.md`,
`mobile/docs/implementations/course-detail-review-buttons.md`

## 배경

병합 커밋 `5e9c849`(`Merge branch 'refs/heads/codex/mobile-map'`, 2026-07-08)가 PR 리뷰 없이
main에 직접 push되면서, 22개 충돌 파일 중 `mobile/src/screens/MapScreen.tsx`와
`mobile/src/screens/CourseDetailScreen.tsx`에서 main 쪽 코드가 충돌 해소 과정에서 완전히
버려지고 `codex/mobile-map` 쪽 구버전으로 조용히 대체되었다. 그 결과 다음 두 기능이 화면에서
사라졌다.

1. **Map 화면 "주변 코스 찾기"** — bounds 기반 공개 코스 목록을 바텀시트로 보여주고, 선택한
   코스를 지도에 미리보기로 그리는 기능 (`2026-07-02-map-course-search-design.md` +
   `map-nearby-courses-button-distinction.md`에서 완성된 최종 버전).
2. **코스별 후기 작성/목록 버튼** — Map 화면의 코스 탐색 시트와 `CourseDetailScreen` 양쪽에
   있던, 특정 코스에 바로 후기를 남기거나 후기 목록을 보는 진입점
   (`2026-07-07-course-detail-review-buttons-design.md`).

두 기능 모두 병합 전 마지막 main 커밋(`498c410`)에 완성된 형태로 남아있고, API 계약/데이터
모델은 전혀 바뀌지 않았으므로(기존 엔드포인트만 재사용) 이번 작업은 **모바일 전용 복구**다.
`docs/api-contract.md`, `docs/data-model.md` 변경은 없다.

이번 조사에서 함께 발견한 `isBrowseMode`(codex 쪽이 독자적으로 만든, 지도 마커 기반 공개 코스
탐색 기능의 토글 버튼이 사라진 문제)는 원인과 설계 판단이 다른 별개 회귀이므로 범위에서
제외한다.

## 목표

- 병합 이전 main(`498c410`)에 있던 두 기능을, 그 사이 `codex/mobile-map` 쪽에서 새로 추가된
  기능(태그/이름 검색, 보행로 토글, `isBrowseMode`)을 건드리지 않고 현재 코드베이스 위에
  재통합한다.
- 새 API나 새 데이터 모델을 추가하지 않는다 — 기존 `GET /courses`(공개 코스 목록)와 기존
  `PostCreate`/`CourseBoard` 라우트만 재사용한다.

## 범위 밖

- `isBrowseMode` 토글 버튼 복구 (별개 이슈로 후속 처리)
- `KakaoMapView`의 마커 기반 공개 코스 탐색(`showPublicCourses`/`setBrowseMode`) 리팩터링
- `CourseSearchSheet`/`CourseDetailScreen`의 FAB 스타일을 공용 컴포넌트로 추출 (기존 설계
  문서에서도 YAGNI로 보류한 항목 — 사용처가 하나 더 늘었다고 이번에 바꾸지 않는다)

## 변경 1: `KakaoMapView.tsx` — ref 메서드 4개 추가

현재 `KakaoMapViewRef`에는 `moveToLocation`/`addWaypoint`/`addRouteSegment`/`fitBounds`/
`undoLast`/`clearMap`/`showPublicCourses`/`clearPublicCourses`/`setBrowseMode`가 있다. 여기에
아래 4개를 **추가**한다 (기존 메서드/메시지 타입과 이름 충돌 없음 — `MAP_BOUNDS_CHANGE`는
`idle` 이벤트로 계속 스트리밍되는 채널이고, `GET_BOUNDS`는 버튼 클릭 시 1회성 요청/응답이라
서로 다른 메시지 타입으로 공존한다):

```ts
getBounds: () => Promise<GeoBounds>; // 현재 지도에 보이는 영역 1회 조회 ("주변 코스 찾기" 버튼용)
previewCourse: (path: RoutePoint[]) => void; // 선택한 공개 코스 경로를 미리보기로 표시 (카메라 이동 없음)
showCourseWaypoints: (waypoints: RoutePoint[]) => void; // "상세 보기" 시 경로 순서 번호 핀 표시
clearCoursePreview: () => void; // 미리보기 경로/순서 핀 제거
```

WebView HTML 스크립트에 추가할 것 (`498c410`의 구현을 그대로 이식):

- 전역 변수 `previewPolyline`, `courseWaypointMarkers = []` 추가
- 메시지 핸들러 4개 추가: `GET_BOUNDS`(→ `BOUNDS_RESULT` 응답), `PREVIEW_COURSE`(주황 점선
  `strokeColor: '#F97316'`, `strokeStyle: 'solid'`, 폭 3 — 사용자가 그리는 파란 실선
  `routePolyline`/`segmentPolylines`와 시각적으로 구분), `SHOW_COURSE_WAYPOINTS`(색상 규칙은
  기존 `ADD_WAYPOINT`의 start/mid/end 패턴 재사용), `CLEAR_COURSE_PREVIEW`
- RN 쪽: `boundsResolverRef`(단일 pending Promise, 큐잉 불필요 — 동시에 여러 번 호출되지 않음)를
  새 `useRef`로 추가하고, `getBounds()`는 5초 타임아웃 후 reject
- `handleMessage`에 `BOUNDS_RESULT` 케이스 추가해 `boundsResolverRef.current`를 resolve

## 변경 2: `courseApi.ts` — 변경 없음

병합 전 main에는 `getCourses(bounds, accessToken?)`가 별도로 있었지만, 지금은 동일한
`GET /api/courses` 엔드포인트를 호출하는 `getPublicCourses(params, accessToken?)`가 이미
존재하고 `{ courses, nextCursor }`를 반환한다. `getCourses`를 별도로 되살리지 않고
`getPublicCourses({ swLat, swLng, neLat, neLng }, accessToken)`를 호출해 `.courses`만 쓴다 —
동일 기능의 중복 API 클라이언트 함수를 만들지 않는다.

## 변경 3: `MapScreen.tsx`

### 추가 state

```ts
const [isCourseSheetOpen, setIsCourseSheetOpen] = useState(false);
const [nearbyCourses, setNearbyCourses] = useState<CourseSummary[]>([]);
const [isLoadingCourses, setIsLoadingCourses] = useState(false);
const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
const [selectedCourseDetail, setSelectedCourseDetail] = useState<Course | null>(null);
const [isCourseSheetCollapsed, setIsCourseSheetCollapsed] = useState(false);
const courseSheetRef = useRef<CourseSearchSheetRef>(null);
const searchButtonBottom = useRef(new Animated.Value(FLOATING_BUTTONS_DEFAULT_BOTTOM)).current;
const sheetContentHeightRef = useRef(0);
```

`isCourseSheetOpen`에 따라 `searchButtonBottom`을 시트 펼침/접힘 높이에 맞춰 애니메이션하는
기존 `useEffect`(498c410 참고)도 함께 복구한다.

### 추가 핸들러

`handleOpenCourseSearch`, `handleCloseCourseSearch`, `handleSelectCourse`,
`handleViewCourseDetail`, `handlePressWritePost`, `handlePressCourseBoard` — 498c410의 구현을
그대로 이식하되, `getCourses` 호출부만 `getPublicCourses`로 교체한다.

`handleMapPress`에 `if (isCourseSheetOpen) return;` 가드를 추가해, 코스 탐색 중에는 지도 탭이
사용자 경로에 웨이포인트를 추가하지 않게 한다 (기존 `isRouting`/`isBrowseMode` 가드와 나란히).

### UI 변경

- 좌하단: "주변 코스 찾기" pill 버튼(아이콘 `navigate` + 라벨, `Colors.primary` 배경)을 기존
  검색(이름/태그) FAB 왼쪽에 추가. 두 버튼 모두 `Animated.View`로 감싸 시트 위치를 따라간다.
- 우측 플로팅 버튼: `isCourseSheetOpen`이 아닐 때는 기존 그대로(위치/보행로토글/undo/저장/삭제),
  열려있을 때는 위치 + 글쓰기(`create-outline`, `handlePressWritePost`, `selectedCourseDetail`
  없으면 disabled) + 목록(`list-outline`, `handlePressCourseBoard`, 동일 조건)으로 전환.
- `<CourseSearchSheet>` 연결 — 컴포넌트는 이미 최종 완성형으로 트리에 남아있어(orphaned) 그대로
  재사용, props만 다시 연결한다.

기존에 새로 생긴 것(`isSearchOpen`/`CourseSearchBar`, `isPedestrianRouteEnabled`,
`isBrowseMode` state 자체)은 변경하지 않는다.

## 변경 4: `CourseDetailScreen.tsx`

`course-detail-review-buttons.md`/설계 문서 그대로 복구:

```ts
const handlePressWriteReview = () => {
  if (!requireAuth() || !course) return;
  navigation.navigate('PostCreate', {
    attachedCourseId: course.id,
    attachedCourseTitle: course.title,
  });
};

const handlePressReviewBoard = () => {
  if (!course) return;
  navigation.navigate('CourseBoard', {
    courseId: course.id,
    courseTitle: course.title,
  });
};
```

지도 우측에 원형 FAB 2개(`create-outline`, `list-outline`), `MapScreen`의 `floatingButtons`/
`fab` 스타일 로컬 복제. `useAuthGate`의 `requireAuth`를 새로 import.

## 에러 처리

기존 패턴 그대로: `getBounds()`/`getPublicCourses()`/`getCourse()` 실패는 모두 `Alert.alert`.
후기 작성/목록 버튼은 별도 에러 케이스 없음(기존 `PostCreate`/`CourseBoard` 화면의 에러 처리
재사용).

## 검증 계획

- `npx tsc --noEmit`
- `npx expo start` 백그라운드 기동 후 bundle 200 확인
- 실기기/시뮬레이터:
  - Map 화면: "주변 코스 찾기" → 바텀시트에 목록 표시 → 코스 선택 → 지도에 주황 점선 미리보기
    → "상세 보기" → 카메라 이동 + 순서 핀 → 글쓰기/목록 버튼 진입 확인
  - CourseDetail 화면: 저장한 코스 → 상세 진입 → 후기 작성(로그인 게이트, 제목 prefill)/목록
    (코스별 필터링) 버튼 확인
  - 기존 기능 회귀 없음 확인: 이름/태그 검색, 보행로 토글, 내 경로 그리기/저장/삭제

## 완료 후 문서화

`mobile/docs/implementations/`에 복구 요약 기록 (예:
`restore-map-course-search-and-review-buttons.md`) — 무엇이 왜 사라졌었는지, 어떤 커밋을
참고해 복구했는지 남긴다.

## Git

브랜치 하나로 두 화면을 함께 복구한다 (`870743d`가 같은 사고의 다른 파일들을 한 커밋으로
복구한 선례를 따름). 브랜치명: `fix/restore-map-search-review-buttons`. main은 이제 branch
protection이 걸려 있으므로 PR을 통해 병합한다.
