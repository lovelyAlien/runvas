# 지도 범위 기반 코스 조회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Map 화면 우측 상단에 원형 "코스 조회" 버튼을 추가해, 현재 지도에 보이는 영역 기준으로 공개 코스 목록을 바텀시트로 보여주고, 선택한 코스를 같은 지도 위에 바로 표시한다.

**Architecture:** 백엔드/문서 변경 없이 이미 존재하는 `GET /courses`(bounds 기반), `GET /courses/{courseId}` API만 재사용하는 모바일 전용 기능. `KakaoMapView`에 WebView↔RN 명령형 메서드 2개(`getBounds`, `showCourse`)를 추가하고, 신규 바텀시트 컴포넌트와 `MapScreen`의 새 버튼/상태로 연결한다.

**Tech Stack:** React Native (Expo SDK 54), TypeScript, `react-native-webview` + Kakao Maps JS SDK, 기존 `courseApi.ts`(fetch 기반).

## Global Constraints

- 좌표는 항상 `{ latitude, longitude }` 전체 이름 사용, `lat`/`lng` 축약 금지 (`mobile/CLAUDE.md`).
- 거리는 meters, 시간은 seconds로만 상태에 보관하고, 분/시간 변환은 표시 컴포넌트에서만 한다 (`mobile/CLAUDE.md`).
- WebView 새 메시지 타입을 추가할 때는 `KakaoMapViewRef` 인터페이스와 HTML `<script>` 핸들러를 항상 같이 수정한다 (`mobile/CLAUDE.md`).
- 이 저장소에는 jest 등 테스트 러너가 없다 (`mobile/CLAUDE.md` §테스트). 각 태스크의 검증은
  `npx tsc --noEmit` + 코드 리뷰로 하고, 전체 동작의 수동 확인은 마지막 태스크에서 한 번에 한다.
- `docs/api-contract.md`, `docs/data-model.md`는 이 작업에서 변경하지 않는다 (기존 계약 재사용,
  `docs/superpowers/specs/2026-07-02-map-course-search-design.md` §배경 참고).
- 커밋 메시지는 Conventional Commits 형식, `git add`는 파일을 명시해서 add한다 (`git add -A` 금지).
- `sh scripts/setup-git-hooks.sh`가 이미 이 워크트리에 적용되어 있다 (`core.hooksPath=.githooks` 확인됨) — 별도 조치 불필요.

---

### Task 1: KakaoMapView — `getBounds()` 추가

**Files:**
- Modify: `mobile/src/components/KakaoMapView.tsx`

**Interfaces:**
- Consumes: 없음 (기존 `KakaoMapViewRef`, `Coordinate`, `GeoBounds` 타입 재사용)
- Produces: `KakaoMapViewRef.getBounds(): Promise<GeoBounds>` — 이후 Task 5에서 `MapScreen`이 호출

- [ ] **Step 1: `KakaoMapViewRef` 인터페이스에 `getBounds` 추가**

`mobile/src/components/KakaoMapView.tsx:11-18`의 인터페이스를 아래로 교체:

```ts
export interface KakaoMapViewRef {
  moveToLocation: (coord: Coordinate) => void;
  addWaypoint: (coord: Coordinate, index: number) => void; // 마커만 추가
  addRouteSegment: (coords: Coordinate[]) => void; // 실제 경로 폴리라인 추가
  fitBounds: (bounds: GeoBounds) => void; // 카메라를 주어진 영역에 맞춤 (저장된 코스 보기용)
  getBounds: () => Promise<GeoBounds>; // 현재 지도에 보이는 영역 조회 (코스 조회 버튼용)
  undoLast: () => void;
  clearMap: () => void;
}
```

- [ ] **Step 2: WebView HTML 스크립트에 `GET_BOUNDS` 핸들러 추가**

`mobile/src/components/KakaoMapView.tsx`에서 아래 부분을 찾는다 (line 183-189 부근):

```js
      } else if (msg.type === 'FIT_BOUNDS') {
        var sw = new kakao.maps.LatLng(msg.swLat, msg.swLng);
        var ne = new kakao.maps.LatLng(msg.neLat, msg.neLng);
        var llBounds = new kakao.maps.LatLngBounds(sw, ne);
        map.setBounds(llBounds);

      } else if (msg.type === 'CLEAR') {
```

`} else if (msg.type === 'CLEAR') {` 줄 바로 앞에 `GET_BOUNDS` 분기를 끼워 넣어 아래 형태로 만든다
(기존 코드는 지우지 않고 사이에 새 분기만 추가하는 것):

