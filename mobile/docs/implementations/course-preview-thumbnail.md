# 코스 미리보기 썸네일 구현

## 작업 개요

저장한 코스 목록(`SavedRoutesScreen`)에서 각 코스를 탭하기 전에 경로를 시각적으로 미리 볼 수
있도록 SVG 경로 스케치 썸네일을 추가하고, 썸네일 탭 시 반화면 바텀시트로 상세 지도를 표시하는
기능을 구현했다.

---

## 주요 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `src/components/CourseRouteSvg.tsx` | 신규 — SVG 경로 스케치 썸네일 컴포넌트 |
| `src/components/CoursePreviewModal.tsx` | 전체화면 Modal → 반화면 바텀시트로 변경 |
| `src/screens/SavedRoutesScreen.tsx` | CourseMapThumbnail → CourseRouteSvg 교체 |
| `package.json` | react-native-svg 추가 |

삭제된 파일:
- `src/components/CourseMapThumbnail.tsx` (Kakao Static Map API 의존, 유료 승인 필요)
- `src/utils/mapUtils.ts` (Static Map URL 생성 유틸, 더 이상 사용하지 않음)

---

## CourseRouteSvg 컴포넌트

### 역할

`CourseSummary`에는 `path` 필드가 없으므로 마운트 시 `getCourse(courseId)`를 호출해 전체
`Course`를 가져온 뒤 `RoutePoint[]`를 SVG `Polyline`으로 렌더링한다.

### 좌표 정규화 (`normalizePoints`)

```
x = PADDING + ((lng - minLng) / lngSpan) * drawSize
y = PADDING + ((maxLat - lat) / latSpan) * drawSize   ← latitude는 위로 갈수록 크므로 y 반전
```

padding = 6px, drawSize = size - 2 * PADDING.

### 상태 흐름

```
마운트 → isLoading=true → getCourse() 호출
  성공 → path 저장 → Svg Polyline 렌더
  실패 → hasError=true → map-outline 아이콘 fallback
```

race condition 방지: `active` 플래그로 언마운트 후 setState 차단.

---

## CoursePreviewModal 변경사항

### 전: 전체화면 슬라이드 모달

```tsx
<Modal animationType="slide">
  <SafeAreaView style={{ flex: 1 }}>...</SafeAreaView>
</Modal>
```

### 후: 반화면 바텀시트

```tsx
<Modal animationType="slide" transparent>
  <TouchableWithoutFeedback onPress={onClose}>
    <View style={styles.overlay} />   {/* 반투명 오버레이, 탭으로 닫기 */}
  </TouchableWithoutFeedback>
  <View style={styles.sheet}>        {/* 화면 55% 높이, 하단 고정 */}
    <View style={styles.handle} />    {/* 드래그 핸들 */}
    ...
  </View>
</Modal>
```

- 오버레이 탭으로 닫기 지원
- `useSafeAreaInsets`로 하단 safe area 처리
- 드래그 핸들 표시 (UX 힌트)

---

## 결정 사항 기록

### Kakao Static Map API 미사용 이유

`dapi.kakao.com/v2/maps/staticmap`은 JS Key(401)·REST Key(403 NotAuthorizedError) 모두
React Native 환경에서 동작하지 않았으며, 확인 결과 별도 심사·승인이 필요한 유료 API임이
확인됐다.

### CourseDetail 분리 유지 결정

미리보기 바텀시트와 CourseDetail 화면을 통합하자는 논의가 있었으나, 향후 편집·공유·러닝 시작·
통계 등 액션이 CourseDetail에 추가될 것을 가정해 두 화면의 역할을 명확히 분리 유지했다.

- **바텀시트**: 빠른 지도 미리보기 전용
- **CourseDetail**: 액션 허브 (GPX 다운로드 등 현재 기능 + 향후 확장)

### react-native-svg 선택 이유

Expo SDK 54 호환, config plugin 불필요, SVG Polyline 렌더링에 필요한 최소 API만 사용.
`npx expo install react-native-svg`로 SDK 호환 버전 자동 선택.

