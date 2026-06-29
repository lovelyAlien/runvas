$# Runvas Mobile — 작업 로그

## Phase 0 — 데모에서 가져온 것 / docs 계약에 맞춰 바꾼 것

`runvas-demo/`(검증된 참고 구현)를 포팅하면서 `runvas/docs/`(공통 계약) 기준으로 바꾼 부분입니다.
다음 세션은 "왜 데모와 다르게 짰는지" 다시 추론하지 말고 이 표를 먼저 확인하세요.

| 항목 | 데모 | 이번 구현 | 이유 |
| --- | --- | --- | --- |
| GPX `<trkpt>` | `<time>` 태그 포함 | `<time>` 태그 제거 | `docs/gpx-export.md`: 시간/고도/심박 등 MVP 제외 항목 |
| 경로 시간 단위 | `RouteStats.estimatedMinutes` (분) | `RouteStats.estimatedDurationSeconds` (초) | `docs/geo-conventions.md`: 시간 길이 단위 = seconds |
| 경로 좌표 타입 | `Coordinate[]`만 사용 | `Coordinate`(UI용) + `RoutePoint`(sequence 포함, 저장/전송용) 분리 | `docs/data-model.md` RoutePoint에 `sequence` 필수 |
| GPX 파일명 | 미사용(타임스탬프 없음) | `runvas-route-{timestamp}.gpx` | 로컬 전용 모드라 `courseId`가 없어 `docs/gpx-export.md`의 `{courseId}.gpx` 규칙을 100% 따를 수 없음. **백엔드 연동 후 `{courseId}.gpx`로 교체할 것** |
| `expo-file-system` import | `'expo-file-system/legacy'` | `File`/`Paths` 클래스 API | Phase 4에서 `/legacy` 서브패스도 SDK 54에서 런타임 에러를 던지는 것을 발견, `running-app` 검증 패턴으로 교체 (AGENTS.md 참고) |

## Phase 1 — Expo 스캐폴딩

### 완료 일자
2026-06-24

### 주요 작업

- `npx create-expo-app@latest . --template blank-typescript`로 `runvas/mobile/`에 생성
  (기존 `README.md`, `.omc/`는 임시로 옮긴 뒤 복원 — 스캐폴딩 도구가 빈 디렉토리만 허용)
- `npx expo install react-native-webview expo-location expo-file-system expo-sharing geolib @expo/vector-icons`
- 결과 버전: `expo ~56.0.12`, `react-native 0.85.3` (데모는 `expo ~54.0.0` — SDK 버전 드리프트 발생, AGENTS.md에 기록)
- `app.json`: iOS `NSLocationWhenInUseUsageDescription`, Android `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` 권한,
  `expo-location` 플러그인 설정 추가
- `.env.example` 작성: `EXPO_PUBLIC_KAKAO_APP_KEY`, `EXPO_PUBLIC_TMAP_APP_KEY`, `EXPO_PUBLIC_API_BASE_URL`
- `.gitignore`에 `.env` 추가 (기존엔 `.env*.local`만 있었음)

### 발견한 SDK 56 breaking change

- `expo-file-system`: `writeAsStringAsync` 등이 새 클래스 기반 API로 교체, 기존 함수는 런타임 에러.
  `expo-file-system/legacy`로 우회 (demo도 이미 이 방식 사용 중이었음 — 검증된 패턴).
- React Native 0.85: `StyleSheet.absoluteFillObject` 제거, `StyleSheet.absoluteFill`로 대체.

## Phase 2 — 타입/유틸/지도/위치/경로 그리기/GPX 내보내기

### 완료 일자
2026-06-24

### 주요 작업

- `src/types/index.ts`: `Coordinate`, `RoutePoint`, `GeoBounds`, `CourseVisibility`, `Course`, `RouteStats`,
  `CreateCourseRequestBody`, `ApiErrorBody` — `docs/data-model.md`, `docs/api-contract.md`와 1:1 대응
- `src/constants/theme.ts`, `src/utils/tmapRouting.ts`, `src/hooks/useLocation.ts`,
  `src/components/KakaoMapView.tsx`, `src/components/Header.tsx` — 데모 그대로 포팅
- `src/utils/gpx.ts`: `<time>` 태그 제거, `RoutePoint[]` 기반으로 시그니처 변경
- `src/utils/exportGpx.ts`: 시그니처 변경 반영, 파일명 규칙 변경
- `src/hooks/useRoute.ts`: `estimatedDurationSeconds`(초)로 변경, `toRoutePoints()`/`getBounds()` 파생 함수 추가
- `src/components/RouteStatsBar.tsx`: `formatTime(seconds)`로 변경
- `App.tsx`: 위 변경사항 연결, GPX 내보내기는 `toRoutePoints()` 결과 사용
- `npx tsc --noEmit` 통과

