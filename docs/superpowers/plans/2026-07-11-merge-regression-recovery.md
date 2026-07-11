# 병합 회귀로 소실된 기능 복구 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 병합 커밋 `5e9c849`가 PR 리뷰 없이 main에 직접 push되면서 사라진 Map 화면의 "주변 코스
찾기"(바텀시트) 기능과 코스별 "후기 작성/목록" 버튼(Map 화면 + CourseDetail 화면)을 복구한다.

**Architecture:** 병합 이전 main 커밋(`498c410`)에 남아있는 완성된 구현을 그대로 참고자료 삼아
현재 코드베이스(그 사이 `codex/mobile-map` 쪽에서 추가된 태그/이름 검색, 보행로 토글은 그대로
유지)에 재통합한다. 새 API·데이터 모델은 추가하지 않는다.

**Tech Stack:** React Native (Expo SDK 54), TypeScript, react-native-webview(카카오맵 SDK 브리지)

## Global Constraints

- 이 저장소(`mobile/`)에는 jest 등 테스트 러너가 설정되어 있지 않다(`mobile/CLAUDE.md`). 각
  태스크의 검증은 `npx tsc --noEmit`(타입 체크)과 실기기/시뮬레이터 수동 확인으로 대체한다 —
  "실패하는 테스트 작성" 단계 대신 "변경 후 tsc 통과 확인" 단계를 쓴다.
- 좌표는 항상 `{ latitude, longitude }` 전체 이름을 쓴다 (`mobile/CLAUDE.md`).
- API 계약/데이터 모델 변경 없음 — 기존 `GET /courses`, `PostCreate`/`CourseBoard` 라우트만
  재사용한다. `docs/api-contract.md`, `docs/data-model.md`는 건드리지 않는다.
- 커밋 메시지는 Conventional Commits, body에 왜 이 변경이 필요한지 남긴다. `Co-Authored-By` 등
  도구/저작자 표시는 절대 넣지 않는다 (`CLAUDE.md` Git 작업 규칙).
- 변경 파일만 `git add`로 스테이징한다 (`git add .`/`-A` 금지).
- 작업 위치: `.claude/worktrees/restore-map-search-review-buttons` (브랜치
  `fix/restore-map-search-review-buttons`, 이미 생성됨). 모든 명령은 이 워크트리 안에서 실행한다.

---

## Task 1: KakaoMapView — 코스 미리보기 관련 메서드 4개 복구

**Files:**
- Modify: `mobile/src/components/KakaoMapView.tsx`

**Interfaces:**
- Consumes: 없음 (다른 태스크에 의존하지 않음)
- Produces: `KakaoMapViewRef`에 추가되는 4개 메서드 — Task 2가 이 시그니처를 그대로 사용한다.
  ```ts
  getBounds: () => Promise<GeoBounds>;
  previewCourse: (path: RoutePoint[]) => void;
  showCourseWaypoints: (waypoints: RoutePoint[]) => void;
  clearCoursePreview: () => void;
  ```

- [ ] **Step 1: `RoutePoint` 타입 import 추가**

`mobile/src/components/KakaoMapView.tsx:4`의 현재 코드:
```ts
import { Coordinate, GeoBounds } from '../types';
```
다음으로 교체:
```ts
import { Coordinate, GeoBounds, RoutePoint } from '../types';
```

- [ ] **Step 2: `KakaoMapViewRef` 인터페이스에 메서드 4개 추가**

`mobile/src/components/KakaoMapView.tsx:18-28`의 현재 코드:
```ts
export interface KakaoMapViewRef {
  moveToLocation: (coord: Coordinate) => void;
  addWaypoint: (coord: Coordinate, index: number) => void;
  addRouteSegment: (coords: Coordinate[]) => void;
  fitBounds: (bounds: GeoBounds) => void;
  undoLast: () => void;
  clearMap: () => void;
  showPublicCourses: (courses: PublicCourseMarker[]) => void;
  clearPublicCourses: () => void;
  setBrowseMode: (enabled: boolean) => void;
}
```
다음으로 교체:
```ts
export interface KakaoMapViewRef {
  moveToLocation: (coord: Coordinate) => void;
  addWaypoint: (coord: Coordinate, index: number) => void;
  addRouteSegment: (coords: Coordinate[]) => void;
  fitBounds: (bounds: GeoBounds) => void;
  getBounds: () => Promise<GeoBounds>; // 현재 지도에 보이는 영역 1회 조회 ("주변 코스 찾기" 버튼용)
  previewCourse: (path: RoutePoint[]) => void; // 선택한 공개 코스 경로를 미리보기로 표시 (카메라 이동 없음)
  showCourseWaypoints: (waypoints: RoutePoint[]) => void; // "상세 보기" 시 경로 순서 번호 핀 표시
  clearCoursePreview: () => void; // 미리보기 경로/순서 핀 제거
  undoLast: () => void;
  clearMap: () => void;
  showPublicCourses: (courses: PublicCourseMarker[]) => void;
  clearPublicCourses: () => void;
  setBrowseMode: (enabled: boolean) => void;
}
```

- [ ] **Step 3: WebView HTML의 전역 변수에 미리보기 상태 추가**

`mobile/src/components/KakaoMapView.tsx:166-173`의 현재 코드:
```js
    var map;
    var waypointMarkers = [];
    var currentLocationOverlay;
    var segmentPolylines = [];
    var routePolyline;
    var publicCourseOverlays = [];
    var publicCourses = []; // 숫자 인덱스로만 참조 — 사용자 입력값을 onclick 속성에 직접 삽입하지 않기 위함
    var activeBubbleOverlay = null;
    var isBrowseMode = false;
```
다음으로 교체 (마지막 두 줄 추가):
```js
    var map;
    var waypointMarkers = [];
    var currentLocationOverlay;
    var segmentPolylines = [];
    var routePolyline;
    var publicCourseOverlays = [];
    var publicCourses = []; // 숫자 인덱스로만 참조 — 사용자 입력값을 onclick 속성에 직접 삽입하지 않기 위함
    var activeBubbleOverlay = null;
    var isBrowseMode = false;
    var previewPolyline;       // 코스 조회로 선택한 공개 코스 미리보기 폴리라인 (사용자가 그리는 경로와 별개)
    var courseWaypointMarkers = []; // 코스 상세 보기 시 표시하는 경로 순서 번호 핀
```

