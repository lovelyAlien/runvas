# Runvas Mobile

Runvas 모바일 앱 구현을 관리하는 디렉토리입니다.

화면, 지도 인터랙션, API 연동은 `../docs/`의 공통 기준에 맞춰 구현합니다.

작업 시작 전 [CLAUDE.md](./CLAUDE.md), [AGENTS.md](./AGENTS.md), [WORKLOG.md](./WORKLOG.md)를 먼저 확인하세요.

## 시작하기 (최초 1회)

```bash
npm install
cp .env.example .env   # Kakao/T-MAP 키, API_BASE_URL 입력
```

이 앱은 카카오 지도 WebView, 위치 권한, `expo-build-properties` 등 커스텀 네이티브 설정을 쓰기
때문에 Expo Go로는 제대로 테스트할 수 없습니다 (Expo Go는 자체 Info.plist를 써서 ATS 예외
설정이 반영되지 않습니다). 실기기에 개발용 dev-client를 한 번 설치해야 합니다.

```bash
cd mobile
eas build --profile development --platform ios   # 또는 android
```

빌드가 끝나면 EAS가 안내하는 링크/QR로 실기기에 직접 설치합니다 (스토어를 거치지 않습니다).
네이티브 의존성이 바뀌지 않는 한 다시 빌드할 필요는 없습니다.

## 로컬에서 테스트하기 (매번)

```bash
./scripts/mobile.sh
```

저장소 루트에서 실행합니다. `mobile/.env`가 있는지 확인하고, 현재 맥북의 로컬 WiFi IP를 감지해
`EXPO_PUBLIC_API_BASE_URL`을 자동으로 갱신한 뒤 Metro 서버를 띄웁니다 — 실기기가 같은 WiFi에서
개발자 컴퓨터의 로컬 백엔드(포트 8080)에 접근하게 하기 위함입니다. 뜨는 QR을 이미 설치된
dev-client 앱으로 스캔하면 코드 저장 시 Fast Refresh로 바로 반영됩니다.

다른 위치의 `mobile` 디렉터리(예: git worktree)에서 실행하려면:

```bash
./scripts/mobile.sh --mobile <경로>
```

## 빌드 & 배포

전체 절차(버전/빌드 번호 관리, App Store Connect 제출, ASC API 키 설정 등)는
[`../docs/deployment.md`](../docs/deployment.md)가 기준입니다. 요약하면:

1. `git tag mobile-v{semver} && git push origin mobile-v{semver}` — `production` 프로필로
   EAS 빌드가 자동 트리거됩니다 (빌드까지만 자동, 제출은 자동화하지 않음).
2. 빌드가 끝나면 사람이 직접 제출합니다:
   ```bash
   eas submit --profile production --platform ios
   ```
3. App Store Connect에서 빌드 선택 → App Review Information에 데모 계정 입력 → 심사 제출
   (이 마지막 단계는 의도적으로 수동입니다).

팀 내부 배포용 빌드가 필요하면 GitHub Actions에서 `Mobile EAS Build (Preview)` 워크플로를
수동 실행합니다 (`workflow_dispatch`).

## 기술 스택

- Expo (React Native, TypeScript)
- 지도: Kakao Maps JavaScript SDK (WebView)
- 보행자 경로 탐색: T-MAP
- 위치: expo-location (포그라운드)
- 경로 내보내기: GPX (expo-file-system, expo-sharing)
