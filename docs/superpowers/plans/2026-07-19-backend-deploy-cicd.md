# 백엔드 배포 CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `backend-v{semver}` 태그 push 시 GitHub Actions가 백엔드 Docker 이미지를 빌드해 GHCR(private)에 push하고, 이어서 SSH로 운영 VPS에 접속해 새 이미지를 pull+재시작하는 배포 워크플로를 추가하고, 팀 배포 절차를 `docs/collaboration.md`에 문서화한다.

**Architecture:** 새 워크플로 `.github/workflows/backend-deploy.yml`에 `build-and-push` job과 `deploy` job을 순서대로(`needs:`) 둔다. `docker-compose.yml`의 `backend` 서비스는 `build: context` 대신 GHCR `image:`를 참조하도록 바꿔, VPS가 더 이상 소스에서 빌드하지 않고 pull만 하게 한다.

**Tech Stack:** GitHub Actions, `docker/login-action@v3`, `appleboy/ssh-action@v1`, GHCR(GitHub Container Registry), Docker Compose, yamllint(로컬 문법 검증용).

## Global Constraints

- 트리거: `push` tag matching `backend-v*.*.*`만. `main` push 자동배포(Continuous Deploy)는 이번 범위 밖. (스펙: `docs/superpowers/specs/2026-07-19-backend-deploy-cicd-design.md` §목표, §범위 밖)
- 이미지: `ghcr.io/lovelyalien/runvas-backend:latest`, **private** 유지. GHCR push 인증은 워크플로 내장 `GITHUB_TOKEN`으로 수행하며 별도 시크릿을 만들지 않는다.
- `build-and-push` → `deploy` 순서를 `needs:`로 강제한다 (이 워크플로는 두 job 모두 같은 태그 이벤트에서 항상 실행되므로, 모바일 워크플로에서 있었던 조건부 스킵 전파 문제는 없다).
- GitHub repo Secrets(사용자가 직접 등록, 에이전트가 대신 등록하지 않음): `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, `DEPLOY_SSH_KEY`.
- GitHub repo Variables(민감정보 아님): `DEPLOY_PATH` — VPS에 저장소가 clone된 절대 경로.
- 배포 후 헬스체크 자동 확인, 자동 롤백, VPS 최초 셋업 자동화, 별도 DB 마이그레이션 스텝은 이번 플랜의 범위 밖 — Flyway(`backend/build.gradle:29-30`)가 `ddl-auto: validate`(`backend/src/main/resources/application.yml:21`) 설정 하에 앱 기동 시 자동으로 마이그레이션을 수행한다.
- 커밋 메시지는 Conventional Commits, `git add -A`/`git add .` 금지, 커밋에 도구/저작자 표시(`Co-Authored-By` 등) 금지.

---

### Task 1: 배포 워크플로 추가 + `docker-compose.yml` 이미지 참조로 전환

**Files:**
- Create: `.github/workflows/backend-deploy.yml`
- Modify: `docker-compose.yml:20-22` (backend 서비스의 `build:` → `image:`)

**Interfaces:**
- Consumes: 없음 (신규 독립 워크플로 + 기존 `docker-compose.yml`의 `backend` 서비스 블록 수정).
- Produces: 워크플로 파일명 `backend-deploy.yml`, job 이름 `build-and-push`/`deploy`, 이미지 참조 `ghcr.io/lovelyalien/runvas-backend:latest`, 태그 패턴 `backend-v{semver}`, secret 이름 `DEPLOY_SSH_HOST`/`DEPLOY_SSH_USER`/`DEPLOY_SSH_KEY`, variable 이름 `DEPLOY_PATH`. Task 2의 문서에서 이 이름들을 그대로 인용한다.

- [ ] **Step 1: 배포 워크플로 파일 작성**

`.github/workflows/backend-deploy.yml`:

```yaml
name: Backend Deploy

on:
  push:
    tags: ['backend-v*.*.*']

