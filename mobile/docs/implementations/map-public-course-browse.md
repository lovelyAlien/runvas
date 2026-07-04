# 지도 화면 공개 코스 탐색

## 배경 및 목적

MVP 범위에 "지도 범위 기반 코스 탐색"이 포함되어 있으나, 기존 MapScreen은 경로 그리기 전용이었다.
다른 사용자의 공개 코스를 지도 위 마커로 탐색하고 상세 화면으로 진입할 수 있도록 둘러보기 모드를 추가했다.

## 구현된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `src/services/courseApi.ts` | `getPublicCourses(params, accessToken?)` 추가 |
| `src/components/KakaoMapView.tsx` | 공개 코스 마커/말풍선, bounds 이벤트, SET_BROWSE_MODE |
| `src/screens/MapScreen.tsx` | 둘러보기 모드 토글, 공개 코스 fetch, 상세 화면 이동 |

## 아키텍처 결정

### WebView 메시지 패턴으로 모드 제어

둘러보기 모드 전환 시 HTML을 재생성하지 않고 `SET_BROWSE_MODE` 메시지를 전송한다.
HTML 재생성 시 지도가 초기 좌표로 리셋되고 기존 그리기 경로가 사라지는 문제가 있다.

### 말풍선 "자세히 보기" 이중 이스케이프

WebView HTML 내 인라인 onclick에서 `onCourseMarkerClick('id', 'title', lat, lng)` 형태로 호출한다.
title의 작은따옴표 이스케이프(`\\'`)와 템플릿 리터럴 이스케이프(`\\`)가 중첩되므로 주의가 필요하다.

### 마커 위치는 bounds 중심점

`CourseSummary`에는 `path`가 없으므로 코스의 대표 좌표를 `bounds`의 중심점 `(sw + ne) / 2`로 계산한다.

### 비로그인 탐색 허용

`GET /api/courses`는 Auth: Optional이다. `accessToken`이 없어도 조회 가능하며, 로그인 시 `likedByMe` 필드가 채워진다.

## 새 WebView 메시지 타입

| 방향 | 타입 | payload |
|------|------|---------|
| WebView → RN | `MAP_BOUNDS_CHANGE` | `{ swLat, swLng, neLat, neLng }` |
| WebView → RN | `COURSE_MARKER_PRESS` | `{ courseId: string }` |
| RN → WebView | `SHOW_PUBLIC_COURSES` | `{ courses: PublicCourseMarker[] }` |
| RN → WebView | `CLEAR_PUBLIC_COURSES` | 없음 |
| RN → WebView | `SET_BROWSE_MODE` | `{ enabled: boolean }` |

## UX 흐름

1. 사용자가 우측 FAB의 돋보기 아이콘 탭 → 둘러보기 모드 진입
2. 지도 idle 이벤트 → `MAP_BOUNDS_CHANGE` → 1초 디바운스 후 `getPublicCourses` 호출
3. 응답 courses → `showPublicCourses` → 주황색 마커 표시
4. 마커 탭 → 말풍선(제목 + "자세히 보기") 표시
5. "자세히 보기" 탭 → `COURSE_MARKER_PRESS` → `CourseDetailScreen` 이동
6. 돋보기(활성) 탭 → 그리기 모드 복귀, 마커 전부 제거

## 수동 테스트 방법 (시뮬레이터 기준)

### 사전 조건

- 백엔드 실행 중 (`./gradlew bootRun`)
- `EXPO_PUBLIC_API_BASE_URL` 설정됨
- DB에 `visibility = 'PUBLIC'`인 코스가 1개 이상 존재

### 기본 흐름

1. 앱 실행 → 지도 탭 진입
2. 우측 하단 FAB에서 돋보기(🔍) 버튼 탭
3. 버튼 색상이 파란색으로 변하고 그리기 버튼(undo, save, trash)이 사라지는지 확인
4. 공개 코스가 있는 지역으로 지도 이동 → 1초 후 주황색 마커 표시 확인
5. 주황색 마커 탭 → 말풍선(제목 + "자세히 보기") 노출 확인
6. "자세히 보기" 탭 → CourseDetailScreen 이동 확인
7. 뒤로가기 → 지도 복귀
8. 돋보기(활성) 버튼 재탭 → 그리기 모드 복귀, 마커 사라짐 확인

### 경계 케이스

| 시나리오 | 기대 동작 |
|----------|-----------|
| 공개 코스 없는 지역 이동 | 마커 없음, 오류 없음 |
| 비로그인 상태에서 둘러보기 | 탐색 가능 (로그인 불필요) |
| 빠른 지도 이동 연속 | 마지막 위치 기준 1번만 fetch |
| 백엔드 응답 오류 | 마커 없음 표시, Alert 없음 |
| 둘러보기 모드에서 지도 탭 | 경로 추가 안 됨 |

## 알려진 제약

- 마커 개수 최대 50개 (API limit 파라미터)
- 커서 페이지네이션 미구현 — 범위 내 코스가 50개 초과 시 일부만 표시
- 말풍선은 한 번에 하나만 표시 (다른 마커 탭 시 기존 말풍선 닫힘)
