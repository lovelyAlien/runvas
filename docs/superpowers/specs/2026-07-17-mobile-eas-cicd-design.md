# 모바일 EAS Build CI/CD 배포 프로세스 설계

작성일: 2026-07-17
관련 문서: `mobile/eas.json`, `mobile/app.json`, `.github/workflows/mobile-typecheck.yml`,
`docs/collaboration.md`

## 배경

현재는 `mobile/eas.json`에 `development`/`preview`/`production` 세 프로필이 정의돼 있지만,
빌드는 항상 사람이 로컬에서 `eas build --profile <profile>`을 직접 실행해서 트리거한다. Git
이벤트(브랜치 push, 태그 push)와 EAS Build가 연결돼 있지 않아서, "이 커밋이 어떤 빌드로
나갔는지"를 추적하려면 사람의 기억이나 Slack 대화에 의존해야 한다.

백엔드는 `docker-compose.yml`에 이미 `deploy` profile(서비스 컨테이너)이 있지만, 이번 스펙에서는
다루지 않는다 — 모바일 EAS Build 자동화를 먼저 안정화한 뒤 별도 스펙으로 진행한다.

## 목표

1. `main` 브랜치에 `mobile/**` 변경이 push되면 자동으로 `preview` 프로필 빌드가 트리거된다.
2. `mobile-v{semver}` 형식의 태그(예: `mobile-v1.2.0`)가 push되면 자동으로 `production` 프로필
   빌드가 트리거된다.
3. 두 경로 모두 빌드 시작 전에 `npx tsc --noEmit` 타입체크를 통과해야 한다.
4. 빌드 성공/실패가 GitHub Actions 상태(초록/빨강)에 그대로 반영된다 (`--wait` 사용).
5. `docs/collaboration.md`에 태그 규칙과 배포 절차를 팀 규칙으로 문서화한다.

## 범위 밖

- **EAS Submit 자동화** — 스토어 제출은 이번 스펙 이후에도 계속 사람이 수동으로 `eas submit`
  실행. Apple/Google 서비스 계정 credential을 CI에 등록하는 것은 별도 논의 필요.
- **`development` 프로필 자동화** — 로컬 개발 클라이언트 빌드는 지금처럼 로컬에서 수동 실행.
  PR마다 dev 빌드를 도는 것은 EAS 빌드 크레딧 소모 대비 이득이 낮다고 판단해 제외.
- **백엔드 배포 자동화** — `docker-compose.yml`의 `deploy` profile을 CI와 연결하는 작업은
  후속 스펙으로 분리.
- **버전 번호 자동 증가** — `eas.json`의 `appVersionSource: remote`가 이미 EAS 쪽에서
  빌드 번호를 관리하므로 추가 자동화 불필요.

## 워크플로: `.github/workflows/mobile-eas-build.yml`

```yaml
name: Mobile EAS Build

on:
  push:
    branches: [main]
    paths: ['mobile/**']
    tags: ['mobile-v*.*.*']

jobs:
  build-preview:
    if: github.ref == 'refs/heads/main'
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

  build-production:
    if: startsWith(github.ref, 'refs/tags/mobile-v')
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
      - run: eas build --profile production --platform all --non-interactive --wait
```

두 job은 의도적으로 `needs:`로 묶지 않고 각자 타입체크 스텝을 포함한다. `build-preview`가
`if: github.ref_type == 'branch'` 같은 조건으로 스킵되는 경우, 이를 `needs`로 참조하는
`build-production`도 함께 스킵 처리되는 GitHub Actions 특성이 있어, 두 경로를 완전히
독립적인 job으로 유지하는 편이 이 규모에서는 더 안전하다.

`paths: ['mobile/**']`는 브랜치 push 필터로만 의미가 있고, 태그 push는 사람이 명시적으로
찍는 행위이므로 경로 필터를 적용하지 않는다.

## 사전 준비물 (사람이 직접 수행)

- **`EXPO_TOKEN` GitHub Secret 등록**: [expo.dev/settings/access-tokens](https://expo.dev/settings/access-tokens)에서
  발급한 토큰을 저장소 Settings → Secrets and variables → Actions에 `EXPO_TOKEN`으로 등록.
  이 값은 자격증명이므로 에이전트가 대신 등록하지 않는다.

## `docs/collaboration.md` 추가 내용

- 태그 규칙: `mobile-v{semver}` (예: `mobile-v1.2.0`) 형식으로만 production 빌드가
  트리거된다는 것.
- 절차: main이 배포 가능한 상태가 되면 담당자가 로컬에서
  `git tag mobile-v1.2.0 && git push origin mobile-v1.2.0` 실행 → production 빌드 시작 →
  완료 후 Actions Job Summary의 EAS 빌드 링크에서 산출물 확인 → 필요 시 수동 `eas submit`.

## 검증 계획

- `mobile-eas-build.yml` 문법을 `actionlint` 또는 GitHub의 workflow 편집기 파싱으로 확인
  (실제 EAS 빌드 실행은 `EXPO_TOKEN` 등록 후 main push/태그 push로 별도 확인).
- 기존 `mobile-typecheck.yml`과 새 워크플로의 타입체크 스텝이 동일한 결과를 내는지 확인.
- 태그 push 시 `build-preview`는 트리거되지 않고 `build-production`만 트리거되는지,
  main push 시 반대로 동작하는지 실제 push로 확인.