- [ ] **Step 4: WebView 메시지 핸들러에 4개 케이스 추가**

`mobile/src/components/KakaoMapView.tsx:298-306`의 현재 코드(`FIT_BOUNDS`와 `CLEAR` 사이):
```js
      } else if (msg.type === 'FIT_BOUNDS') {
        // 캐시된 컨테이너 크기 기준으로 bounds를 맞추면 하단이 잘려 흰 공백이 생길 수 있어
        // 크기를 다시 계산한 뒤 bounds를 적용한다.
        map.relayout();
        var sw = new kakao.maps.LatLng(msg.swLat, msg.swLng);
        var ne = new kakao.maps.LatLng(msg.neLat, msg.neLng);
        var llBounds = new kakao.maps.LatLngBounds(sw, ne);
        map.setBounds(llBounds);

      } else if (msg.type === 'CLEAR') {
```
다음으로 교체 (`FIT_BOUNDS` 블록 뒤, `CLEAR` 앞에 4개 블록 삽입):
```js
      } else if (msg.type === 'FIT_BOUNDS') {
        // 캐시된 컨테이너 크기 기준으로 bounds를 맞추면 하단이 잘려 흰 공백이 생길 수 있어
        // 크기를 다시 계산한 뒤 bounds를 적용한다.
        map.relayout();
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

      } else if (msg.type === 'PREVIEW_COURSE') {
        if (previewPolyline) {
          previewPolyline.setMap(null);
          previewPolyline = null;
        }
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];
        var previewPath = msg.coords.map(function(c) {
          return new kakao.maps.LatLng(c.latitude, c.longitude);
        });
        previewPolyline = new kakao.maps.Polyline({
          path: previewPath,
          strokeWeight: 3,
          strokeColor: '#F97316',
          strokeOpacity: 0.9,
          strokeStyle: 'solid'
        });
        previewPolyline.setMap(map);

      } else if (msg.type === 'SHOW_COURSE_WAYPOINTS') {
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];
        msg.waypoints.forEach(function(wp, i) {
          var latlng = new kakao.maps.LatLng(wp.latitude, wp.longitude);
          var colorClass = i === 0 ? 'start' : (i === msg.waypoints.length - 1 ? 'end' : 'mid');
          var overlay = new kakao.maps.CustomOverlay({
            position: latlng,
            yAnchor: 1,
            content: '<div class="waypoint-pin ' + colorClass + '"><span class="num">' + (i + 1) + '</span></div>'
          });
          overlay.setMap(map);
          courseWaypointMarkers.push(overlay);
        });

      } else if (msg.type === 'CLEAR_COURSE_PREVIEW') {
        if (previewPolyline) {
          previewPolyline.setMap(null);
          previewPolyline = null;
        }
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];

      } else if (msg.type === 'CLEAR') {
```

- [ ] **Step 5: RN 쪽에 `boundsResolverRef` 추가**

`mobile/src/components/KakaoMapView.tsx:384-388`의 현재 코드:
```ts
  ({ onMapPress, onMapReady, onBoundsChange, onCourseMarkerPress }, ref) => {
    const webViewRef = useRef<WebView>(null);

    const postMessage = (msg: object) => {
      webViewRef.current?.postMessage(JSON.stringify(msg));
    };
```
다음으로 교체:
```ts
  ({ onMapPress, onMapReady, onBoundsChange, onCourseMarkerPress }, ref) => {
    const webViewRef = useRef<WebView>(null);
    const boundsResolverRef = useRef<((bounds: GeoBounds) => void) | null>(null);

    const postMessage = (msg: object) => {
      webViewRef.current?.postMessage(JSON.stringify(msg));
    };
```

- [ ] **Step 6: `useImperativeHandle`에 4개 메서드 구현 추가**

`mobile/src/components/KakaoMapView.tsx:415-423`의 현재 코드(`fitBounds`와 `undoLast` 사이):
```ts
      fitBounds: (bounds: GeoBounds) => {
        postMessage({
          type: 'FIT_BOUNDS',
          swLat: bounds.southWest.latitude,
          swLng: bounds.southWest.longitude,
          neLat: bounds.northEast.latitude,
          neLng: bounds.northEast.longitude,
        });
      },
      undoLast: () => {
```
다음으로 교체:
```ts
      fitBounds: (bounds: GeoBounds) => {
        postMessage({
          type: 'FIT_BOUNDS',
          swLat: bounds.southWest.latitude,
          swLng: bounds.southWest.longitude,
          neLat: bounds.northEast.latitude,
          neLng: bounds.northEast.longitude,
        });
      },
      getBounds: () => {
        return new Promise<GeoBounds>((resolve, reject) => {
          const timeoutId = setTimeout(() => {
            boundsResolverRef.current = null;
            reject(new Error('지도 범위를 가져오지 못했습니다.'));
          }, 5000);
          boundsResolverRef.current = (bounds) => {
            clearTimeout(timeoutId);
            resolve(bounds);
          };
          postMessage({ type: 'GET_BOUNDS' });
        });
      },
      previewCourse: (path: RoutePoint[]) => {
        postMessage({
          type: 'PREVIEW_COURSE',
          coords: path.map((p) => ({ latitude: p.latitude, longitude: p.longitude })),
        });
      },
      showCourseWaypoints: (waypoints: RoutePoint[]) => {
        postMessage({
          type: 'SHOW_COURSE_WAYPOINTS',
          waypoints: waypoints.map((w) => ({ latitude: w.latitude, longitude: w.longitude })),
        });
      },
      clearCoursePreview: () => {
        postMessage({ type: 'CLEAR_COURSE_PREVIEW' });
      },
      undoLast: () => {
```

