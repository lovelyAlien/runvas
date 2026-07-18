# 모바일 EAS Build CI/CD 배포 프로세스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `main` push와 `mobile-v*.*.*` 태그 push를 각각 EAS `preview`/`production` 빌드에 자동 연결하는 GitHub Actions 워크플로를 추가하고, 팀 배포 절차를 `docs/collaboration.md`에 문서화한다.

**Architecture:** 워크플로 두 개(`mobile-eas-build-preview.yml`, `mobile-eas-build-production.yml`)를 독립 파일로 둔다. 각 워크플로는 checkout → node 설치 → `npm ci` → `npx tsc --noEmit` → `expo/expo-github-action`으로 EAS 인증 → `eas build --wait`까지 자체적으로 수행한다.

> **설계 수정 (Task 1 리뷰 중 발견):** 원래 하나의 워크플로 파일에 `tags`와 `paths` 필터를 함께 두고 job별 `if:`로 나누는 안이었으나, GitHub Actions는 같은 `on.push` 블록의 `tags`/`branches`와 `paths`를 AND로 결합한다. 릴리스 태그가 가리키는 정확한 커밋이 `mobile/**`을 건드리지 않으면(예: 태그 시점 HEAD가 backend-only 커밋인 경우) `build-production`이 아예 트리거되지 않는 결함이 있었다. 파일을 두 개로 분리해 `paths` 필터를 `preview` 트리거에만 적용하고 `production` 태그 트리거에는 적용하지 않는 방식으로 수정했다.

**Tech Stack:** GitHub Actions, `actions/checkout@v4`, `actions/setup-node@v4`, `expo/expo-github-action@v8`, EAS CLI (`eas build`), yamllint(로컬 문법 검증용).

## Global Constraints

- 트리거: `push` to `main` (path filter `mobile/**`) → `mobile-eas-build-preview.yml`의 `build-preview` job 실행. `push` tag matching `mobile-v*.*.*` (path filter 없음) → `mobile-eas-build-production.yml`의 `build-production` job 실행. 두 워크플로는 별개 파일이므로 `if:`나 `needs:`로 서로 조건을 나눌 필요가 없다.
- `eas build` 호출에는 반드시 `--non-interactive --wait` 플래그를 포함한다.
- `EXPO_TOKEN`은 이 작업 범위에서 등록하지 않는다 — 자격증명이므로 사용자가 GitHub repo Settings에서 직접 등록.
- EAS Submit 자동화, `development` 프로필 자동화, 백엔드 배포 자동화는 이번 플랜의 범위 밖.
- 커밋 메시지는 Conventional Commits, `git add -A`/`git add .` 금지, 커밋에 도구/저작자 표시(`Co-Authored-By` 등) 금지.

---

### Task 1: EAS Build 워크플로 2개 추가 (`preview`/`production` 분리)

**Files:**
- Create: `.github/workflows/mobile-eas-build-preview.yml`
- Create: `.github/workflows/mobile-eas-build-production.yml`
- Delete (있다면): `.github/workflows/mobile-eas-build.yml` (이전 단일 파일 시도의 잔재. 없으면 이 스텝은 스킵)

**Interfaces:**
- Consumes: 없음 (신규 독립 워크플로).
- Produces: 워크플로 파일명 `mobile-eas-build-preview.yml`/`mobile-eas-build-production.yml`, job 이름 `build-preview`/`build-production`. Task 2의 문서에서 이 파일/job 이름을 그대로 인용한다.

- [ ] **Step 1: preview 워크플로 파일 작성**

`.github/workflows/mobile-eas-build-preview.yml`:

```yaml
name: Mobile EAS Build (Preview)

on:
  push:
    branches: [main]
    paths: ['mobile/**']

jobs:
  build-preview:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: mobile
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: mobile/package-lock.json
      - run: npm ci
      - run: npx tsc --noEmit
      - uses: expo/expo-github-action@v8
        with:
          eas-version: latest
          token: ${{ secrets.EXPO_TOKEN }}
      - run: eas build --profile preview --platform all --non-interactive --wait
```