## Phase 3 — courseApi.ts (미래 연동 준비)

### 완료 일자
2026-06-24

### 주요 작업

- `src/services/courseApi.ts`: `buildCreateCourseRequest()`(순수 변환, 오늘도 동작) +
  `postCourse()`(완성된 POST 구현, `API_BASE_URL` 없으면 명확한 에러) 작성
- `App.tsx`에서는 import/호출하지 않음 — 백엔드 연동 체크리스트는 파일 상단 주석 참고

## Phase 4 — Expo SDK 56 → 54 다운그레이드

### 완료 일자
2026-06-24

### 배경

Phase 1에서 `create-expo-app`이 그 시점 최신인 SDK 56을 받아왔는데, 사용자의 안드로이드 기기에 설치된
(최신) Expo Go 앱이 아직 SDK 56을 지원하지 않아 "Project is incompatible with this version of Expo Go"
에러가 발생. 사용자가 `~/dev-dnd/running-app/`(별도 러닝 앱 프로젝트, SDK 54)와 버전을 맞춰달라고 요청.
`runvas-demo/`도 SDK 54라서 두 프로젝트 모두 SDK 54로 검증되어 있음을 확인 후 다운그레이드 결정.

### 주요 작업

- `package.json`: `expo ~56.0.12` → `~54.0.0`, `react-native 0.85.3` → `0.81.5`, `react` `19.2.3` → `19.1.0`,
  `expo-file-system`/`expo-location`/`expo-sharing`/`expo-status-bar`/`react-native-webview`/
  `@expo/vector-icons`/`typescript`/`@types/react`를 모두 `runvas-demo`·`running-app`과 동일한
  버전으로 고정
- `node_modules`/`package-lock.json` 삭제 후 재설치, `npx expo install --fix`로 SDK 54 매니페스트와
  버전 정합성 재확인
- `app.json`: SDK 56 템플릿 전용 키 `predictiveBackGestureEnabled` 제거

### 다운그레이드 중 발견한 추가 문제 2건

1. **`expo-file-system/legacy`가 SDK 54에서도 런타임 에러**: AGENTS.md에 "SDK 56부터 legacy 서브패스로
   우회 가능"이라고 적어뒀던 게 틀렸음을 발견. `running-app/docs/implementations/export-download-share.md`에
   "legacyWarnings가 공개 API 호출 시 즉시 에러를 던지도록 바뀌었다"는 기록이 있었고, 실제로
   `running-app/src/components/ExportButtons.tsx`는 기본 캐시 쓰기+공유 흐름에 새 `File`/`Paths`
   클래스 API를 쓰고 있었음. `src/utils/exportGpx.ts`를 동일한 패턴으로 교체.
2. **`app.json`의 `plugins: ["expo-sharing", ...]`가 dev 서버 자체를 크래시시킴**: `expo-sharing`은
   config plugin이 없는데 plugins 배열에 들어가 있어서, Expo CLI가 plugin 여부 확인 중
   `expo-modules-core`의 TS 소스를 Node로 직접 import하려다 Node 24의
   `ERR_UNSUPPORTED_NODE_MODULES_TYPE_STRIPPING`에 걸려 `expo start`/`expo export`가 즉시 죽음.
   `runvas-demo`/`running-app` 둘 다 `expo-sharing`을 plugins에 넣지 않는다는 걸 확인하고 제거.

### 검증

- `npx tsc --noEmit` 통과
- `npx expo start --port 19101` 백그라운드 기동 후 `curl http://localhost:19101/index.bundle?platform=ios&dev=true` → `HTTP 200`, 정상 번들 응답 확인

## 미해결 / 다음 단계

- [ ] T-MAP/Kakao 키: 사용자가 `.env`에 직접 입력 (placeholder만 작성됨)
- [ ] 백그라운드 위치추적 미지원 (포그라운드만, 데모와 동일한 한계)
- [ ] `courseApi.ts`는 구현했지만 어디서도 호출하지 않음 — backend 완성 후 "저장" 버튼 추가 시 연결
- [ ] T-MAP API가 실제로 소요시간 필드를 주는지 미검증 (현재 6분/km 고정 가정 — 실제 키로 호출 후 다르면 이 표 갱신)
- [ ] 인증(로그인) 기능 없음 — `Authorization: Bearer` 헤더 미구현
