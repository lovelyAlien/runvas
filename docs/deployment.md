# 배포 가이드

모바일과 백엔드의 배포 절차를 정리합니다. Git 브랜치/커밋/PR 같은 협업 규칙은
`docs/collaboration.md`를 참고하세요.

## 모바일 배포 (EAS Build CI/CD)

두 워크플로가 Git 이벤트에 맞춰 EAS Build를 자동으로 트리거합니다. `development`는 CI 대상이
아니라 로컬에서 수동으로만 실행합니다.

| 프로필 | 트리거 | 워크플로 / Job | 배포 대상 | 스토어(TestFlight) 경유 |
| --- | --- | --- | --- | --- |
| `development` | 로컬에서 수동 `eas build --profile development` | 없음 (CI 미연동) | 개발자 본인 기기 | 아님 (직접 설치) |
| `preview` | `main` push (`mobile/**` 변경 시) | `mobile-eas-build-preview.yml` → `build-preview` | 팀 내부 상시 빌드 | 아님 (EAS ad-hoc 링크로 직접 설치) |
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

**용도:** `main`의 최신 상태를 팀원/이해관계자가 스토어 심사 없이 수시로 설치해서 확인합니다.

**트리거:** `mobile/**` 변경이 `main`에 push될 때마다 `mobile-eas-build-preview.yml`이 자동
실행됩니다. 사람이 직접 트리거할 필요가 없습니다.

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

3. `mobile-eas-build-production.yml`이 자동으로 `eas build --profile production --platform all`을
   실행합니다 (여기까지만 자동입니다). `build-production` job이 끝나면 GitHub Actions의
   Job Summary에서 EAS 빌드 링크를 확인합니다.
4. **스토어 제출은 자동화돼 있지 않습니다.** 빌드가 끝나면 사람이 직접 실행합니다.

   ```bash
   cd mobile
   eas submit --profile production --platform ios
   ```

   이 명령이 App Store Connect에 빌드를 업로드하고, 거기서 TestFlight로 이어집니다. `eas submit`을
   CI에 자동화하지 않은 이유는 `docs/superpowers/specs/2026-07-17-mobile-eas-cicd-design.md`의
   "범위 밖"을 참고하세요 — 스토어 배포 권한을 가진 자격증명을 CI에 두는 건 보안 영향이 커서
   별도 논의가 필요하다고 판단했습니다.
5. App Store Connect의 TestFlight 탭에서 빌드 처리가 끝나면(보통 수 분~수십 분) 테스터 그룹에
   배정합니다. 이후 테스터의 TestFlight 앱에서 업데이트를 받을 수 있습니다.

**참고:** `mobile/app.json`의 `ios.infoPlist.ITSAppUsesNonExemptEncryption: false`가 이미
설정돼 있어, Apple의 수출 규정 준수(암호화 사용 여부) 질문에 추가 응답 없이 자동으로 통과됩니다.

**버전(version)과 빌드 번호(buildNumber)는 별개입니다.** `cli.appVersionSource: "remote"`라
`mobile/app.json`의 `"version"`은 매 빌드마다 다시 읽지 않고, EAS가 원격으로 관리하는 값을
씁니다. Git 태그(`mobile-v1.2.0`)는 CI를 트리거하는 신호일 뿐, 그 값을 버전에 반영하는 절차는
없습니다 — 태그를 새로 만들어도 버전 문자열은 자동으로 안 바뀝니다.

`production` 프로필에는 `"autoIncrement": true`가 설정돼 있어 **빌드 번호(iOS
buildNumber/Android versionCode)만** 빌드할 때마다 자동으로 +1 됩니다. App Store Connect는
같은 버전 문자열 안에서 빌드 번호가 겹치면 업로드를 거부하므로("Build number N for app version
X has already been used"), 같은 버전으로 재제출해야 할 때 이 설정이 필요합니다. 버전 문자열
자체(예: `1.0.0 → 1.0.1`)를 올리려면 `eas build:version:set`으로 직접 지정해야 합니다.

### 사전 준비물 (공통)

- 저장소 Settings → Secrets and variables → Actions에 `EXPO_TOKEN`이 등록돼 있어야
  `build-preview`/`build-production`이 EAS에 인증할 수 있습니다. 이 토큰은
  [expo.dev/settings/access-tokens](https://expo.dev/settings/access-tokens)에서 발급합니다.

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
4. 이어서 `deploy` job이 SSH로 운영 VPS에 접속해 새 이미지를 pull하고 `docker compose --profile deploy up -d`로 재시작합니다.
5. 데이터베이스 마이그레이션은 Flyway가 앱 기동 시 자동으로 수행하므로 별도 스텝이 없습니다.

### 사전 준비물

- 저장소 Settings → Secrets and variables → Actions → **Secrets**에 다음을 등록합니다.
  - `DEPLOY_SSH_HOST`: 운영 VPS 주소
  - `DEPLOY_SSH_USER`: SSH 접속 계정
  - `DEPLOY_SSH_KEY`: SSH 개인키 (대응하는 공개키가 VPS의 `~/.ssh/authorized_keys`에 등록돼 있어야 함)
- 같은 화면의 **Variables**에 `DEPLOY_PATH`(VPS에 저장소가 clone된 절대 경로, 예: `/home/deploy/runvas`)를 등록합니다.
- `deploy` job은 `docker compose pull/up`만 실행할 뿐 VPS의 git 저장소는 갱신하지 않습니다. `docker-compose.yml`이 바뀔 때마다(이번 백엔드 배포 자동화 도입 시 포함) VPS에서 먼저 `git pull`로 동기화해야 합니다.
  ```bash
  cd <DEPLOY_PATH> && git pull origin main
  ```
  동기화 전에는 `docker compose pull backend`가 `Skipped - No image to be pulled`로 아무 동작도 하지 않고, 기존 컨테이너가 교체되지 않은 채 그대로 남습니다.
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