- [ ] **Step 2: production 워크플로 파일 작성**

`.github/workflows/mobile-eas-build-production.yml`:

```yaml
name: Mobile EAS Build (Production)

on:
  push:
    tags: ['mobile-v*.*.*']

jobs:
  build-production:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: mobile
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: mobile/package-lock.json
      - run: npm ci
      - run: npx tsc --noEmit
      - uses: expo/expo-github-action@v8
        with:
          eas-version: latest
          token: ${{ secrets.EXPO_TOKEN }}
      - run: >
          eas build --profile production --platform all
          --non-interactive --wait
```

- [ ] **Step 3: YAML 문법 검증**

Run: `yamllint -d relaxed .github/workflows/mobile-eas-build-preview.yml .github/workflows/mobile-eas-build-production.yml`
Expected: 출력 없음, exit code 0.

- [ ] **Step 4: 트리거 필터 확인**

Run: `grep -n "branches:\|paths:\|tags:" .github/workflows/mobile-eas-build-preview.yml .github/workflows/mobile-eas-build-production.yml`
Expected:
```
mobile-eas-build-preview.yml:    branches: [main]
mobile-eas-build-preview.yml:    paths: ['mobile/**']
mobile-eas-build-production.yml:    tags: ['mobile-v*.*.*']
```
`mobile-eas-build-production.yml`에는 `paths`/`branches` 필터가 없어야 한다 (있으면 태그 push 시 AND 결합으로 트리거가 막힐 수 있음 — 이번 설계 수정의 핵심).

- [ ] **Step 5: 기존 typecheck 워크플로와 스텝 비교**

Run: `grep -c "run: npx tsc --noEmit" .github/workflows/mobile-eas-build-preview.yml .github/workflows/mobile-eas-build-production.yml .github/workflows/mobile-typecheck.yml`
Expected: 세 파일 모두 `1`. (동일한 타입체크 명령이 세 워크플로에 각각 정확히 한 번씩 존재)

- [ ] **Step 6: 이전 단일 파일 삭제 및 커밋**

`.github/workflows/mobile-eas-build.yml`이 존재하면 삭제한다 (Task 1의 첫 시도에서 만든 파일이며, 이번 두 파일로 대체됨).

```bash
git rm -f --ignore-unmatch .github/workflows/mobile-eas-build.yml
git add .github/workflows/mobile-eas-build-preview.yml .github/workflows/mobile-eas-build-production.yml
git commit -m "ci(mobile): EAS Build 프로필별 자동 트리거 워크플로 추가"
```

---

### Task 2: `docs/collaboration.md`에 배포 절차 문서화

**Files:**
- Modify: `docs/collaboration.md` (파일 끝, `## 완료 기준` 섹션 앞에 새 섹션 추가)

**Interfaces:**
- Consumes: Task 1에서 만든 워크플로 파일명 `mobile-eas-build-preview.yml`/`mobile-eas-build-production.yml`, job 이름 `build-preview`/`build-production`, 태그 패턴 `mobile-v{semver}`.
- Produces: 없음 (문서 최종 산출물).

- [ ] **Step 1: 새 섹션 추가**

`docs/collaboration.md`의 `## 완료 기준` 섹션 바로 앞에 다음 섹션을 삽입한다 (161번째 줄, `## 완료 기준` 앞):

