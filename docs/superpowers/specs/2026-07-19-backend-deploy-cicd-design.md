# 백엔드 배포 CI/CD 설계

## 배경

모바일은 `mobile-eas-build-preview.yml`/`mobile-eas-build-production.yml`로 Git 태그/브랜치 이벤트에 맞춰 EAS Build를 자동 트리거하는 파이프라인이 이미 있다 (`docs/superpowers/specs/2026-07-17-mobile-eas-cicd-design.md`). 백엔드는 `docker-compose.yml`에 `backend` 서비스와 `deploy` profile이 이미 정의돼 있지만(`docker compose --profile deploy up -d`), 이 명령을 실행하는 과정은 아직 자동화돼 있지 않다.

백엔드는 실제로 요청을 받는 서비스라서, 배포는 곧 "지금 떠 있는 프로세스를 새 걸로 교체"하는 작업이다. 잘못된 배포가 바로 장애로 이어질 수 있어, 모바일 빌드보다 트리거 시점과 되돌릴 수 있는 여지를 더 보수적으로 잡는다.

## 목표

1. `backend-v{semver}` 태그 push 시, GitHub Actions가 백엔드 Docker 이미지를 빌드해 GHCR(private)에 push한다.
2. 같은 워크플로가 이어서 SSH로 운영 VPS에 접속해 새 이미지를 pull하고 `docker compose --profile deploy up -d`로 재시작한다.
3. 팀이 이 절차와 사전 준비물을 `docs/collaboration.md`에서 모바일 섹션과 같은 형식으로 찾을 수 있게 한다.

## 범위 밖

- 배포 후 헬스체크 자동 확인 (Actuator 등 헬스 엔드포인트가 아직 없음 — 사람이 직접 확인)
- 자동 롤백 (실패 시 이전 태그로 재배포하는 절차는 사람이 수동으로 수행)
- VPS 최초 셋업 자동화 (VPS는 이미 git clone + Docker 설치가 끝난 상태로 전제)
- `main` push 시 자동 배포 (Continuous Deploy) — 이번에는 태그 push로만 트리거
- 데이터베이스 마이그레이션 자동화 별도 스텝 — Flyway(`backend/build.gradle:29-30`)가 `ddl-auto: validate`(`backend/src/main/resources/application.yml:21`) 설정 하에 앱 기동 시 자동으로 마이그레이션을 실행하므로 배포 워크플로에 별도 스텝이 필요 없다.

## 아키텍처

새 워크플로 `.github/workflows/backend-deploy.yml`, 트리거는 `push` tag matching `backend-v*.*.*` 하나뿐이다.

```
push tag `backend-v*.*.*`
  → build-and-push job:
      - checkout
      - GHCR 로그인 (GITHUB_TOKEN, 워크플로 permissions: packages: write)
      - `docker build -t ghcr.io/lovelyalien/runvas-backend:latest ./backend`
      - `docker push ghcr.io/lovelyalien/runvas-backend:latest`
  → deploy job (needs: build-and-push):
      - SSH로 VPS 접속 (DEPLOY_SSH_HOST/DEPLOY_SSH_USER/DEPLOY_SSH_KEY)
      - `docker compose -f ${DEPLOY_PATH}/docker-compose.yml --profile deploy pull backend`
      - `docker compose -f ${DEPLOY_PATH}/docker-compose.yml --profile deploy up -d --no-build`
```

`DEPLOY_PATH`는 VPS에 저장소가 clone된 절대 경로다 (예: `/home/deploy/runvas`). 민감정보가 아니므로 GitHub repo Secret이 아니라 **Actions Variable**(`vars.DEPLOY_PATH`)로 등록한다.

두 job은 `needs:`로 순서를 강제한다 (모바일과 달리 여기서는 job 조건 분기가 없고 항상 같은 태그 이벤트에 대해 순차 실행되므로, 조건부 스킵 전파 문제가 없다).

`docker-compose.yml`의 `backend` 서비스 정의를 수정한다:
- 기존: `build: { context: ./backend }`
- 변경: `image: ghcr.io/lovelyalien/runvas-backend:latest`

VPS는 더 이상 소스에서 이미지를 빌드하지 않고, GHCR에서 미리 빌드된 이미지를 pull만 한다.

## GHCR 이미지 공개 범위

`ghcr.io/lovelyalien/runvas-backend`는 **private**로 유지한다. VPS가 이 이미지를 pull하려면 VPS에서 1회 `docker login ghcr.io`를 수행해 인증 상태를 남겨둬야 한다 (아래 사전 준비물 참고). GitHub Actions의 push 인증은 워크플로 내장 `GITHUB_TOKEN`으로 충분하며 별도 시크릿이 필요 없다.

## 시크릿 및 사전 준비물

**GitHub repo Secrets** (사용자가 GitHub Settings에서 직접 등록 — 자격증명이므로 에이전트가 대신 등록하지 않는다):
- `DEPLOY_SSH_HOST` — VPS 주소
- `DEPLOY_SSH_USER` — SSH 접속 계정
- `DEPLOY_SSH_KEY` — SSH 개인키 (대응하는 공개키가 VPS의 `~/.ssh/authorized_keys`에 등록돼 있어야 함)

**GitHub repo Variables** (민감정보 아님, Secrets와 별도로 등록):
- `DEPLOY_PATH` — VPS에 저장소가 clone된 절대 경로 (예: `/home/deploy/runvas`)

**VPS에서 1회 수동 설정** (CI 시크릿이 아니라 VPS 로컬 설정 — 사용자가 직접 수행):
- `read:packages` 권한의 GitHub PAT로 `docker login ghcr.io --username <github-id> --password <PAT>` 실행

## 문서화

`docs/collaboration.md`에 기존 "## 모바일 배포 (EAS Build CI/CD)" 섹션 바로 다음에 "## 백엔드 배포 (Docker + GHCR)" 섹션을 추가한다. 내용: 트리거 표(태그 패턴 → 워크플로 → 용도), 정식 배포 절차(로컬에서 `backend-v{semver}` 태그 생성 및 push), 사전 준비물(GitHub Secrets 3개 + Variable 1개 + VPS의 GHCR 로그인), 범위 밖 항목(헬스체크, 롤백, main 자동배포는 이번 범위 아님).

## 테스트 및 검증

이 변경은 GitHub Actions 워크플로 YAML과 `docker-compose.yml` 설정, 문서 변경이라 전통적인 단위 테스트 대상이 아니다. 구현 단계에서 다음으로 검증한다:
- `yamllint -d relaxed`로 새 워크플로 파일 문법 확인
- `docker-compose.yml`이 유효한 YAML이고 `backend` 서비스가 `image:`를 참조하며 더 이상 `build:`를 참조하지 않는지 확인
- 실제 배포 트리거(태그 push)는 `DEPLOY_SSH_*` 시크릿 등록과 VPS의 GHCR 로그인이 끝난 뒤 사람이 실제 릴리스 시점에 1회 수행해 확인 (모바일 프로덕션 빌드 검증과 동일한 패턴)