permissions:
  contents: read
  packages: write

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - run: docker build -t ghcr.io/lovelyalien/runvas-backend:latest ./backend
      - run: docker push ghcr.io/lovelyalien/runvas-backend:latest

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.DEPLOY_SSH_HOST }}
          username: ${{ secrets.DEPLOY_SSH_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          script: |
            docker compose -f ${{ vars.DEPLOY_PATH }}/docker-compose.yml --profile deploy pull backend
            docker compose -f ${{ vars.DEPLOY_PATH }}/docker-compose.yml --profile deploy up -d --no-build
```

- [ ] **Step 2: `docker-compose.yml`의 backend 서비스를 이미지 참조로 변경**

`docker-compose.yml`에서 현재 이 블록을:

```yaml
  backend:
    build:
      context: ./backend
    restart: unless-stopped
```

다음으로 바꾼다 (그 아래 `profiles`, `depends_on`, `environment`, `volumes`, `ports` 등 나머지 필드는 그대로 둔다):

```yaml
  backend:
    image: ghcr.io/lovelyalien/runvas-backend:latest
    restart: unless-stopped
```

- [ ] **Step 3: YAML 문법 검증**

Run: `yamllint -d relaxed .github/workflows/backend-deploy.yml docker-compose.yml`
Expected: 출력 없음, exit code 0.

- [ ] **Step 4: 트리거 및 job 순서 확인**

Run: `grep -n "tags:\|needs:\|packages: write" .github/workflows/backend-deploy.yml`
Expected:
```
    tags: ['backend-v*.*.*']
  packages: write
    needs: build-and-push
```
`needs: build-and-push`가 `deploy` job에 있어야 하고, 트리거는 `tags`만 있고 `branches`나 `paths`가 없어야 한다 (있으면 태그 push 시 AND 결합으로 트리거가 막힐 수 있음 — 모바일 설계에서 이미 겪은 문제와 동일).

- [ ] **Step 5: `docker-compose.yml`에서 backend가 더 이상 소스 빌드를 참조하지 않는지 확인**

Run: `grep -n "context: ./backend\|image: ghcr.io/lovelyalien/runvas-backend" docker-compose.yml`
Expected:
```
    image: ghcr.io/lovelyalien/runvas-backend:latest
```
`context: ./backend` 줄은 더 이상 나오지 않아야 한다.

- [ ] **Step 6: 커밋**

```bash
git add .github/workflows/backend-deploy.yml docker-compose.yml
git commit -m "ci(backend): GHCR 빌드 후 VPS SSH 배포 워크플로 추가"
```

---

### Task 2: `docs/collaboration.md`에 백엔드 배포 절차 문서화

**Files:**
- Modify: `docs/collaboration.md:193` (기존 "## 모바일 배포 (EAS Build CI/CD)" 섹션 끝, `## 완료 기준` 섹션 바로 앞에 새 섹션 추가)

**Interfaces:**
- Consumes: Task 1에서 만든 워크플로 파일명 `backend-deploy.yml`, job 이름 `build-and-push`/`deploy`, 이미지 참조 `ghcr.io/lovelyalien/runvas-backend:latest`, 태그 패턴 `backend-v{semver}`, secret 이름 `DEPLOY_SSH_HOST`/`DEPLOY_SSH_USER`/`DEPLOY_SSH_KEY`, variable 이름 `DEPLOY_PATH`.
- Produces: 없음 (문서 최종 산출물).

- [ ] **Step 1: 새 섹션 추가**

`docs/collaboration.md`의 193번째 줄(모바일 섹션의 마지막 줄 "백엔드 배포 자동화는 별도로 진행합니다." 다음 빈 줄) 바로 뒤, `## 완료 기준` 섹션 앞에 다음 섹션을 삽입한다:

```markdown
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

3. `build-and-push` job이 `ghcr.io/lovelyalien/runvas-backend:latest` 이미지를 빌드해 GHCR(private)에 push합니다.
4. 이어서 `deploy` job이 SSH로 운영 VPS에 접속해 새 이미지를 pull하고 `docker compose --profile deploy up -d`로 재시작합니다.
5. 데이터베이스 마이그레이션은 Flyway가 앱 기동 시 자동으로 수행하므로 별도 스텝이 없습니다.

### 사전 준비물

- 저장소 Settings → Secrets and variables → Actions → **Secrets**에 다음을 등록합니다.
  - `DEPLOY_SSH_HOST`: 운영 VPS 주소
  - `DEPLOY_SSH_USER`: SSH 접속 계정
  - `DEPLOY_SSH_KEY`: SSH 개인키 (대응하는 공개키가 VPS의 `~/.ssh/authorized_keys`에 등록돼 있어야 함)
- 같은 화면의 **Variables**에 `DEPLOY_PATH`(VPS에 저장소가 clone된 절대 경로, 예: `/home/deploy/runvas`)를 등록합니다.
- VPS에서 1회 `docker login ghcr.io`를 실행해 GHCR 인증 상태를 남겨둡니다 (이미지가 private이라 pull에 인증이 필요, `read:packages` 권한의 GitHub PAT 사용).

### 범위 밖

- 배포 후 헬스체크 자동 확인 (헬스 엔드포인트가 아직 없음)
- 실패 시 자동 롤백 (필요하면 이전 태그로 사람이 수동 재배포)
- `main` push 시 자동 배포 (Continuous Deploy) — 태그 push로만 트리거
```

- [ ] **Step 2: 삽입 위치 확인**

Run: `grep -n "^## " docs/collaboration.md`
Expected: `## 백엔드 배포 (Docker + GHCR)`가 `## 모바일 배포 (EAS Build CI/CD)`와 `## 완료 기준` 사이에 나타나야 한다.

- [ ] **Step 3: 커밋**

```bash
git add docs/collaboration.md
git commit -m "docs: 백엔드 배포 CI/CD 절차 문서화"
```

---

## Post-Merge 수동 검증 (플랜 범위 밖, 사람이 직접 수행)

이 두 태스크는 워크플로/compose 문법과 문서 내용만 검증한다. 실제 배포 트리거는 시크릿·변수 등록과 VPS의 GHCR 로그인이 필요하므로 PR 머지 이후 사람이 다음을 확인한다.

- 저장소 Settings에 `DEPLOY_SSH_HOST`/`DEPLOY_SSH_USER`/`DEPLOY_SSH_KEY` secret과 `DEPLOY_PATH` variable을 등록.
- VPS에서 `docker login ghcr.io`를 1회 실행해 GHCR 인증 상태를 남겨둠.
- 실제 릴리스 시점에 `backend-v{semver}` 태그를 push해 `build-and-push` → `deploy`가 순서대로 성공하는지, VPS에서 새 컨테이너가 떠 있는지 확인 (실제 운영 서비스를 재시작하므로 진짜 배포 시점에 수행할 것).

## Self-Review 결과

- **스펙 커버리지**: 스펙의 목표 1~3 모두 Task 1(목표 1~2) + Task 2(목표 3)로 커버됨. "범위 밖" 항목(헬스체크, 롤백, VPS 최초 셋업, main 자동배포, 별도 마이그레이션 스텝)은 플랜에 포함하지 않음 — 의도된 누락.
- **플레이스홀더 스캔**: 없음. 모든 스텝에 실행 가능한 실제 명령/코드 포함.
- **이름 일관성**: 워크플로 파일명(`backend-deploy.yml`), job 이름(`build-and-push`/`deploy`), 이미지 참조(`ghcr.io/lovelyalien/runvas-backend:latest`), 태그 패턴(`backend-v{semver}`), secret/variable 이름이 Task 1과 Task 2 전체에서 동일하게 사용됨.
