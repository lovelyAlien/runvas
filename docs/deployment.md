# 배포 가이드

모바일과 백엔드의 배포 절차를 정리합니다. Git 브랜치/커밋/PR 같은 협업 규칙은
`docs/collaboration.md`를 참고하세요.

## 모바일 배포 (EAS Build CI/CD)

`production`은 Git 이벤트(버전 태그 push)에 맞춰 EAS Build를 자동으로 트리거합니다.
`development`와 `preview`는 CI 대상이 아니라 필요할 때 사람이 직접 실행합니다 — EAS 빌드 크레딧은
월간 한도가 있어서, `main`에 머지될 때마다 자동으로 preview 빌드가 도는 건 크레딧을 예상보다
빨리 소진시킬 수 있다고 판단해 수동 트리거로 바꿨습니다.

| 프로필 | 트리거 | 워크플로 / Job | 배포 대상 | 스토어(TestFlight) 경유 |
| --- | --- | --- | --- | --- |
| `development` | 로컬에서 수동 `eas build --profile development` | 없음 (CI 미연동) | 개발자 본인 기기 | 아님 (직접 설치) |
| `preview` | GitHub Actions에서 수동 실행 (`workflow_dispatch`) | `mobile-eas-build-preview.yml` → `build-preview` | 팀 내부 상시 빌드 | 아님 (EAS ad-hoc 링크로 직접 설치) |
| `production` | `mobile-v{semver}` 태그 push (예: `mobile-v1.2.0`) | `mobile-eas-build-production.yml` → `build-production` | 정식 릴리스 / TestFlight | 맞음 (수동 `eas submit` 필요) |

`production` 워크플로는 경로 필터가 없습니다 — 태그가 가리키는 커밋의 파일 변경 여부와 무관하게, 릴리스 태그를 push하면 항상 트리거됩니다.

각 프로필의 세부 설정은 `mobile/eas.json`을 참고하세요.

### development — 로컬 개발용 dev-client

**용도:** 개발자가 실기기 또는 시뮬레이터에서 로컬 Metro 서버(`./scripts/mobile.sh`)에 실시간으로
붙여 코드 변경을 즉시 확인합니다. 네이티브 의존성이 바뀌지 않는 한 이 dev-client를 다시 빌드할
필요는 없습니다 — JS는 매번 Metro가 실시간으로 갈아 끼웁니다.

**사전 준비물:**

- Apple Developer Program(유료) 계정이 EAS 프로젝트에 연결돼 있어야 iOS 실기기용 서명이 가능합니다.
- 설치할 기기의 UDID가 Apple 계정에 등록돼 있어야 합니다: `eas device:create`로 추가합니다.
- `mobile/eas.json`의 `development.ios.simulator`가 `false`여야 실기기용으로 빌드됩니다
  (`true`면 Mac의 iOS 시뮬레이터 전용 빌드가 나오고, 실기기에는 설치할 수 없습니다).

**빌드:**

```bash
cd mobile
eas build --profile development --platform ios   # 또는 android, all
```

CI로 자동화돼 있지 않습니다 — 사람이 필요할 때 직접 실행합니다.

**설치 및 사용:**

1. 빌드 완료 후 EAS가 안내하는 링크/QR로 기기에 직접 설치합니다 (스토어를 거치지 않습니다).
2. 이후 `./scripts/mobile.sh` 실행 → 터미널에 뜨는 QR을 스캔 → 설치된 dev-client가 열리며
   "Fetching JavaScript bundle"이 표시됩니다. 이후로는 코드 저장 시 Fast Refresh로 실시간 반영됩니다.

### preview — 내부 상시 빌드

**용도:** `main`의 최신 상태를 팀원/이해관계자가 스토어 심사 없이 설치해서 확인합니다.

**트리거:** `mobile-eas-build-preview.yml`은 `workflow_dispatch`로만 실행됩니다 — `main`에
머지돼도 자동으로 돌지 않습니다. EAS 빌드 크레딧이 월간 한도라, 머지마다 자동으로 preview 빌드를
돌리면 크레딧을 예상보다 빨리 소진해 정작 필요한 production 빌드가 막힐 수 있어서 수동으로
바꿨습니다.

