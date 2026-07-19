# 협업 가이드

이 프로젝트는 2명이 백엔드와 모바일을 나눠 작업하는 것을 전제로 합니다.

## 추천 역할 분담

### 담당자 A: Backend

- 코스 생성 API
- 코스 목록/상세 조회 API
- bounds 기반 검색
- 경로 거리, 예상 소요 시간, bounds 요청값 검증
- GPX 다운로드 응답
- 카카오 소셜 로그인 및 인증 기초 구조
- 공개 코스 북마크 API
- 게시글/댓글 CRUD API
- 코스/게시글 좋아요 API
- 작성자 권한 검증
- 커뮤니티 목록 페이지네이션과 인기순 정렬

### 담당자 B: Mobile

- 지도 화면
- 경로 그리기 인터랙션
- 보행자 경로 탐색 API 연동
- 코스 작성 폼
- 코스 탐색 목록/상세 화면
- 카카오 로그인/프로필 화면
- 커뮤니티 게시글 목록/상세 화면
- 댓글 작성 화면
- 좋아요 인터랙션
- API 연동
- GPX 다운로드 UX

## 첫 구현 순서

1. `docs/`에서 Course 모델과 API 기준 확정
2. `docs/`에서 커뮤니티 모델과 API 기준 확정
3. `backend/`에서 카카오 로그인, 사용자, 토큰 발급 API를 mock 또는 in-memory로 구현
4. `backend/`에서 mock 또는 in-memory 저장소로 코스 API를 구현
5. `backend/`에서 게시글, 댓글, 좋아요 API를 mock 또는 in-memory로 구현
6. `mobile/`에서 카카오 로그인과 프로필 화면을 연동
7. `mobile/`에서 mock API 또는 개발 서버 API로 코스 화면을 연동
8. `mobile/`에서 커뮤니티 화면을 연동
9. bounds 조회, 인기순 정렬, GPX 다운로드를 실제 구현
10. 인증과 영속화 세부 구현

## 기준 변경 절차

API나 데이터 모델 변경이 필요하면 다음 순서로 진행합니다.

1. `docs/` 문서를 먼저 수정합니다.
2. 백엔드와 모바일 담당자가 변경 내용을 확인합니다.
3. `backend/` 구현을 수정합니다.
4. `mobile/` 구현을 수정합니다.
5. 양쪽에서 같은 예시 요청/응답으로 동작을 확인합니다.

## Git 작업 흐름

팀 공통 Git 규칙은 에이전트와 사람이 같은 방식으로 작업하기 위한 기준입니다.

### 브랜치와 PR

- 기본 브랜치(`main`, `master`)에 직접 커밋하거나 푸시하지 않습니다.
- 모든 작업은 기능 브랜치에서 진행합니다.
- 브랜치 이름은 작업 의도를 드러내는 짧은 kebab-case를 권장합니다.
  - 예: `docs/git-workflow`
  - 예: `feat/kakao-auth`
- 작업이 끝나면 PR을 열고 변경 의도, 검증 결과, 남은 위험을 적습니다.

### 커밋 메시지

커밋 메시지는 Conventional Commits 형식을 사용합니다.

```text
<type>: <subject>
<type>(<scope>): <subject>
```

사용 가능한 `type`은 다음과 같습니다.

- `feat`: 사용자가 할 수 있는 일이 새로 생김
- `fix`: 깨졌거나 누락된 동작을 고침
- `docs`: 문서 변경
- `refactor`: 기능 변화 없는 구조 개선
- `test`: 테스트 추가 또는 수정
- `chore`: 유지보수성 설정, 도구, 기타 변경
- `perf`: 성능 개선
- `ci`: CI 설정 변경
- `style`: 포맷팅 등 동작 변화 없는 스타일 변경
- `build`: 빌드 시스템 또는 의존성 변경

`subject`는 명령형 또는 결과 중심으로 짧게 씁니다.

```text
docs: define backend architecture
feat(auth): 카카오 로그인 추가
fix(api): 인증 에러 응답 정리
```

### 커밋 body

작거나 명확한 변경은 subject만으로 충분합니다.
다음 경우에는 커밋 메시지 body를 추가합니다.

- 여러 파일이나 여러 계층(`docs/`, `backend/`, `mobile/`)에 걸친 변경
- API 계약, 데이터 모델, 인증, 좌표 규칙처럼 되돌리기 어려운 기준 변경
- 왜 이 방식을 선택했는지 나중에 알아야 하는 변경
- 검증 방법이나 한계가 커밋 기록에 남아야 하는 변경