- [ ] **Step 7: `handleMessage`에 `BOUNDS_RESULT` 케이스 추가**

`mobile/src/components/KakaoMapView.tsx:441-457`의 현재 코드:
```ts
    const handleMessage = (event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'MAP_PRESS') {
          onMapPress({ latitude: data.latitude, longitude: data.longitude });
        } else if (data.type === 'MAP_READY') {
          onMapReady?.();
        } else if (data.type === 'MAP_BOUNDS_CHANGE') {
          onBoundsChange?.({
            southWest: { latitude: data.swLat, longitude: data.swLng },
            northEast: { latitude: data.neLat, longitude: data.neLng },
          });
        } else if (data.type === 'COURSE_MARKER_PRESS') {
          onCourseMarkerPress?.(data.courseId);
        }
      } catch (_) {}
    };
```
다음으로 교체:
```ts
    const handleMessage = (event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'MAP_PRESS') {
          onMapPress({ latitude: data.latitude, longitude: data.longitude });
        } else if (data.type === 'MAP_READY') {
          onMapReady?.();
        } else if (data.type === 'MAP_BOUNDS_CHANGE') {
          onBoundsChange?.({
            southWest: { latitude: data.swLat, longitude: data.swLng },
            northEast: { latitude: data.neLat, longitude: data.neLng },
          });
        } else if (data.type === 'COURSE_MARKER_PRESS') {
          onCourseMarkerPress?.(data.courseId);
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

- [ ] **Step 8: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없이 종료 (exit code 0). `KakaoMapView.tsx` 관련 에러가 없어야 한다 — 이 시점엔
아직 `MapScreen.tsx`가 새 메서드를 쓰지 않으므로 미사용 경고도 없어야 정상.

- [ ] **Step 9: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/restore-map-search-review-buttons
git add mobile/src/components/KakaoMapView.tsx
git commit -m "$(cat <<'EOF'
fix(mobile): 코스 미리보기 관련 KakaoMapView 메서드 복구

병합 커밋 5e9c849에서 조용히 사라진 getBounds/previewCourse/
showCourseWaypoints/clearCoursePreview를 병합 전 main 구현(498c410)
그대로 되살린다. 기존 showPublicCourses/setBrowseMode 등과는 별도
메시지 타입이라 충돌 없이 추가만 됨.
EOF
)"
```

---

## Task 2: MapScreen — "주변 코스 찾기" 바텀시트 복구

**Files:**
- Modify: `mobile/src/screens/MapScreen.tsx`

**Interfaces:**
- Consumes: Task 1의 `KakaoMapViewRef.getBounds/previewCourse/showCourseWaypoints/clearCoursePreview`,
  기존 `courseApi.getPublicCourses(params, accessToken?): Promise<{courses: CourseSummary[]; nextCursor: string | null}>`,
  기존 `courseApi.getCourse(courseId, accessToken?): Promise<Course>`,
  기존 `CourseSearchSheet`(props: `visible, courses, isLoading, selectedCourseId, onSelectCourse,
  onViewDetail, onClose, onCollapsedChange, onContentHeightChange`, ref: `{ expand(), translateY }`)
- Produces: 없음 (화면 컴포넌트, 최종 소비자)

- [ ] **Step 1: import 추가/수정**

`mobile/src/screens/MapScreen.tsx:1-34`의 현재 코드:
```ts
import React, { useRef, useCallback, useState, useEffect } from 'react';
import {
  View,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  Modal,
  Text,
  TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CompositeScreenProps } from '@react-navigation/native';

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef, PublicCourseMarker } from '../components/KakaoMapView';
import RouteStatsBar from '../components/RouteStatsBar';
import PaceSelector from '../components/PaceSelector';
import { useRoute, DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { useLocation } from '../hooks/useLocation';
import { useAuth } from '../contexts/AuthContext';
import { fetchPedestrianRoute } from '../services/routingApi';
import { exportGpx } from '../utils/exportGpx';
import { postCourse, buildCreateCourseRequest, getPublicCourses, searchPublicCourses, searchPublicCoursesByTag } from '../services/courseApi';
import CourseSearchBar from '../components/CourseSearchBar';
import { patchMe } from '../services/authApi';
import Toast from 'react-native-toast-message';
import { Colors } from '../constants/theme';
import { Coordinate, GeoBounds } from '../types';
import { formatPace } from '../utils/format';
import { RootTabParamList, RootStackParamList } from '../navigation/types';
```
다음으로 교체:
```ts
import React, { useRef, useCallback, useState, useEffect } from 'react';
import {
  Animated,
  View,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  Modal,
  Text,
  TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CompositeScreenProps } from '@react-navigation/native';

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef, PublicCourseMarker } from '../components/KakaoMapView';
import CourseSearchSheet, {
  CourseSearchSheetRef,
  SHEET_HANDLE_HEIGHT,
} from '../components/CourseSearchSheet';
import RouteStatsBar from '../components/RouteStatsBar';
import PaceSelector from '../components/PaceSelector';
import { useRoute, DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { useLocation } from '../hooks/useLocation';
import { useAuth } from '../contexts/AuthContext';
import { fetchPedestrianRoute } from '../services/routingApi';
import { exportGpx } from '../utils/exportGpx';
import {
  postCourse,
  buildCreateCourseRequest,
  getCourse,
  getPublicCourses,
  searchPublicCourses,
  searchPublicCoursesByTag,
} from '../services/courseApi';
import CourseSearchBar from '../components/CourseSearchBar';
import { patchMe } from '../services/authApi';
import Toast from 'react-native-toast-message';
import { Colors } from '../constants/theme';
import { Coordinate, Course, CourseSummary, GeoBounds } from '../types';
import { formatPace } from '../utils/format';
import { RootTabParamList, RootStackParamList } from '../navigation/types';
```

