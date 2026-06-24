@AGENTS.md

# 코드 규칙 및 가드레일 (React Native / Expo)

Expo SDK 버전 고정과 과거에 겪은 breaking change는 `AGENTS.md`를 보세요. 전역 React/TypeScript
스타일 규칙(`~/.claude/rules/react/*`, `typescript/*`)은 항상 적용되므로 여기서는 반복하지 않고,
이 프로젝트(runvas/mobile)에만 해당하는 규칙만 적습니다.

## 데이터 모델 — docs가 source of truth

- 좌표는 항상 `{ latitude, longitude }` 전체 이름을 쓴다. `lat`/`lng` 같은 축약 금지.
- 거리는 **meters**, 시간 길이는 **seconds**로만 상태에 보관한다. 분·시간으로 변환하는 건
  표시 컴포넌트(`RouteStatsBar.tsx`)의 포맷 함수 안에서만 — hook이나 상태에 분 단위를 들고
  있지 않는다.
- `Course`/`RoutePoint`/`GeoBounds` 등 저장·전송용 타입은 `src/types/index.ts`에서만 정의하고
  `runvas/docs/data-model.md`, `docs/api-contract.md`와 필드명·타입이 1:1로 일치해야 한다.
  이 타입을 바꿔야 하면 먼저 `docs/`를 고치고 와야 한다 (`docs/collaboration.md`의 변경 원칙).
- UI 전용 좌표(`Coordinate`)와 저장용 좌표(`RoutePoint`, `sequence` 포함)를 구분해서 쓴다.
  `RoutePoint`가 필요한 자리에 `Coordinate`를 그냥 캐스팅해서 넣지 않는다.

## 네이티브 모듈 / 패키지

- 네이티브 의존성은 `npm install`이 아니라 `npx expo install <package>`로 설치한다.
- `expo-file-system`은 새 `File`/`Paths`/`Directory` 클래스 API만 쓴다. `expo-file-system/legacy`,
  `writeAsStringAsync`, `cacheDirectory` 사용 금지 (`AGENTS.md` "이미 겪은 SDK 버전 변경" 참고 —
  SDK 54에서 런타임 에러).
- `app.json`의 `plugins` 배열에는 실제 config plugin이 있는 패키지만 넣는다(예: `expo-location`).
  config plugin이 없는 패키지(`expo-sharing` 등)를 넣으면 Expo CLI가 죽는다 — 패키지 추가 전에
  공식 문서에서 config plugin 여부를 확인한다.

## WebView ↔ RN 통신 (KakaoMapView 패턴)

- RN ↔ WebView 메시지는 항상 JSON으로 직렬화한 `postMessage`/`onMessage`로 주고받는다
  (`src/components/KakaoMapView.tsx` 참고). `injectJavaScript`로 직접 JS를 주입하는 방식은
  최후의 수단으로만 쓰고, 쓸 경우 주입 스크립트 끝에 반드시 `; true;`를 붙인다 (안 붙이면
  Android에서 실패할 수 있음).
- 메시지 타입은 문자열 상수(`'ADD_WAYPOINT'`, `'MOVE_TO'` 등)로 명시하고, 새 메시지 타입을
  추가할 때는 `KakaoMapViewRef` 인터페이스와 HTML `<script>` 내부 핸들러를 항상 같이 수정한다.

## 컴포넌트 / 훅

- 함수형 컴포넌트만 사용한다. `forwardRef`+`useImperativeHandle`은 부모가 자식의 명령형 API를
  호출해야 할 때만 쓴다 (지도 제어처럼 ref가 꼭 필요한 경우).
- 경로/통계 관련 계산은 `useRoute.ts`처럼 전용 hook에 모으고 컴포넌트 안에서 직접 계산하지 않는다.
- 외부 상태 라이브러리(Redux, Zustand 등)는 도입하지 않는다 — 화면 간 공유 상태가 실제로
  필요해지기 전까지는 `useState`/`useCallback`/`useMemo`로 충분하다 (YAGNI).

## 환경 변수 / 키

- API 키와 베이스 URL은 항상 `EXPO_PUBLIC_*` 환경변수로만 읽는다. 하드코딩 금지.
- `.env`는 절대 커밋하지 않는다 (`.gitignore`에 이미 포함). 새 환경변수를 추가하면
  `.env.example`에도 빈 값으로 같이 추가한다.

## 권한이 필요한 네이티브 기능 추가 시

1. `app.json`의 `ios.infoPlist`(설명 문구) / `android.permissions`(권한 코드)를 같이 추가한다.
2. 해당 패키지의 config plugin이 있으면 `plugins`에 등록한다.
3. 실기기/시뮬레이터에서 권한 다이얼로그가 실제로 뜨는지 직접 확인한다 — 코드만 보고 통과시키지 않는다.

## 테스트

현재 jest 등 테스트 러너가 설정되어 있지 않다. 테스트가 필요해지면 새로 고민하지 말고
`~/dev-dnd/running-app`의 `jest-expo` + `@testing-library/react-native` 구성을 그대로 가져온다
(이미 같은 사용자의 다른 러닝 앱에서 검증된 설정).

## 변경 후 검증 (매번)

1. `npx tsc --noEmit`
2. `npx expo start` 백그라운드로 띄운 뒤 `curl ".../index.bundle?platform=ios&dev=true"`로
   HTTP 200 확인 (또는 실기기/시뮬레이터에서 직접 동작 확인). `tsc`만으로는 잡히지 않는 런타임
   크래시(예: config plugin 리졸브 실패)가 실제로 있었다 — `WORKLOG.md` Phase 4 참고.
3. 기능을 하나 끝내면 `mobile/docs/implementations/{slug}.md`에 설계 과정과 사용한 스킬/훅을
   기록한다 (사용자와 합의된 5단계 작업 프로세스).