body에는 변경 이유, 주요 결정, 검증 결과를 적습니다.
파일 목록을 반복하기보다 diff만으로 알 수 없는 맥락을 남깁니다.

```text
docs(api): 카카오 인증 계약 정리

모바일은 카카오 SDK에서 받은 authorizationCode만 백엔드로 전달하고,
백엔드는 카카오 토큰 교환과 사용자 조회를 책임지도록 역할을 분리했다.

카카오 access token은 Runvas API 인증 토큰이 아니므로 응답에 포함하지 않는다.
```

### 스테이징과 검증

- 의도한 변경 파일만 스테이징합니다.
- `git add .`, `git add -A`는 사용하지 않습니다.
- 민감 정보, 환경 변수 파일, 빌드 산출물이 커밋에 들어가지 않았는지 확인합니다.
- 커밋 전 변경 범위에 맞는 검증을 실행합니다.
  - 문서 변경: 링크, 예시 요청/응답, 용어 일관성 확인
  - 백엔드 변경: 관련 테스트 또는 API 동작 확인
  - 모바일 변경: 타입 체크, 테스트, 주요 화면 수동 확인

### 로컬 hook 설정

저장소에는 커밋 메시지 검증 hook이 포함되어 있습니다.
처음 한 번 아래 명령을 실행하면 로컬 커밋 시 메시지 형식을 확인합니다.

```bash
./scripts/setup-git-hooks.sh
```

이 hook은 로컬 편의 장치입니다. 최종 검증은 PR의 CI에서 수행합니다.

### push 전 체크리스트

원격에 push하거나 PR을 열기 전에, 변경한 영역에 해당하는 CI 검증을 로컬에서 먼저 실행합니다.
CI 워크플로우(`.github/workflows/`)와 로컬 확인 방법의 대응은 다음과 같습니다.

| 변경 영역 | CI 워크플로우 | 로컬 확인 명령 |
| --- | --- | --- |
| `backend/**` | `backend-test.yml` | `cd backend && ./gradlew test` |
| `mobile/**` | `mobile-typecheck.yml` | `cd mobile && npx tsc --noEmit` |
| 커밋 메시지 | `commit-message.yml` | `setup-git-hooks.sh` 활성화 시 커밋할 때 자동 검증 |

`backend-test.yml`은 Redis 서비스 컨테이너에 의존하므로, 로컬에서 `./gradlew test`를 실행하려면
Redis가 로컬에 떠 있어야 합니다.

`commit-message.yml`은 각 커밋 메시지뿐 아니라 PR 제목도 검증합니다. PR 제목은 GitHub에서 PR을
열 때 정해지므로 로컬 hook만으로는 완전히 재현할 수 없고, 최종 확인은 CI에서 이뤄집니다.

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
- 첫 배포 태그 push 이후, GitHub 저장소의 Packages 화면에서 `runvas-backend` 패키지의 Visibility가 **Private**로 설정돼 있는지 확인합니다 (`ghcr.io/lovelyalien/runvas-backend` package settings → Change visibility). 리포지토리가 public이라도 패키지 visibility는 별도로 관리되므로, 첫 push 후 반드시 수동으로 확인해야 합니다.
- VPS에서 1회 `docker login ghcr.io`를 실행해 GHCR 인증 상태를 남겨둡니다 (이미지가 private이라 pull에 인증이 필요, `read:packages` 권한의 GitHub PAT 사용).

### 범위 밖

- 배포 후 헬스체크 자동 확인 (헬스 엔드포인트가 아직 없음)
- 실패 시 자동 롤백 (필요하면 이전 태그로 사람이 수동 재배포)
- `main` push 시 자동 배포 (Continuous Deploy) — 태그 push로만 트리거

## 완료 기준

공통 기준 변경은 다음 조건을 만족해야 완료로 봅니다.

- 문서에 요청/응답 예시가 있다.
- 필드 타입과 필수 여부가 명확하다.
- 좌표 단위와 순서가 명확하다.
- 백엔드와 모바일 중 어느 쪽이 값을 생성하거나 검증하는지 명확하다.
- 작성자 권한, 공개 범위, 중복 좋아요 처리 방식이 명확하다.