- [ ] **Step 2: 상단 레벨 상수 추가**

`mobile/src/screens/MapScreen.tsx:36-40`의 현재 코드:
```ts
type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Map'>,
  NativeStackScreenProps<RootStackParamList>
>;

export default function MapScreen({ navigation }: Props) {
```
다음으로 교체:
```ts
type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Map'>,
  NativeStackScreenProps<RootStackParamList>
>;

const FLOATING_BUTTONS_DEFAULT_BOTTOM = 16;
const FLOATING_BUTTONS_SHEET_GAP = 12; // 시트 상단과 우측 FAB 스택 사이 여백

export default function MapScreen({ navigation }: Props) {
```

- [ ] **Step 3: `courseSheetRef`와 코스 탐색 state 추가**

`mobile/src/screens/MapScreen.tsx:41-70`의 현재 코드:
```ts
export default function MapScreen({ navigation }: Props) {
  const mapRef = useRef<KakaoMapViewRef>(null);
  const { accessToken, requireAuth, user, updateUser } = useAuth();
  const { getCurrentLocation } = useLocation();

  const selectedPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  const {
    waypoints,
    routeCoords,
    stats,
    addFirstPoint,
    addSegment,
    undoLast,
    clearRoute,
    toRoutePoints,
    toWaypointPoints,
    getBounds,
  } = useRoute(selectedPace);

  const [isExporting, setIsExporting] = useState(false);
  const [isRouting, setIsRouting] = useState(false);
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [routeTitle, setRouteTitle] = useState('');
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isBrowseMode, setIsBrowseMode] = useState(false);
  const [isFetchingCourses, setIsFetchingCourses] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isPedestrianRouteEnabled, setIsPedestrianRouteEnabled] = useState(true);

  const boundsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
```
다음으로 교체:
```ts
export default function MapScreen({ navigation }: Props) {
  const mapRef = useRef<KakaoMapViewRef>(null);
  const courseSheetRef = useRef<CourseSearchSheetRef>(null);
  const { accessToken, requireAuth, user, updateUser } = useAuth();
  const { getCurrentLocation } = useLocation();

  const selectedPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  const {
    waypoints,
    routeCoords,
    stats,
    addFirstPoint,
    addSegment,
    undoLast,
    clearRoute,
    toRoutePoints,
    toWaypointPoints,
    getBounds,
  } = useRoute(selectedPace);

  const [isExporting, setIsExporting] = useState(false);
  const [isRouting, setIsRouting] = useState(false);
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [routeTitle, setRouteTitle] = useState('');
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isBrowseMode, setIsBrowseMode] = useState(false);
  const [isFetchingCourses, setIsFetchingCourses] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isPedestrianRouteEnabled, setIsPedestrianRouteEnabled] = useState(true);
  const [isCourseSheetOpen, setIsCourseSheetOpen] = useState(false);
  const [nearbyCourses, setNearbyCourses] = useState<CourseSummary[]>([]);
  const [isLoadingCourses, setIsLoadingCourses] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [selectedCourseDetail, setSelectedCourseDetail] = useState<Course | null>(null);
  const [isCourseSheetCollapsed, setIsCourseSheetCollapsed] = useState(false);
  const searchButtonBottom = useRef(new Animated.Value(FLOATING_BUTTONS_DEFAULT_BOTTOM)).current;
  const sheetContentHeightRef = useRef(0);

  const boundsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
```

- [ ] **Step 4: 탐색 버튼이 시트 위치를 따라가는 `useEffect` 추가 + 코스 탐색 핸들러 추가**

`mobile/src/screens/MapScreen.tsx`에서 (Step 3 적용 후 기준) `fetchPublicCourses`
콜백 바로 앞, 즉 `const boundsTimerRef = ...` 다음 줄부터 시작되는 아래 블록:
```ts

  const fetchPublicCourses = useCallback(
```
그 바로 앞에 다음 코드를 삽입한다 (줄바꿈 유지):
```ts

  // 탐색 버튼은 시트가 열려 있는 동안 사라지지 않고, 시트의 실시간 위치(드래그 중에도)를 그대로
  // 따라다닌다. translateY는 native driver로도 움직이므로, addListener로 JS 쪽 값을 동기화한다.
  useEffect(() => {
    if (!isCourseSheetOpen) {
      searchButtonBottom.setValue(FLOATING_BUTTONS_DEFAULT_BOTTOM);
      return;
    }
    const sheet = courseSheetRef.current;
    if (!sheet) return;
    const listenerId = sheet.translateY.addListener(({ value }) => {
      const target =
        SHEET_HANDLE_HEIGHT + sheetContentHeightRef.current + FLOATING_BUTTONS_SHEET_GAP - value;
      searchButtonBottom.setValue(target);
    });
    return () => sheet.translateY.removeListener(listenerId);
  }, [isCourseSheetOpen, searchButtonBottom]);

  const handleOpenCourseSearch = async () => {
    if (!mapRef.current) return;
    setIsLoadingCourses(true);
    setIsCourseSheetOpen(true);
    setSelectedCourseId(null);
    setSelectedCourseDetail(null);
    setIsCourseSheetCollapsed(false);
    sheetContentHeightRef.current = 0;
    mapRef.current.clearCoursePreview();
    courseSheetRef.current?.expand();
    try {
      const bounds = await mapRef.current.getBounds();
      const { courses } = await getPublicCourses(
        {
          swLat: bounds.southWest.latitude,
          swLng: bounds.southWest.longitude,
          neLat: bounds.northEast.latitude,
          neLng: bounds.northEast.longitude,
        },
        accessToken ?? undefined
      );
      setNearbyCourses(courses);
    } catch (e: unknown) {
      Alert.alert('조회 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      setIsCourseSheetOpen(false);
    } finally {
      setIsLoadingCourses(false);
    }
  };

  const handleCloseCourseSearch = () => {
    setIsCourseSheetOpen(false);
    setSelectedCourseId(null);
    setSelectedCourseDetail(null);
    setIsCourseSheetCollapsed(false);
    mapRef.current?.clearCoursePreview();
  };

  // 목록에서 코스를 선택: 현재 지도 범위를 유지한 채 경로만 미리보기로 그린다 (카메라 이동 없음).
  const handleSelectCourse = async (courseId: string) => {
    setSelectedCourseId(courseId);
    try {
      const course = await getCourse(courseId, accessToken ?? undefined);
      setSelectedCourseDetail(course);
      mapRef.current?.previewCourse(course.path);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      setSelectedCourseId(null);
      setSelectedCourseDetail(null);
    }
  };

  // "상세 보기" 버튼: 선택된 코스의 좌표로 카메라를 이동하고 경로 순서(웨이포인트 번호)를 표시한다.
  const handleViewCourseDetail = (courseId: string) => {
    if (!selectedCourseDetail || selectedCourseDetail.id !== courseId) return;
    mapRef.current?.fitBounds(selectedCourseDetail.bounds);
    mapRef.current?.showCourseWaypoints(selectedCourseDetail.waypoints);
  };

  const handlePressWritePost = () => {
    if (!requireAuth() || !selectedCourseDetail) return;
    navigation.navigate('PostCreate', {
      attachedCourseId: selectedCourseDetail.id,
      attachedCourseTitle: selectedCourseDetail.title,
    });
  };

  const handlePressCourseBoard = () => {
    if (!selectedCourseDetail) return;
    navigation.navigate('CourseBoard', {
      courseId: selectedCourseDetail.id,
      courseTitle: selectedCourseDetail.title,
    });
  };

  const fetchPublicCourses = useCallback(
```