**수동 실행:**

```bash
gh workflow run mobile-eas-build-preview.yml
```

또는 GitHub 저장소의 Actions 탭 → `Mobile EAS Build (Preview)` → **Run workflow** 버튼으로도
실행할 수 있습니다.

**빌드 확인:**

```bash
gh run list --workflow=mobile-eas-build-preview.yml --limit 5
```

또는 GitHub Actions 실행 결과의 Job Summary에서 EAS 빌드 링크를 확인합니다.

**설치:** EAS의 `internal` 배포 링크로 직접 설치합니다. App Store Connect나 TestFlight를 거치지
않는, production과는 별개의 배포 경로입니다.

**주의:** 현재 `mobile/eas.json`의 `preview.ios.simulator`가 `true`로 되어 있어 iOS는 시뮬레이터
빌드만 나옵니다. 실기기에 preview 빌드를 설치하려면 `development`와 마찬가지로 이 값을 `false`로
바꿔야 합니다 (아직 바뀌지 않은 상태입니다).

### production — 정식 릴리스 / TestFlight

**용도:** 스토어 출시 또는 TestFlight를 통한 정식 배포.

**트리거:** 사람이 직접 버전 태그를 push할 때만 동작합니다 (자동 트리거 없음 — 릴리스 시점을
사람이 결정하도록 의도적으로 설계됨).

**절차:**

1. `main`이 배포 가능한 상태인지 확인합니다 (`build-preview`가 성공한 최신 커밋인지 확인).
2. 로컬에서 버전 태그를 생성하고 push합니다. 기존에 쓴 태그와 겹치지 않는 semver를 씁니다
   (`git tag -l "mobile-v*"`로 확인).

   ```bash
   git tag mobile-v1.2.0
   git push origin mobile-v1.2.0
   ```

3. `mobile-eas-build-production.yml`이 태그 이름에서 버전을 뽑아 `mobile/.release-version`
   파일에 적어둔 뒤(`mobile/app.config.js`가 이 파일을 읽어 `version`을 결정합니다)
   `eas build --profile production --platform all`을 실행합니다 (여기까지만 자동입니다). 빌드
   번호는 `eas.json`의 `autoIncrement: true` 설정에 따라 EAS가 자체적으로 올립니다.
   `build-production` job이 끝나면 GitHub Actions의 Job Summary에서 EAS 빌드 링크를 확인합니다.
4. **스토어 제출은 자동화돼 있지 않습니다.** 빌드가 끝나면 사람이 직접 실행합니다.

   ```bash
   cd mobile
   eas submit --profile production --platform ios
   ```

   이 명령은 **App Store Connect에 바이너리를 업로드하는 것까지만** 합니다 — 실제 심사 제출은
   아래 6번의 별도 수동 단계입니다. `eas submit`을 CI에 자동화하지 않은 이유는
   `docs/superpowers/specs/2026-07-17-mobile-eas-cicd-design.md`의 "범위 밖"을 참고하세요 —
   스토어 배포 권한을 가진 자격증명을 CI에 두는 건 보안 영향이 커서 별도 논의가 필요하다고
   판단했습니다.
5. App Store Connect의 TestFlight 탭에서 빌드 처리가 끝나면(보통 수 분~수십 분) 테스터 그룹에
   배정합니다. 이후 테스터의 TestFlight 앱에서 업데이트를 받을 수 있습니다.