```js
      } else if (msg.type === 'FIT_BOUNDS') {
        var sw = new kakao.maps.LatLng(msg.swLat, msg.swLng);
        var ne = new kakao.maps.LatLng(msg.neLat, msg.neLng);
        var llBounds = new kakao.maps.LatLngBounds(sw, ne);
        map.setBounds(llBounds);

      } else if (msg.type === 'GET_BOUNDS') {
        var currentBounds = map.getBounds();
        var boundsSw = currentBounds.getSouthWest();
        var boundsNe = currentBounds.getNorthEast();
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'BOUNDS_RESULT',
          swLat: boundsSw.getLat(),
          swLng: boundsSw.getLng(),
          neLat: boundsNe.getLat(),
          neLng: boundsNe.getLng()
        }));

      } else if (msg.type === 'CLEAR') {
```

- [ ] **Step 3: RN 컴포넌트에 pending resolver ref와 `getBounds` 구현 추가**

`mobile/src/components/KakaoMapView.tsx:222-227`의 컴포넌트 시작부(`const webViewRef = useRef<WebView>(null);` 바로 뒤)에 추가:

```ts
    const boundsResolverRef = useRef<((bounds: GeoBounds) => void) | null>(null);
```

`mobile/src/components/KakaoMapView.tsx:239-247`의 `fitBounds` 구현 바로 뒤에 추가:

```ts
      getBounds: () => {
        return new Promise<GeoBounds>((resolve) => {
          boundsResolverRef.current = resolve;
          postMessage({ type: 'GET_BOUNDS' });
        });
      },
```

- [ ] **Step 4: `handleMessage`에 `BOUNDS_RESULT` 분기 추가**

`mobile/src/components/KakaoMapView.tsx:256-265`의 `handleMessage` 함수를 아래로 교체:

```ts
    const handleMessage = (event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'MAP_PRESS') {
          onMapPress({ latitude: data.latitude, longitude: data.longitude });
        } else if (data.type === 'MAP_READY') {
          onMapReady?.();
        } else if (data.type === 'BOUNDS_RESULT') {
          boundsResolverRef.current?.({
            southWest: { latitude: data.swLat, longitude: data.swLng },
            northEast: { latitude: data.neLat, longitude: data.neLng },
          });
          boundsResolverRef.current = null;
        }
      } catch (_) {}
    };
```

- [ ] **Step 5: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 6: Commit**

```bash
git add mobile/src/components/KakaoMapView.tsx
git commit -m "feat(mobile): KakaoMapView에 getBounds 명령형 메서드 추가"
```

---

### Task 2: KakaoMapView — `showCourse()` 추가

**Files:**
- Modify: `mobile/src/components/KakaoMapView.tsx`

**Interfaces:**
- Consumes: `RoutePoint`, `GeoBounds` 타입 (`../types`)
- Produces: `KakaoMapViewRef.showCourse(path: RoutePoint[], bounds: GeoBounds): void` — Task 5에서 `MapScreen.handleSelectCourse`가 호출

- [ ] **Step 1: `RoutePoint` 타입 import 추가**

`mobile/src/components/KakaoMapView.tsx:4`를 교체:

```ts
import { Coordinate, GeoBounds, RoutePoint } from '../types';
```

- [ ] **Step 2: `KakaoMapViewRef` 인터페이스에 `showCourse` 추가**

Task 1에서 만든 인터페이스에 `getBounds` 줄 바로 뒤에 추가:

```ts
  showCourse: (path: RoutePoint[], bounds: GeoBounds) => void; // 조회한 공개 코스를 지도에 미리보기로 표시
```

- [ ] **Step 3: WebView 스크립트에 `previewPolyline` 변수 선언 추가**

`mobile/src/components/KakaoMapView.tsx:107-111`의 변수 선언부를 아래로 교체:

```js
    var map;
    var waypointMarkers = []; // 사용자가 탭한 마커
    var currentLocationOverlay; // 현재 위치 표시용 핀
    var segmentPolylines = []; // 각 구간별 폴리라인
    var routePolyline;         // 전체 경로 폴리라인
    var previewPolyline;       // 코스 조회로 선택한 공개 코스 미리보기 폴리라인 (사용자가 그리는 경로와 별개)
```

- [ ] **Step 4: `SHOW_COURSE` 메시지 핸들러 추가**

Task 1 Step 2에서 추가한 `GET_BOUNDS` 분기 바로 뒤, `} else if (msg.type === 'CLEAR') {` 줄 바로
앞에 `SHOW_COURSE` 분기를 끼워 넣는다:

```js
      } else if (msg.type === 'SHOW_COURSE') {
        if (previewPolyline) {
          previewPolyline.setMap(null);
          previewPolyline = null;
        }
        var previewPath = msg.coords.map(function(c) {
          return new kakao.maps.LatLng(c.latitude, c.longitude);
        });
        previewPolyline = new kakao.maps.Polyline({
          path: previewPath,
          strokeWeight: 5,
          strokeColor: '#F97316',
          strokeOpacity: 0.9,
          strokeStyle: 'shortdash'
        });
        previewPolyline.setMap(map);

        var previewSw = new kakao.maps.LatLng(msg.bounds.swLat, msg.bounds.swLng);
        var previewNe = new kakao.maps.LatLng(msg.bounds.neLat, msg.bounds.neLng);
        map.setBounds(new kakao.maps.LatLngBounds(previewSw, previewNe));

      } else if (msg.type === 'CLEAR') {
```

- [ ] **Step 5: RN 컴포넌트에 `showCourse` 구현 추가**

Task 1 Step 3에서 추가한 `getBounds` 구현 바로 뒤에 추가:

```ts
      showCourse: (path: RoutePoint[], bounds: GeoBounds) => {
        postMessage({
          type: 'SHOW_COURSE',
          coords: path.map((p) => ({ latitude: p.latitude, longitude: p.longitude })),
          bounds: {
            swLat: bounds.southWest.latitude,
            swLng: bounds.southWest.longitude,
            neLat: bounds.northEast.latitude,
            neLng: bounds.northEast.longitude,
          },
        });
      },
```

- [ ] **Step 6: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 7: Commit**

```bash
git add mobile/src/components/KakaoMapView.tsx
git commit -m "feat(mobile): KakaoMapView에 showCourse 미리보기 폴리라인 추가"
```

---

### Task 3: courseApi — `getCourses()` 추가

**Files:**
- Modify: `mobile/src/services/courseApi.ts`

**Interfaces:**
- Consumes: `GeoBounds`, `CourseSummary` 타입 (이미 import됨), `API_BASE_URL` (이미 정의됨)
- Produces: `getCourses(bounds: GeoBounds, accessToken?: string): Promise<CourseSummary[]>` — Task 5에서 `MapScreen.handleOpenCourseSearch`가 호출

- [ ] **Step 1: `getCourses` 함수 추가**

`mobile/src/services/courseApi.ts:116` (파일 끝, `deleteCourse` 함수 뒤)에 추가:

```ts

export async function getCourses(bounds: GeoBounds, accessToken?: string): Promise<CourseSummary[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const params = new URLSearchParams({
    swLat: String(bounds.southWest.latitude),
    swLng: String(bounds.southWest.longitude),
    neLat: String(bounds.northEast.latitude),
    neLng: String(bounds.northEast.longitude),
  });

  const response = await fetch(`${API_BASE_URL}/api/courses?${params.toString()}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { courses } = (await response.json()) as { courses: CourseSummary[] };
  return courses;
}
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/services/courseApi.ts
git commit -m "feat(mobile): 지도 범위 기준 공개 코스 목록 조회 API 클라이언트 추가"
```

---

### Task 4: `CourseSearchSheet` 컴포넌트 생성

**Files:**
- Create: `mobile/src/components/CourseSearchSheet.tsx`

**Interfaces:**
- Consumes: `CourseSummary` 타입 (`../types`), `formatDistance`/`formatDuration` (`../utils/format`), `Colors` (`../constants/theme`)
- Produces: `CourseSearchSheet` 기본 export 컴포넌트, props `{ visible: boolean; courses: CourseSummary[]; isLoading: boolean; onSelectCourse: (courseId: string) => void; onClose: () => void }` — Task 5에서 `MapScreen`이 렌더링

- [ ] **Step 1: 컴포넌트 파일 작성**

`mobile/src/components/CourseSearchSheet.tsx` 생성:

```tsx
import React from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Modal,
  StyleSheet,
} from 'react-native';
import { formatDistance, formatDuration } from '../utils/format';
import { Colors } from '../constants/theme';
import { CourseSummary } from '../types';

interface Props {
  visible: boolean;
  courses: CourseSummary[];
  isLoading: boolean;
  onSelectCourse: (courseId: string) => void;
  onClose: () => void;
}