(원래 있던 `getPublicCourses` 브라우즈 모드용 호출부(`fetchPublicCourses` 콜백 내부)는 그대로
둔다 — 파라미터 형태가 `{swLat,swLng,neLat,neLng,limit:50}`로 이미 존재하는 호출이라 손대지
않는다. `handleOpenCourseSearch`에서 새로 추가한 호출은 `limit`을 생략해 기본값(50)을 쓴다.)

- [ ] **Step 5: `handleMapPress`에 시트 열림 가드 추가**

`mobile/src/screens/MapScreen.tsx:166-196`의 현재 코드:
```ts
  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isBrowseMode) return;
      if (isRouting) return;

      if (waypoints.length === 0) {
```
다음으로 교체:
```ts
  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isBrowseMode) return;
      if (isCourseSheetOpen) return; // 코스 탐색 중에는 지도 탭이 내 경로에 웨이포인트를 추가하지 않게 한다
      if (isRouting) return;

      if (waypoints.length === 0) {
```

그리고 같은 콜백의 의존성 배열(`mobile/src/screens/MapScreen.tsx:195`)의 현재 코드:
```ts
    [waypoints, isRouting, isBrowseMode, isPedestrianRouteEnabled, accessToken, addFirstPoint, addSegment]
```
다음으로 교체:
```ts
    [waypoints, isRouting, isBrowseMode, isCourseSheetOpen, isPedestrianRouteEnabled, accessToken, addFirstPoint, addSegment]
```

- [ ] **Step 6: 좌하단 버튼 영역을 pill 버튼 + 검색 FAB로 교체**