```markdown
## 모바일 배포 (EAS Build CI/CD)

두 워크플로가 Git 이벤트에 맞춰 EAS Build를 자동으로 트리거합니다.

| 트리거 | 워크플로 | Job | EAS 프로필 | 용도 |
| --- | --- | --- | --- | --- |
| `main` push (`mobile/**` 변경 시) | `mobile-eas-build-preview.yml` | `build-preview` | `preview` | 내부 배포용 상시 빌드 |
| `mobile-v{semver}` 태그 push (예: `mobile-v1.2.0`) | `mobile-eas-build-production.yml` | `build-production` | `production` | 정식 릴리스 빌드 |

`production` 워크플로는 경로 필터가 없습니다 — 태그가 가리키는 커밋의 파일 변경 여부와 무관하게, 릴리스 태그를 push하면 항상 트리거됩니다.

### 정식 릴리스 절차

1. `main`이 배포 가능한 상태인지 확인합니다 (`build-preview`가 성공한 최신 커밋인지 확인).
2. 로컬에서 버전 태그를 생성하고 push합니다.

   ```bash
   git tag mobile-v1.2.0
   git push origin mobile-v1.2.0
   ```

3. `build-production` job이 끝나면 GitHub Actions의 Job Summary에서 EAS 빌드 링크를 확인합니다.
4. 스토어 제출은 자동화돼 있지 않습니다. 산출물을 받아 필요 시 수동으로 `eas submit`을 실행합니다.

### 사전 준비물

- 저장소 Settings → Secrets and variables → Actions에 `EXPO_TOKEN`이 등록돼 있어야 `build-preview`/`build-production`이 EAS에 인증할 수 있습니다. 이 토큰은 [expo.dev/settings/access-tokens](https://expo.dev/settings/access-tokens)에서 발급합니다.

### 범위 밖

- `development` 프로필 빌드는 계속 로컬에서 수동 실행합니다 (`eas build --profile development`).
- 백엔드 배포 자동화는 별도로 진행합니다.
```

- [ ] **Step 2: 삽입 위치 확인**

Run: `grep -n "^## " docs/collaboration.md`
Expected: `## 모바일 배포 (EAS Build CI/CD)`가 `## Git 작업 흐름`과 `## 완료 기준` 사이에 나타나야 한다.

- [ ] **Step 3: 커밋**

```bash
git add docs/collaboration.md
git commit -m "docs: 모바일 EAS Build CI/CD 배포 절차 문서화"
```

---

## Post-Merge 수동 검증 (플랜 범위 밖, 사람이 직접 수행)

이 두 태스크는 워크플로 문법과 문서 내용만 검증한다. 실제 EAS 빌드 트리거는 `EXPO_TOKEN` 시크릿 등록과 실제 push가 필요하므로 PR 머지 이후 사람이 다음을 확인한다.

- PR 머지로 `main`에 mobile 변경이 반영되면 `build-preview`가 자동으로 돌고 성공하는지 GitHub Actions 탭에서 확인.
- 실제 릴리스 시점에 `mobile-v{semver}` 태그를 push해 `build-production`만 트리거되고 `build-preview`는 트리거되지 않는지 확인 (이 태그 push는 실제 EAS 빌드 크레딧을 소모하므로 진짜 릴리스 시점에 수행할 것).

## Self-Review 결과

- **스펙 커버리지**: 스펙의 목표 1~5 모두 Task 1(목표 1~4) + Task 2(목표 5)로 커버됨. "범위 밖" 항목(EAS Submit, dev 프로필, 백엔드, 버전 자동증가)은 플랜에 포함하지 않음 — 의도된 누락.
- **플레이스홀더 스캔**: 없음. 모든 스텝에 실행 가능한 실제 명령/코드 포함.
- **이름 일관성**: 워크플로 파일명(`mobile-eas-build-preview.yml`/`mobile-eas-build-production.yml`), job 이름(`build-preview`/`build-production`), 태그 패턴(`mobile-v*.*.*`)이 Task 1과 Task 2 전체에서 동일하게 사용됨.
- **Task 1 리뷰 반영**: 단일 파일 + `tags`/`paths` 공존 설계가 태그 push 시 AND 결합으로 트리거를 막을 수 있는 결함이 발견되어, 사람 확인 후 파일을 2개로 분리하는 쪽으로 계획을 수정함 (Architecture, Global Constraints, Task 1, Task 2 전체 반영 완료).