---

## 검증

- `npx tsc --noEmit`: 에러 없음
- 삭제된 `CourseMapThumbnail`, `mapUtils`의 잔여 import 없음 확인

---

## 성능 최적화 (N1 + N2)

### 개선 전 문제점

| 문제 | 설명 |
|---|---|
| N+1 API 호출 | 목록에 N개 코스가 있으면 마운트 시 N개의 `getCourse()` 호출이 동시에 발생 |
| 중복 호출 | 썸네일이 이미 로드한 courseId에 대해 `CoursePreviewModal` 열 때 동일 API 재호출 |
| 일괄 렌더 | FlatList가 모든 아이템을 한 번에 렌더 → API 호출이 한꺼번에 집중됨 |

### N1: 경로 데이터 캐시 (`src/services/courseCache.ts`)

**구현 방식**
- 모듈 레벨 `Map<string, Course>` (결과 캐시) + `Map<string, Promise<Course>>` (진행 중 요청 dedup)
- `getCachedCourse(courseId, accessToken)`: 캐시 hit → 즉시 반환, in-flight 중복 → 같은 Promise 공유, 없으면 신규 fetch 후 캐시 저장
- `evictCourse(courseId)`: 코스 삭제 시 캐시 제거

**개선 전 vs 후 (코스 4개, getCourse 평균 응답 시간 300ms 가정)**

| 시나리오 | 개선 전 | 개선 후 |
|---|---|---|
| 목록 첫 진입 | 4개 동시 API 호출 (~300ms) | 4개 동시 API 호출 (~300ms) — 동일 |
| 동일 courseId로 미리보기 열기 | API 재호출 (~300ms) | 캐시 hit (~0ms) |
| 동일 courseId를 여러 컴포넌트에서 동시 요청 | N회 중복 fetch | 1회 fetch + N-1회 동일 Promise 공유 |
| �självklart 재마운트 후 미리보기 열기 | API 재호출 (~300ms) | 캐시 hit (~0ms) |

핵심 이득: **미리보기를 열 때마다 발생하던 ~300ms 지연이 캐시 hit 시 ~0ms로 단축.**

주의사항: 캐시는 앱 세션 메모리에만 존재. 서버에서 코스 내용이 수정된 경우 stale할 수 있으나 MVP 범위에서는 세션 내 불변으로 간주.

---

### N2: FlatList 렌더 배치 최적화 (`SavedRoutesScreen.tsx`)

**추가한 props**

```tsx
<FlatList
  initialNumToRender={4}    // 초기 렌더 아이템 수 (기본값 10 → 4)
  maxToRenderPerBatch={2}   // 스크롤 시 한 번에 렌더할 추가 아이템 수
  windowSize={5}            // 뷰포트 5배 범위만 메모리에 유지 (기본값 21)
/>
```

**개선 전 vs 후**

| 지표 | 개선 전 | 개선 후 |
|---|---|---|
| 초기 마운트 시 렌더 아이템 수 | 최대 10개 (기본값) | 최대 4개 |
| 초기 마운트 시 동시 API 호출 수 | 최대 10개 | 최대 4개 |
| 스크롤 중 추가 렌더 배치 크기 | 10개씩 | 2개씩 |
| 메모리에 유지되는 아이템 범위 | 뷰포트 21배 | 뷰포트 5배 |

현재 목록 규모(4~10개) 기준에서는 N1 캐시가 더 직접적인 효과를 낸다. 목록이 10개 이상으로 늘어날 때 초기 API 호출 집중 억제 효과가 명확해진다.

---

### 성능 개선 관련 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `src/services/courseCache.ts` | 신규 — 모듈 레벨 인메모리 캐시 + 요청 dedup |
| `src/components/CourseRouteSvg.tsx` | `getCourse` → `getCachedCourse` |
| `src/components/CoursePreviewModal.tsx` | `getCourse` → `getCachedCourse` |
| `src/screens/SavedRoutesScreen.tsx` | `evictCourse` 추가, FlatList 배치 props 추가 |
