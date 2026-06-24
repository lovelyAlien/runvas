# Runvas Mobile — Agent Rules

## Expo 버전

현재 고정된 버전: **Expo SDK 54** (`expo ~54.0.0`, `react-native 0.81.5`).

`package.json`의 실제 버전이 이 숫자와 다르면, 코드를 작성하기 전에 이 파일을 먼저 갱신하고
`https://docs.expo.dev/versions/v<해당 버전>.0.0/`에서 정확한 버전의 공식문서를 확인하세요.
SDK 메이저 버전이 올라가면 네이티브 모듈 API가 자주 바뀝니다 (아래 "이미 겪은 변경" 참고).

**임의로 최신 SDK로 올리지 마세요.** `npx create-expo-app`은 기본적으로 그 시점의 최신 SDK(현재 56)를
받아오지만, 사용자 기기의 Expo Go 앱이 아직 그 SDK를 지원하지 않으면 "Project is incompatible with
this version of Expo Go" 에러가 납니다. SDK 54는 `runvas-demo/`와 `running-app/`(둘 다 사용자의 다른
러닝 앱 프로젝트, `~/dev-dnd/running-app/CLAUDE.md` 참고) 양쪽에서 이미 검증된 버전이므로 이 숫자를
유지하세요.

## 우선순위 규칙

- `../docs/`(`runvas/docs/`)가 백엔드·모바일 공통 계약의 source of truth입니다.
- `runvas-demo/`, `~/dev-dnd/running-app/`는 검증된 참고 구현입니다(둘 다 SDK 54). `docs/`와
  필드명·단위·포맷이 다른 부분이 있으면 `docs/`를 따르고, 두 프로젝트는 로직/버전 재사용 참고용으로
  취급하세요. 특히 `expo-file-system` 같은 네이티브 모듈 사용 패턴은 `running-app/src/components/
  ExportButtons.tsx`가 SDK 54에서 실제로 검증한 방식을 우선 따르세요(아래 참고).
- 네이티브 의존성(`react-native-webview` 등)은 `npm install`이 아니라
  `npx expo install <package>`로 설치해 SDK 호환 버전을 자동으로 맞추세요.

## 이미 겪은 SDK 버전 변경 (반복하지 말 것)

- **`expo-file-system` (SDK 54부터)**: `writeAsStringAsync`/`readAsStringAsync` 등 콜백형 API가
  새 `File`/`Directory`/`Paths` 클래스 기반 API로 교체되었습니다. **`expo-file-system/legacy`
  서브패스도 안전하지 않습니다** — `legacyWarnings`가 공개 API 호출 시 즉시 에러를 던지도록
  바뀌었습니다(`running-app/docs/implementations/export-download-share.md`에서 실제로 검증됨).
  캐시에 쓰고 공유하는 기본 흐름은 새 클래스 API를 쓰세요:
  ```ts
  import { File, Paths } from 'expo-file-system';
  const file = new File(Paths.cache, fileName);
  file.write(content);
  // file.uri를 Sharing.shareAsync에 전달
  ```
  (`src/utils/exportGpx.ts`가 이 방식 사용 중). `StorageAccessFramework` 같은 더 깊은 레거시 기능이
  필요할 때만 `expo-file-system/src/legacy/FileSystem`(공개 서브패스 아님, 내부 경로) import를
  고려하세요.
- **`StyleSheet.absoluteFillObject`**: SDK 54(`react-native 0.81.5`)에서는 여전히 존재하고,
  스프레드 가능한 plain object입니다. `absoluteFill`은 `RegisteredStyle`이라 스프레드하면
  타입 에러가 납니다. **RN 0.85+로 올릴 때만** `absoluteFillObject`가 사라지고 `absoluteFill`이
  plain object로 바뀌니, 버전 올릴 때마다 둘 중 어떤 게 스프레드 가능한지 다시 확인하세요.
- **`app.json`의 `plugins` 배열에 `expo-sharing`을 넣지 마세요.** `expo-sharing`은 config plugin이
  없는 패키지입니다. plugins에 넣으면 Expo CLI가 plugin 여부를 확인하려고 `expo-modules-core`의
  TypeScript 소스를 Node로 직접 import하는데, Node 22+의 `ERR_UNSUPPORTED_NODE_MODULES_TYPE_STRIPPING`
  제약에 걸려 `expo start`/`expo export`가 즉시 크래시합니다. `expo-location`처럼 실제 config plugin이
  있는 패키지만 `plugins`에 넣으세요.

## 경계 규칙

- mobile 관련 `.md` 파일(`CLAUDE.md`, `AGENTS.md`, `WORKLOG.md`)은 이 디렉토리(`runvas/mobile/`)
  안에만 작성하세요. `runvas/` 루트의 `README.md`나 `runvas/docs/` 문서는 백엔드·모바일 공통
  영역이므로 모바일 작업만으로 수정하지 않습니다.
- API 키(`EXPO_PUBLIC_KAKAO_APP_KEY`, `EXPO_PUBLIC_TMAP_APP_KEY`)와 `EXPO_PUBLIC_API_BASE_URL`은
  `.env`에만 두고 커밋하지 않습니다. `.env.example`만 갱신하세요.