`mobile/src/screens/MapScreen.tsx`의 현재 코드:
```tsx
        {isSearchOpen && (
          <CourseSearchBar
            onClose={() => setIsSearchOpen(false)}
            onSelectCourse={handleSelectSearchResult}
            onSearch={handleSearchCourse}
            onSearchByTag={handleSearchCourseByTag}
          />
        )}

        <View style={styles.floatingButtons}>
          <FAB
            icon="search"
            onPress={() => setIsSearchOpen(true)}
            color={Colors.gray500}
          />
          <FAB icon="locate" onPress={handleLocate} />
          {!isBrowseMode && (
            <>
              <FAB
                icon={isPedestrianRouteEnabled ? 'walk' : 'walk-outline'}
                onPress={togglePedestrianRoute}
                color={isPedestrianRouteEnabled ? Colors.primary : Colors.gray400}
              />
              <FAB
                icon="arrow-undo"
                onPress={handleUndo}
                disabled={waypoints.length === 0 || isRouting}
              />
              <FAB icon="save-outline" onPress={handleOpenSaveModal} disabled={!canSave} />
              <FAB
                icon="trash-outline"
                onPress={handleClear}
                disabled={waypoints.length === 0 || isRouting}
                color={Colors.danger}
              />
            </>
          )}
        </View>
```
다음으로 교체:
```tsx
        {isSearchOpen && (
          <CourseSearchBar
            onClose={() => setIsSearchOpen(false)}
            onSelectCourse={handleSelectSearchResult}
            onSearch={handleSearchCourse}
            onSearchByTag={handleSearchCourseByTag}
          />
        )}

        {/* 좌측 하단: "주변 코스 찾기"는 지도 bounds로 목록을 가져오는 주요 동작이라 텍스트
            라벨이 있는 pill 버튼으로, "검색"은 이름/태그 검색창을 여는 보조 동작이라 아이콘
            전용 FAB로 남겨 서로 다른 동작임을 형태로 구분한다. 시트가 열려 있는 동안도 사라지지
            않고 시트 상단 위치를 따라간다. */}
        <Animated.View style={[styles.bottomLeftButtons, { bottom: searchButtonBottom }]}>
          <TouchableOpacity
            style={[styles.nearbyCoursesButton, isLoadingCourses && styles.nearbyCoursesButtonDisabled]}
            onPress={handleOpenCourseSearch}
            disabled={isLoadingCourses}
            activeOpacity={0.8}
          >
            <Ionicons
              name="navigate"
              size={16}
              color={isLoadingCourses ? Colors.gray400 : Colors.white}
            />
            <Text
              style={[
                styles.nearbyCoursesButtonLabel,
                isLoadingCourses && styles.nearbyCoursesButtonLabelDisabled,
              ]}
            >
              주변 코스 찾기
            </Text>
          </TouchableOpacity>
          <FAB
            icon="search"
            onPress={() => setIsSearchOpen(true)}
            color={Colors.gray500}
          />
        </Animated.View>

        {/* 우측: 코스 탐색 시트가 닫혀 있을 때는 기존 '내 경로' 도구 모음, 열려 있을 때는
            위치 + 후기 작성/목록 버튼으로 전환. 시트가 열린 동안은 '내 경로' 도구가 맥락에
            안 맞으므로 숨긴다. */}
        {!isCourseSheetOpen && (
          <View style={styles.floatingButtons}>
            <FAB icon="locate" onPress={handleLocate} />
            {!isBrowseMode && (
              <>
                <FAB
                  icon={isPedestrianRouteEnabled ? 'walk' : 'walk-outline'}
                  onPress={togglePedestrianRoute}
                  color={isPedestrianRouteEnabled ? Colors.primary : Colors.gray400}
                />
                <FAB
                  icon="arrow-undo"
                  onPress={handleUndo}
                  disabled={waypoints.length === 0 || isRouting}
                />
                <FAB icon="save-outline" onPress={handleOpenSaveModal} disabled={!canSave} />
                <FAB
                  icon="trash-outline"
                  onPress={handleClear}
                  disabled={waypoints.length === 0 || isRouting}
                  color={Colors.danger}
                />
              </>
            )}
          </View>
        )}

        {isCourseSheetOpen && (
          <Animated.View style={[styles.floatingButtons, { bottom: searchButtonBottom }]}>
            <FAB icon="locate" onPress={handleLocate} />
            <FAB
              icon="create-outline"
              onPress={handlePressWritePost}
              disabled={!selectedCourseDetail}
            />
            <FAB
              icon="list-outline"
              onPress={handlePressCourseBoard}
              disabled={!selectedCourseDetail}
            />
          </Animated.View>
        )}

        <CourseSearchSheet
          ref={courseSheetRef}
          visible={isCourseSheetOpen}
          courses={nearbyCourses}
          isLoading={isLoadingCourses}
          selectedCourseId={selectedCourseId}
          onSelectCourse={handleSelectCourse}
          onViewDetail={handleViewCourseDetail}
          onClose={handleCloseCourseSearch}
          onCollapsedChange={setIsCourseSheetCollapsed}
          onContentHeightChange={(height) => {
            sheetContentHeightRef.current = height;
          }}
        />
```

- [ ] **Step 7: 스타일 추가**

`mobile/src/screens/MapScreen.tsx`의 현재 `floatingButtons` 스타일 블록:
```ts
  floatingButtons: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    gap: 10,
    alignItems: 'center',
  },
```
바로 뒤에 다음 스타일들을 추가한다:
```ts
  bottomLeftButtons: {
    position: 'absolute',
    left: 16,
    gap: 10,
    alignItems: 'flex-start',
  },
  nearbyCoursesButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 44,
    paddingHorizontal: 16,
    borderRadius: 22,
    backgroundColor: Colors.primary,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 4,
    elevation: 4,
  },
  nearbyCoursesButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  nearbyCoursesButtonLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.white,
  },
  nearbyCoursesButtonLabelDisabled: {
    color: Colors.gray400,
  },
```

(기존 `fab` 스타일이 44×44/borderRadius 22이므로 `nearbyCoursesButton`도 같은 높이·둥근 정도로
맞췄다 — 원본 498c410은 46×46이었지만, 기존 FAB들을 건드리지 않기 위해 현재 크기에 맞춘다.)

`Colors.gray100`이 `mobile/src/constants/theme.ts`에 이미 정의되어 있는지 확인한다:

Run: `grep -n "gray100" mobile/src/constants/theme.ts`
Expected: 최소 한 줄 출력 (이미 다른 화면에서 쓰이고 있어 존재할 것).

- [ ] **Step 8: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없이 종료. 특히 `getPublicCourses` 호출부에서 `limit` 생략이 `GetPublicCoursesParams`
타입과 맞는지(옵셔널), `CourseSearchSheet`/`CourseSearchSheetRef`/`SHEET_HANDLE_HEIGHT` import
경로가 맞는지 확인한다.

- [ ] **Step 9: Expo 번들 확인**