6. **App Store 정식 심사 제출**은 TestFlight 배정과 별개로, App Store Connect에서 직접 진행합니다.
   업로드된 빌드가 처리 완료(이메일 통지)된 뒤:
   1. App Store Connect → 앱 → **App Store** 탭 → 해당 버전 선택
   2. **빌드** 항목에서 방금 업로드된 빌드 번호를 선택
   3. **App Review Information**의 Notes에 "Sign in with Apple로 로그인해달라"고 안내합니다
      (심사자 본인의 Apple 계정으로 별도 데모 계정 없이 로그인 가능). 카카오 테스트 계정
      아이디/비밀번호도 함께 남겨 대체 경로로 제공합니다 — 이게 둘 다 없으면 Guideline
      2.1.0(데모 계정 누락/로그인 불가)으로 자동 리젝됩니다
   4. **"심사에 제출"(Submit for Review)** 버튼을 눌러야 실제로 심사 큐에 들어갑니다

   이 마지막 클릭은 의도적으로 자동화하지 않았습니다 — 리젝 이력이 있는 계정이라 빌드를
   실기기에서 한 번 확인한 뒤 사람이 직접 제출하는 것을 원칙으로 합니다.

**참고:** `mobile/app.config.js`의 `ios.infoPlist.ITSAppUsesNonExemptEncryption: false`가 이미
설정돼 있어, Apple의 수출 규정 준수(암호화 사용 여부) 질문에 추가 응답 없이 자동으로 통과됩니다.

**버전(version)과 빌드 번호(buildNumber/versionCode)는 별개 개념이고, 값을 관리하는 위치도 다릅니다.**

- **버전:** `mobile/app.config.js`가 `mobile/.release-version` 파일(커밋되지 않음, `.gitignore`
  대상 아님 — 아래 참고)을 읽어서 결정합니다. 파일이 없으면 기본값 `1.0.0`을 씁니다. 태그 이름
  (`mobile-v1.2.0`)에서 `mobile-v` 접두어를 뗀 `1.2.0`이 이 파일의 내용이 됩니다.
  `mobile-eas-build-production.yml`의 "태그에서 버전을 .release-version에 반영" 스텝이 `eas
  build` 직전에 이 파일을 새로 만듭니다 — 커밋하지 않고 그 워크플로 실행(매번 새로 clone된
  워크트리) 안에서만 존재했다 사라집니다. 태그를 새로 만들 때마다 그 태그가 곧 버전입니다.
  환경변수가 아니라 파일인 이유: 이 프로젝트는 managed workflow라 네이티브 프로젝트가 저장소에
  없고, EAS 원격 빌드 워커가 `expo prebuild` 단계에서 `app.config.js`를 다시 평가합니다(아래
  참고) — CI 러너에만 설정한 환경변수는 그 원격 평가에 전달된다는 보장이 없지만, 프로젝트 트리에
  포함된 파일은 업로드에 그대로 딸려갑니다. 이 파일을 `.gitignore`에 넣지 않는 이유도 같습니다 —
  EAS Build는 업로드할 파일을 고를 때 기본적으로 `.gitignore`를 그대로 따르므로(`.easignore`가
  없는 한), gitignore된 파일은 원격 빌드 워커에 아예 전달되지 않습니다.
- **빌드 번호(iOS `buildNumber` / Android `versionCode`):** `cli.appVersionSource: "remote"`
  설정에 따라 **EAS 서버가 프로젝트 단위로 값을 추적**합니다. `mobile/app.config.js`에는 이
  필드를 두지 않습니다 (remote 모드에서는 로컬 값이 무시되기 때문 — `eas build`가 실행마다
  "field in app config is ignored" 경고를 띄웁니다). `build.production.autoIncrement: true`에
  따라 `production` 프로필 빌드마다 EAS가 원격 값을 이전 값 기준으로 자동으로 올립니다.
  현재 값은 `eas build:version:get -p ios`/`-p android`로 조회할 수 있고, 필요시
  `eas build:version:set -p <플랫폼>`으로 직접 지정할 수 있습니다 (대화형 프롬프트만
  지원합니다).

  **왜 `"local"` 대신 `"remote"`를 쓰는가:** 이전에는 `"local"` + `autoIncrement: true`
  조합이었는데, CI가 `eas build` 직전에 계산한 증가값을 `app.json`에 다시 커밋하지 않아서
  다음 실행이 매번 "커밋된 옛날 값 + 1"을 반복 계산하는 구조적 결함이 있었습니다. 실제로
  Android `versionCode`가 `1.0.3`, `1.0.4` 두 릴리스에서 똑같이 `3`으로 나온 사례가 있었고,
  이 상태로는 `eas submit` 시 Play Console이 업로드를 거부합니다. `"remote"`는 git 커밋 상태와
  무관하게 EAS 서버가 마지막 값을 직접 기억하므로 이 문제가 구조적으로 발생하지 않습니다.
  전환 시점에 `eas build:version:set`으로 그때까지 실제로 빌드된 최댓값(iOS build 5,
  Android versionCode 3) 이상으로 원격 카운터를 초기화해뒀습니다.

