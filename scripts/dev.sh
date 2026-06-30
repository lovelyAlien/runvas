#!/usr/bin/env bash
# 백엔드 + Expo 개발 서버를 동시에 시작합니다.
#
# 사전 조건:
#   - Docker Desktop 실행 중
#   - backend/.env 파일 존재 (backend/.env.example 참고)
#   - mobile/.env 파일 존재
#
# 사용법:
#   ./scripts/dev.sh                        # 기본 (runvas/mobile)
#   ./scripts/dev.sh --mobile <경로>        # 다른 mobile 디렉터리 (예: git worktree)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
MOBILE_DIR="$REPO_ROOT/mobile"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mobile)
      MOBILE_DIR="$(cd "$2" && pwd)"
      shift 2
      ;;
    *)
      echo "알 수 없는 인수: $1" >&2
      exit 1
      ;;
  esac
done

# ── 사전 조건 확인 ────────────────────────────────────────────────

check_docker() {
  if ! docker info &>/dev/null; then
    echo "❌  Docker Desktop이 실행 중이지 않습니다. 먼저 시작해 주세요."
    exit 1
  fi
}

check_backend_env() {
  if [[ ! -f "$BACKEND_DIR/.env" ]]; then
    echo "❌  $BACKEND_DIR/.env 파일이 없습니다."
    echo "   .env.example을 복사한 뒤 값을 채워 주세요:"
    echo "   cp $BACKEND_DIR/.env.example $BACKEND_DIR/.env"
    exit 1
  fi
  if ! grep -q "^JWT_SECRET=.\+" "$BACKEND_DIR/.env"; then
    echo "❌  backend/.env 에 JWT_SECRET 값이 없습니다."
    echo "   다음 명령으로 값을 생성할 수 있습니다:"
    echo "   openssl rand -base64 32"
    exit 1
  fi
}

check_mobile_env() {
  if [[ ! -f "$MOBILE_DIR/.env" ]]; then
    echo "❌  $MOBILE_DIR/.env 파일이 없습니다."
    local original="$REPO_ROOT/mobile/.env"
    if [[ -f "$original" && "$MOBILE_DIR" != "$REPO_ROOT/mobile" ]]; then
      echo "   원본 .env를 심볼릭 링크로 연결하려면:"
      echo "   ln -s $original $MOBILE_DIR/.env"
    else
      echo "   .env.example을 참고해 .env를 만들어 주세요."
    fi
    exit 1
  fi
}

check_docker
check_backend_env
check_mobile_env

# ── Docker Compose로 인프라 시작 ──────────────────────────────────

echo "▶  인프라 시작  (PostgreSQL + Redis via Docker Compose)"
docker compose -f "$REPO_ROOT/docker-compose.yml" up -d

echo -n "   PostgreSQL 준비 대기 중"
until docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T postgres pg_isready -U runvas &>/dev/null; do
  echo -n "."
  sleep 1
done
echo " 완료"

# ── 프로세스 정리 ─────────────────────────────────────────────────

PIDS=()

cleanup() {
  echo -e "\n종료 중..."
  for pid in "${PIDS[@]:-}"; do
    pkill -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  echo "Docker Compose 인프라는 계속 실행 중입니다. 중단하려면:"
  echo "  docker compose -f $REPO_ROOT/docker-compose.yml down"
  echo "완료"
}
trap cleanup INT TERM

# ── 백엔드 시작 (backend/.env 주입) ──────────────────────────────

echo "▶  백엔드 시작  ($BACKEND_DIR)"
(
  set -a
  # shellcheck source=/dev/null
  source "$BACKEND_DIR/.env"
  set +a
  cd "$BACKEND_DIR" && ./gradlew bootRun 2>&1 | sed 's/^/[backend] /'
) &
PIDS+=($!)

# ── Expo 시작 ─────────────────────────────────────────────────────

echo "▶  Expo 시작    ($MOBILE_DIR)"
(cd "$MOBILE_DIR" && npx expo start 2>&1 | sed 's/^/[expo]    /') &
PIDS+=($!)

echo ""
echo "두 서버가 시작됐습니다. 종료하려면 Ctrl+C"
echo ""

wait "${PIDS[@]}"