Run: `cd mobile && npx expo start &` 후 `curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: `200`

- [ ] **Step 10: 실기기/시뮬레이터 수동 확인**

다음을 직접 확인한다 (자동화된 테스트가 없으므로 필수):
- Map 화면 진입 → 좌하단에 "주변 코스 찾기" pill 버튼과 검색 FAB가 나란히 보임
- "주변 코스 찾기" 탭 → 바텀시트가 열리고 현재 지도 범위의 공개 코스 목록이 표시됨 (없으면
  "주변에 표시된 코스가 없습니다")
- 목록에서 코스 하나 탭 → 지도에 주황 점선으로 경로 미리보기 표시, 우측에 위치/글쓰기/목록
  FAB로 전환, 글쓰기/목록 버튼이 활성화됨
- "상세 보기"(목록 항목 재탭 또는 시트 내 버튼) → 카메라가 해당 코스로 이동하고 경로 순서
  번호 핀 표시
- 글쓰기 버튼 → 로그인 안 되어 있으면 로그인 유도, 로그인 상태면 `PostCreate` 화면에 코스가
  첨부된 채로 진입
- 목록 버튼 → `CourseBoard` 화면으로 이동, 해당 코스로 필터링된 게시글 목록 표시
- 시트 닫기 → 미리보기 폴리라인/핀 사라짐, 우측 버튼이 원래 도구 모음(위치/보행로/undo/저장/
  삭제)으로 복귀
- 회귀 없음 확인: 이름/태그 검색(검색 FAB), 보행로 토글, 지도 탭으로 내 경로 그리기/undo/저장/
  삭제가 기존과 동일하게 동작

- [ ] **Step 11: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/restore-map-search-review-buttons
git add mobile/src/screens/MapScreen.tsx
git commit -m "$(cat <<'EOF'
fix(mobile): Map 화면 주변 코스 찾기 바텀시트 복구

병합 커밋 5e9c849에서 main 쪽 코드가 codex/mobile-map 구버전으로
조용히 대체되며 사라진 기능이다. 병합 전 main 구현(498c410)을
그대로 이식하되, getCourses(bounds) 대신 이미 존재하는
getPublicCourses(params)를 재사용해 중복 API 함수를 만들지 않았다.
그 사이 추가된 이름/태그 검색, 보행로 토글, isBrowseMode는 그대로
유지된다.
EOF
)"
```

---

## Task 3: CourseDetailScreen — 후기 작성/목록 버튼 복구

**Files:**
- Modify: `mobile/src/screens/CourseDetailScreen.tsx`

**Interfaces:**
- Consumes: 기존 `useAuthGate().requireAuth`, 기존 `navigation.navigate('PostCreate', {...})`/
  `navigation.navigate('CourseBoard', {...})`, 컴포넌트 최상단의 `course: Course` state (로딩
  가드 이후 항상 존재)
- Produces: 없음 (화면 컴포넌트, 최종 소비자). Task 1/2와 독립적 — 병렬로 진행 가능.

- [ ] **Step 1: 핸들러 추가**

`mobile/src/screens/CourseDetailScreen.tsx:156-166`의 현재 코드:
```ts
  const handleExport = async () => {
    if (!course) return;
    setIsExporting(true);
    try {
      await exportGpx(course.path, course.title);
    } catch (e: unknown) {
      Alert.alert('내보내기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsExporting(false);
    }
  };
```
바로 뒤에 다음 핸들러 2개를 추가한다:
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

(`!course` 체크는 타입 내로잉용 방어 코드다 — 이 화면은 로딩 실패 시 이미 `goBack()`하므로
렌더링 시점엔 `course`가 항상 존재한다.)

- [ ] **Step 2: 지도 컨테이너에 FAB 2개 추가**

`mobile/src/screens/CourseDetailScreen.tsx:385-387`의 현재 코드:
```tsx
      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />
      </View>
```
다음으로 교체:
```tsx
      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />
        <View style={styles.floatingButtons}>
          <FAB icon="create-outline" onPress={handlePressWriteReview} />
          <FAB icon="list-outline" onPress={handlePressReviewBoard} />
        </View>
      </View>
```

- [ ] **Step 3: 로컬 `FAB` 컴포넌트 추가**

`mobile/src/screens/CourseDetailScreen.tsx`의 컴포넌트 함수 종료 지점, 즉:
```tsx
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
```
을 다음으로 교체 (`FAB` 컴포넌트 정의를 `export default function CourseDetailScreen` 함수와
`const styles = ...` 사이에 삽입):
```tsx
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

interface FABProps {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
}

function FAB({ icon, onPress }: FABProps) {
  return (
    <TouchableOpacity style={styles.fab} onPress={onPress} activeOpacity={0.8}>
      <Ionicons name={icon} size={20} color={Colors.primary} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
```

- [ ] **Step 4: `mapContainer`에 `position: relative` 추가 + `floatingButtons`/`fab` 스타일 추가**

`mobile/src/screens/CourseDetailScreen.tsx:565-567`의 현재 코드:
```ts
  mapContainer: {
    height: 260,
  },
```
다음으로 교체:
```ts
  mapContainer: {
    height: 260,
    position: 'relative',
  },
  floatingButtons: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    gap: 10,
  },
  fab: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 4,
    elevation: 4,
  },
```

(`MapScreen.tsx`의 `fab` 스타일과 동일한 값 — 두 화면 다 44×44 원형 흰 배경 FAB로 시각적
일관성을 유지한다. 공용 컴포넌트로 추출하지는 않는다 — 원래 설계 문서에서도 사용처가 2곳뿐이라
YAGNI로 보류한 항목이고, 이번에도 3번째 사용처가 생기는 게 아니므로 그대로 따른다.)

- [ ] **Step 5: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없이 종료.

- [ ] **Step 6: Expo 번들 확인**

Run: `cd mobile && curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/index.bundle?platform=ios&dev=true"`
(Task 2 Step 9에서 이미 `expo start`가 떠 있다면 그대로 사용, 아니면 먼저 백그라운드로 기동)
Expected: `200`

- [ ] **Step 7: 실기기/시뮬레이터 수동 확인**

- 저장한 코스 목록(내 코스/북마크) → 코스 탭 → 상세 화면 진입 시 지도 우측에 글쓰기/목록
  버튼이 바로 활성 상태로 보임
- 글쓰기 버튼 → 로그인 안 되어 있으면 로그인 유도, 로그인 상태면 `PostCreate`에 현재 코스가
  첨부된 채로 진입 (제목 prefill 확인)
- 목록 버튼 → `CourseBoard`로 이동, 해당 코스로 필터링된 게시글 목록 확인
- 회귀 없음 확인: 좋아요/북마크, 댓글 작성/수정/삭제/대댓글, GPX 내보내기 기존과 동일하게 동작

- [ ] **Step 8: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/restore-map-search-review-buttons
git add mobile/src/screens/CourseDetailScreen.tsx
git commit -m "$(cat <<'EOF'
fix(mobile): 코스 상세 화면 후기 작성/목록 버튼 복구