`cli.appVersionSource`는 프로필별이 아니라 **프로젝트 전체에 적용되는 설정**이라,
`development`/`preview` 빌드도 빌드 번호는 같은 원격 값을 그대로 조회해서 씁니다 — 다만 이
프로필들은 `autoIncrement`가 없어서 값을 올리지는 않고 마지막으로 설정/증가된 값을 그대로
재사용합니다 (내부 테스트용이라 매번 고유할 필요는 없습니다). 버전(semver)은 이 두 프로필의
워크플로가 `.release-version` 파일을 만들지 않으므로, `app.config.js`의 기본값(`1.0.0`)을
그대로 씁니다.

### 사전 준비물 (공통)

- 저장소 Settings → Secrets and variables → Actions에 `EXPO_TOKEN`이 등록돼 있어야
  `build-preview`/`build-production`이 EAS에 인증할 수 있습니다. 이 토큰은
  [expo.dev/settings/access-tokens](https://expo.dev/settings/access-tokens)에서 발급합니다.
- `eas submit`(iOS)이 App Store Connect API 키로 인증하려면:
  - `mobile/eas.json`의 `submit.production.ios.ascAppId`에 App Store Connect 앱 고유 숫자 ID를
    등록해둡니다 (Bundle ID `com.runvas.mobile`과는 다른 값 — App Store Connect 앱 상세 페이지
    URL의 `/apps/<숫자>/` 부분, 또는 "앱 정보"의 "Apple ID"). 이 값이 있으면 `eas submit`이
    Apple ID 로그인으로 앱을 조회하는 단계를 건너뜁니다.
  - 이 프로젝트는 EAS 서버에 이미 관리형 App Store Connect API 키가 저장돼 있어서
    (`eas submit` 실행 시 "Key Source: EAS servers"로 표시), 로컬에 `.p8` 파일이 없어도
    `ascAppId`만 있으면 제출이 됩니다.
  - 로컬에서 직접 키를 관리해야 하는 경우(EAS 관리형 키를 못 쓰는 상황 등), App Store Connect →
    사용자 및 접근 → 통합(Integrations) → App Store Connect API(**Team Keys** 탭)에서
    "App Manager" 권한의 키를 생성하고, `.p8` 파일은 **생성 시 1회만 다운로드 가능**하므로
    바로 저장해야 합니다. `mobile/private/`(gitignore 대상, 커밋되지 않음)에 파일을 두고,
    `mobile/private/eas-submit.env.sh`에 `EXPO_ASC_API_KEY_PATH`/`EXPO_ASC_KEY_ID`/
    `EXPO_ASC_ISSUER_ID` 환경변수를 채워 `source`한 뒤 `eas submit`을 실행하면 이 키로
    인증됩니다 (`.p8` 파일과 이 스크립트는 각자 로컬에 별도로 준비해야 하며, 저장소에는
    없습니다).

### 범위 밖

- `eas submit`(스토어 제출) CI 자동화 — 스토어 배포 권한 자격증명을 CI에 두는 문제라 별도 논의 필요.
- `development`/`preview` 빌드의 CI 자동 트리거 — 필요할 때만 사람이 직접 실행.
- 백엔드 배포 자동화는 별도로 진행합니다.

## 백엔드 배포 (Docker + GHCR)

`backend-deploy.yml` 워크플로가 릴리스 태그 push에 맞춰 백엔드를 자동으로 빌드하고 배포합니다.

| 트리거 | 워크플로 | Job | 용도 |
| --- | --- | --- | --- |
| `backend-v{semver}` 태그 push (예: `backend-v1.0.0`) | `backend-deploy.yml` | `build-and-push` → `deploy` | GHCR에 이미지 push 후 운영 VPS에 배포 |

### 정식 배포 절차

1. `main`이 배포 가능한 상태인지 확인합니다 (`backend-test.yml`이 성공한 최신 커밋인지 확인).
2. 로컬에서 버전 태그를 생성하고 push합니다.

   ```bash
   git tag backend-v1.0.0
   git push origin backend-v1.0.0
   ```

3. `build-and-push` job이 이미지를 빌드해 `ghcr.io/lovelyalien/runvas-backend:latest`와 `ghcr.io/lovelyalien/runvas-backend:backend-v1.0.0`(태그 이름 그대로) 두 태그로 GHCR(private)에 push합니다.
4. 이어서 `deploy` job이 SSH로 운영 VPS에 접속해 `DEPLOY_PATH`의 git 저장소를 `origin/main`으로
   동기화(`git fetch` + `git reset --hard`)한 뒤, 새 이미지를 pull하고 `docker compose --profile
   deploy up -d`로 재시작합니다.
5. 데이터베이스 마이그레이션은 Flyway가 앱 기동 시 자동으로 수행하므로 별도 스텝이 없습니다.

### 사전 준비물

- 저장소 Settings → Secrets and variables → Actions → **Secrets**에 다음을 등록합니다.
  - `DEPLOY_SSH_HOST`: 운영 VPS 주소
  - `DEPLOY_SSH_USER`: SSH 접속 계정
  - `DEPLOY_SSH_KEY`: SSH 개인키 (대응하는 공개키가 VPS의 `~/.ssh/authorized_keys`에 등록돼 있어야 함)
- 같은 화면의 **Variables**에 `DEPLOY_PATH`(VPS에 저장소가 clone된 절대 경로, 예: `/home/deploy/runvas`)를 등록합니다.
- `deploy` job은 `docker compose pull/up` 전에 `DEPLOY_PATH`에서 `git fetch origin main` +
  `git reset --hard origin/main`을 실행해 VPS의 git 저장소를 항상 최신 `main`으로 맞춥니다.
  `docker-compose.yml`이 바뀌어도 수동으로 `git pull`할 필요가 없습니다. `DEPLOY_PATH`는 배포
  전용 clone이어야 합니다 — `reset --hard`가 그 디렉터리의 로컬 변경사항을 모두 버리므로, 수동으로
  고친 파일을 두면 안 됩니다.
- 첫 배포 태그 push 이후, GitHub 저장소의 Packages 화면에서 `runvas-backend` 패키지의 Visibility가 **Private**로 설정돼 있는지 확인합니다 (`ghcr.io/lovelyalien/runvas-backend` package settings → Change visibility). 리포지토리가 public이라도 패키지 visibility는 별도로 관리되므로, 첫 push 후 반드시 수동으로 확인해야 합니다.
- VPS에서 1회 `docker login ghcr.io`를 실행해 GHCR 인증 상태를 남겨둡니다 (이미지가 private이라 pull에 인증이 필요, `read:packages` 권한의 GitHub PAT 사용).

### 범위 밖

- 배포 후 헬스체크 자동 확인 (헬스 엔드포인트가 아직 없음)
- 실패 시 자동 롤백. 배포는 항상 `:latest`를 pull하므로, 수동 롤백 시에는 VPS에서 다음을 실행합니다.

  ```bash
  docker pull ghcr.io/lovelyalien/runvas-backend:backend-v1.0.0   # 되돌릴 이전 버전 태그
  docker tag ghcr.io/lovelyalien/runvas-backend:backend-v1.0.0 ghcr.io/lovelyalien/runvas-backend:latest
  docker compose -f <DEPLOY_PATH>/docker-compose.yml --profile deploy up -d --no-build
  ```
- `main` push 시 자동 배포 (Continuous Deploy) — 태그 push로만 트리거