export default function CourseSearchSheet({
  visible,
  courses,
  isLoading,
  onSelectCourse,
  onClose,
}: Props) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.header}>
            <Text style={styles.headerTitle}>주변 코스</Text>
            <TouchableOpacity onPress={onClose} activeOpacity={0.7}>
              <Text style={styles.closeLabel}>닫기</Text>
            </TouchableOpacity>
          </View>

          {isLoading ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={Colors.primary} />
            </View>
          ) : (
            <FlatList
              data={courses}
              keyExtractor={(item) => item.id}
              contentContainerStyle={courses.length === 0 ? styles.emptyContainer : undefined}
              ListEmptyComponent={
                <Text style={styles.emptyText}>주변에 표시된 코스가 없습니다.</Text>
              }
              renderItem={({ item }) => (
                <TouchableOpacity
                  style={styles.row}
                  activeOpacity={0.7}
                  onPress={() => onSelectCourse(item.id)}
                >
                  <Text style={styles.rowTitle} numberOfLines={1}>
                    {item.title}
                  </Text>
                  <Text style={styles.rowMeta}>
                    {formatDistance(item.distanceMeters)} ·{' '}
                    {formatDuration(item.estimatedDurationSeconds)}
                    {item.tags.length > 0 ? ` · ${item.tags.join(', ')}` : ''}
                  </Text>
                </TouchableOpacity>
              )}
            />
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  sheet: {
    backgroundColor: Colors.white,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    maxHeight: '60%',
    paddingBottom: 20,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  closeLabel: {
    fontSize: 14,
    color: Colors.gray500,
  },
  loadingContainer: {
    paddingVertical: 40,
    alignItems: 'center',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  row: {
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  rowTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.gray900,
  },
  rowMeta: {
    fontSize: 12,
    color: Colors.gray500,
    marginTop: 4,
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/components/CourseSearchSheet.tsx
git commit -m "feat(mobile): 주변 코스 목록 바텀시트 컴포넌트 추가"
```

---

### Task 5: MapScreen — 버튼/상태/핸들러 연결

**Files:**
- Modify: `mobile/src/screens/MapScreen.tsx`

**Interfaces:**
- Consumes: `KakaoMapViewRef.getBounds()`, `KakaoMapViewRef.showCourse(path, bounds)` (Task 1, 2), `getCourses(bounds, accessToken?)`, `getCourse(courseId, accessToken?)` (Task 3, 기존), `CourseSearchSheet` props (Task 4), `CourseSummary` 타입
- Produces: 없음 (최종 화면 조립)

- [ ] **Step 1: import 추가/수정**

`mobile/src/screens/MapScreen.tsx:25`를 교체:

```ts
import { postCourse, buildCreateCourseRequest, getCourse, getCourses } from '../services/courseApi';
```

`mobile/src/screens/MapScreen.tsx:28`을 교체:

```ts
import { Coordinate, CourseSummary } from '../types';
```

`mobile/src/screens/MapScreen.tsx:17`(`import KakaoMapView, { KakaoMapViewRef } from '../components/KakaoMapView';`) 바로 뒤에 추가:

```ts
import CourseSearchSheet from '../components/CourseSearchSheet';
```

- [ ] **Step 2: 상태 추가**

`mobile/src/screens/MapScreen.tsx:59` (`const [isSavingPace, setIsSavingPace] = useState(false);` 바로 뒤)에 추가:

```ts
  const [isCourseSheetOpen, setIsCourseSheetOpen] = useState(false);
  const [nearbyCourses, setNearbyCourses] = useState<CourseSummary[]>([]);
  const [isLoadingCourses, setIsLoadingCourses] = useState(false);
```

- [ ] **Step 3: 핸들러 추가**

`mobile/src/screens/MapScreen.tsx:103`(`handleLocate` 함수 뒤, `handleUndo` 앞)에 추가:

```ts
  const handleOpenCourseSearch = async () => {
    if (!mapRef.current) return;
    setIsLoadingCourses(true);
    setIsCourseSheetOpen(true);
    try {
      const bounds = await mapRef.current.getBounds();
      const courses = await getCourses(bounds, accessToken ?? undefined);
      setNearbyCourses(courses);
    } catch (e: unknown) {
      Alert.alert('조회 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      setIsCourseSheetOpen(false);
    } finally {
      setIsLoadingCourses(false);
    }
  };

  const handleSelectCourse = async (courseId: string) => {
    setIsCourseSheetOpen(false);
    try {
      const course = await getCourse(courseId, accessToken ?? undefined);
      mapRef.current?.showCourse(course.path, course.bounds);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    }
  };

```

- [ ] **Step 4: 우측 상단 버튼 추가**

`mobile/src/screens/MapScreen.tsx:207-208`(`<KakaoMapView ref={mapRef} onMapPress={handleMapPress} />` 바로 뒤)에 추가:

```tsx

        {/* 우측 상단 코스 조회 버튼 */}
        <View style={styles.topRightButtons}>
          <FAB icon="search" onPress={handleOpenCourseSearch} />
        </View>
```

- [ ] **Step 5: `CourseSearchSheet` 렌더링 추가**

`mobile/src/screens/MapScreen.tsx:249-250`(`<PaceSelector ... />` 컴포넌트 뒤, `<Modal visible={isSaveModalOpen} ...>` 앞)에 추가:

```tsx

      <CourseSearchSheet
        visible={isCourseSheetOpen}
        courses={nearbyCourses}
        isLoading={isLoadingCourses}
        onSelectCourse={handleSelectCourse}
        onClose={() => setIsCourseSheetOpen(false)}
      />
```

- [ ] **Step 6: 스타일 추가**

`mobile/src/screens/MapScreen.tsx:315-320`의 `floatingButtons` 스타일 정의 바로 뒤에 추가:

```ts
  topRightButtons: {
    position: 'absolute',
    right: 16,
    top: 16,
  },
```

- [ ] **Step 7: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 8: Commit**

```bash
git add mobile/src/screens/MapScreen.tsx
git commit -m "feat(mobile): Map 화면에 지도 범위 기반 코스 조회 버튼과 바텀시트 연결"
```

---

### Task 6: 수동 검증 + 구현 문서 기록

**Files:**
- Create: `mobile/docs/implementations/map-course-search.md`

**Interfaces:**
- Consumes: 없음 (검증 및 문서화 태스크)
- Produces: 없음

- [ ] **Step 1: Expo 번들 기동 확인**

Run:
```bash
cd mobile && npx expo start --non-interactive &
sleep 8
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```
Expected: `200`

번들 확인 후 `npx expo start` 프로세스를 종료한다 (`kill %1` 또는 해당 터미널 종료).

- [ ] **Step 2: 시뮬레이터/실기기 수동 확인**

아래 항목을 실기기 또는 시뮬레이터에서 직접 확인한다 (`mobile/CLAUDE.md` §변경 후 검증 규칙):

1. Map 화면 진입 시 우측 상단에 다른 FAB과 동일한 크기의 원형 "코스 조회"(돋보기 아이콘) 버튼이 보인다.
2. 버튼을 누르면 바텀시트가 열리고 로딩 인디케이터 후 현재 지도 화면에 보이는 공개 코스 목록(제목/거리/시간)이 표시된다.
3. 주변에 공개 코스가 없는 위치에서는 "주변에 표시된 코스가 없습니다"가 표시된다.
4. 목록에서 코스를 탭하면 바텀시트가 닫히고, 같은 지도 위에 해당 코스 경로가 주황 점선으로 그려지며 카메라가 그 코스 영역에 맞춰 이동한다.
5. 사용자가 직접 그리던 경로(파란 실선/마커)가 있다면, 코스 미리보기를 표시해도 사라지지 않는다.
6. 다른 코스를 다시 선택하면 이전 미리보기가 사라지고 새 코스만 표시된다.
7. 비로그인 상태에서도 버튼과 목록 조회가 동작한다 (`GET /courses`가 `Optional` 인증이므로).

- [ ] **Step 3: 구현 문서 작성**

`mobile/docs/implementations/map-course-search.md` 생성 (기존 문서 형식 — `mobile/docs/implementations/saved-courses-from-backend.md` 참고):

```markdown
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
  선택 시 상세 조회 → 지도 표시 흐름을 연결했다.
- 사용자가 직접 그리는 경로(`waypoints`/`routeCoords`)와 조회한 코스 미리보기는 완전히 분리된
  상태다 — 미리보기는 새로 선택할 때만 교체되고, 별도의 "미리보기 닫기" UI는 만들지 않았다 (YAGNI).

## 검증

- `npx tsc --noEmit` 통과.
- `npx expo start` 번들 200 확인.
- 시뮬레이터에서 코스 조회 버튼 → 목록 → 선택 → 지도에 주황 점선으로 표시되는 흐름 확인.
```

- [ ] **Step 4: Commit**

```bash
git add mobile/docs/implementations/map-course-search.md
git commit -m "docs(mobile): 지도 범위 기반 코스 조회 구현 기록 추가"
```