병합 커밋 5e9c849에서 handlePressWriteReview/handlePressReviewBoard
핸들러와 버튼이 통째로 사라졌던 것을 원래 설계(PR#27) 그대로
복구한다. PostCreate/CourseBoard 라우트와 파라미터는 기존 그대로
재사용 — API/데이터 모델 변경 없음.
EOF
)"
```

---

## Task 4: 구현 기록 문서화

**Files:**
- Create: `mobile/docs/implementations/restore-map-search-and-review-buttons.md`

**Interfaces:**
- Consumes: Task 1~3에서 변경된 내용 요약
- Produces: 없음 (문서만 생성, 이 저장소 관례상 기능 완료 시 필수 — `mobile/CLAUDE.md` "변경 후
  검증" 3번)

- [ ] **Step 1: 구현 기록 문서 작성**

`mobile/docs/implementations/restore-map-search-and-review-buttons.md` 새로 생성:

```markdown
# 병합 회귀로 소실된 기능 복구 (주변 코스 검색 · 후기 버튼)

## 문제

병합 커밋 `5e9c849`(`Merge branch 'refs/heads/codex/mobile-map'`, 2026-07-08)가 PR 리뷰 없이
main에 직접 push되면서, `MapScreen.tsx`와 `CourseDetailScreen.tsx`에서 main 쪽 코드가 22개
충돌 파일 중 하나로 처리되며 조용히 `codex/mobile-map` 구버전으로 대체됐다. 그 결과 "주변 코스
찾기" 바텀시트와 코스별 후기 작성/목록 버튼이 화면에서 사라졌다. 자세한 원인 분석은
`docs/superpowers/specs/2026-07-11-merge-regression-recovery-design.md` 참고.

## 복구 내용

- `KakaoMapView.tsx`: `getBounds`/`previewCourse`/`showCourseWaypoints`/`clearCoursePreview`
  4개 ref 메서드와 대응 WebView 메시지 핸들러를 병합 전 main(`498c410`) 구현 그대로 복구
- `MapScreen.tsx`: "주변 코스 찾기" pill 버튼, `CourseSearchSheet` 연결, 코스 선택 시
  뜨는 글쓰기/목록 FAB 복구. `getCourses(bounds)`는 되살리지 않고 기존 `getPublicCourses`를
  재사용
- `CourseDetailScreen.tsx`: `handlePressWriteReview`/`handlePressReviewBoard` 핸들러와
  우측 FAB 2개 복구 (원래 설계 PR#27 그대로)

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200
- 실기기/시뮬레이터: Map 화면 주변 코스 찾기 → 바텀시트 → 코스 선택 → 미리보기 → 상세 보기
  → 글쓰기/목록 진입 확인. CourseDetail 화면 후기 작성/목록 버튼 즉시 활성 확인. 기존 기능
  (이름/태그 검색, 보행로 토글, 내 경로 그리기, 좋아요/북마크/댓글) 회귀 없음 확인
```

- [ ] **Step 2: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/restore-map-search-review-buttons
git add mobile/docs/implementations/restore-map-search-and-review-buttons.md
git commit -m "$(cat <<'EOF'
docs(mobile): 병합 회귀 복구 작업 기록 추가
EOF
)"
```

- [ ] **Step 3: PR 생성**

main에 branch protection이 걸려 있으므로 PR을 통해서만 병합 가능하다.

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/restore-map-search-review-buttons
git push -u origin fix/restore-map-search-review-buttons
gh pr create --title "fix(mobile): 병합 회귀로 사라진 주변 코스 검색·후기 버튼 복구" --body "$(cat <<'EOF'
## Summary
- 병합 커밋 5e9c849가 PR 리뷰 없이 main에 직접 push되며 조용히 되돌아간 두 기능을 복구
- Map 화면 "주변 코스 찾기" 바텀시트 (KakaoMapView 4개 메서드 + MapScreen 연결)
- CourseDetailScreen 후기 작성/목록 버튼

## 설계 문서
docs/superpowers/specs/2026-07-11-merge-regression-recovery-design.md

## Test plan
- [x] npx tsc --noEmit
- [x] Expo 번들 HTTP 200
- [ ] 실기기/시뮬레이터: 주변 코스 찾기 → 바텀시트 → 미리보기 → 상세 보기 → 글쓰기/목록 확인
- [ ] 실기기/시뮬레이터: CourseDetail 후기 작성/목록 버튼 확인
- [ ] 기존 기능(이름/태그 검색, 보행로 토글, 좋아요/북마크/댓글) 회귀 없음 확인
EOF
)"
```

---

## Self-Review Notes

- **Spec coverage**: spec의 "변경 1~4"(KakaoMapView, courseApi, MapScreen, CourseDetailScreen)와
  "완료 후 문서화"가 각각 Task 1~4에 대응한다. "범위 밖"(isBrowseMode, 공용 컴포넌트 추출)은
  어떤 Task에도 포함하지 않았다.
- **Type consistency**: `getBounds(): Promise<GeoBounds>`, `previewCourse(path: RoutePoint[])`,
  `showCourseWaypoints(waypoints: RoutePoint[])`, `clearCoursePreview()` — Task 1에서 정의한
  시그니처를 Task 2에서 그대로 호출한다 (`mapRef.current.getBounds()`,
  `mapRef.current?.previewCourse(course.path)`,
  `mapRef.current?.showCourseWaypoints(selectedCourseDetail.waypoints)`,
  `mapRef.current.clearCoursePreview()`) — 이름/인자 타입 일치 확인함.
- **테스트 러너 부재**: 모든 Task의 검증 단계를 jest 테스트 대신 `tsc --noEmit` + Expo 번들
  200 + 명시적 수동 확인 체크리스트로 대체했다 (Global Constraints에 명시).
