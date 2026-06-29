# Runvas Mobile — Agent Rules

## Expo 버전

현재 고정된 버전: **Expo SDK 54** (`expo ~54.0.0`, `react-native 0.81.5`).

`package.json`의 실제 버전이 이 숫자와 다르면, 코드를 작성하기 전에 이 파일을 먼저 갱신하고
`https://docs.expo.dev/versions/v<해당 버전>.0.0/`에서 정확한 버전의 공식문서를 확인하세요.
SDK 메이저 버전이 올라가면 네이티브 모듈 API가 자주 바뀝니다 (아래 "이미 겪은 변경" 참고).

**임의로 최신 SDK로 올리지 마세요.** SDK 54는 이미 검증된 버전이므로 이 숫자를 유지하세요.

## 이미 겪은 SDK 버전 변경 (반복하지 말 것)

- **`expo-file-system` (SDK 54부터)**: `writeAsStringAsync`/`readAsStringAsync` 등 콜백형 API가
  새 `File`/`Directory`/`Paths` 클래스 기반 API로 교체되었습니다. **`expo-file-system/legacy`
  서브패스도 안전하지 않습니다**. 캐시에 쓰고 공유하는 기본 흐름은 새 클래스 API를 쓰세요:
  ```ts
  import { File, Paths } from 'expo-file-system';
  const file = new File(Paths.cache, fileName);
  file.write(content);
  ```
- **`app.json`의 `plugins` 배열에 `expo-sharing`을 넣지 마세요.** config plugin이 없는 패키지입니다.
  `expo-location`처럼 실제 config plugin이 있는 패키지만 `plugins`에 넣으세요.

## 모바일 구현 원칙

- 화면과 사용자 흐름은 `../docs/product-scope.md`의 MVP 범위를 기준으로 구현합니다.
- API 요청과 응답 타입은 `../docs/api-contract.md`와 `../docs/data-model.md`를 기준으로 정의합니다.
- 지도 좌표, 거리, bounds, 경로 순서와 관련된 화면 또는 저장 요청은 `../docs/geo-conventions.md`를 따릅니다.
- GPX 다운로드 UX와 API 호출은 `../docs/gpx-export.md`를 기준으로 구현합니다.
- 서버에서 계산하거나 검증하는 값과 모바일에서 생성하는 값의 책임을 문서와 다르게 바꾸지 않습니다.
- API 필드를 바꿔야 한다면 모바일 코드를 바꾸기 전에 `../docs/`를 먼저 수정합니다.

## API 연동

- `../docs/api-contract.md`를 확인하지 않고 모바일만의 API 모델 가정을 추가하지 않습니다.
- 모바일 응답 타입은 `../docs/`의 `User`, `PublicProfile`, 에러 응답 정의와 맞춥니다.
- 인증이 필요한 API 요청에는 `Authorization: Bearer <accessToken>`을 붙입니다.
- 에러 처리는 `../docs/api-contract.md`의 공통 에러 응답 형식을 기준으로 구현합니다.

## 현재 확정된 인증 방향

- 카카오 SDK로 로그인을 시작하고 `authorizationCode`를 받습니다.
- `authorizationCode`와 `redirectUri`를 `POST /api/auth/kakao`로 백엔드에 보냅니다.
- 백엔드가 반환한 Runvas `accessToken`을 저장하고 Runvas API 호출에 사용합니다.
- 카카오 액세스 토큰은 Runvas API 인증 토큰으로 사용하지 않습니다.

## 경계 규칙

- mobile 관련 `.md` 파일(`CLAUDE.md`, `AGENTS.md`, `WORKLOG.md`)은 이 디렉토리(`runvas/mobile/`)
  안에만 작성하세요. `runvas/docs/` 문서는 백엔드·모바일 공통 영역이므로 모바일 작업만으로 수정하지 않습니다.
- API 키(`EXPO_PUBLIC_KAKAO_APP_KEY`, `EXPO_PUBLIC_TMAP_APP_KEY`)와 `EXPO_PUBLIC_API_BASE_URL`은
  `.env`에만 두고 커밋하지 않습니다. `.env.example`만 갱신하세요.
