#!/usr/bin/env bash
# Expo 개발 서버를 시작합니다.
#
# 사전 조건:
#   - mobile/.env 파일 존재
#
# 사용법:
#   ./scripts/mobile.sh                        # 기본 (runvas/mobile)
#   ./scripts/mobile.sh --mobile <경로>        # 다른 mobile 디렉터리 (예: git worktree)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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

check_mobile_env

# ── 로컬 IP 감지 및 EXPO_PUBLIC_API_BASE_URL 업데이트 ─────────────

LOCAL_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
if [[ -z "$LOCAL_IP" ]]; then
  echo "⚠️  로컬 IP를 감지하지 못했습니다. EXPO_PUBLIC_API_BASE_URL을 수동으로 확인하세요."
else
  NEW_URL="http://${LOCAL_IP}:8080"
  if grep -q "^EXPO_PUBLIC_API_BASE_URL=" "$MOBILE_DIR/.env"; then
    sed -i '' "s|^EXPO_PUBLIC_API_BASE_URL=.*|EXPO_PUBLIC_API_BASE_URL=${NEW_URL}|" "$MOBILE_DIR/.env"
  else
    echo "EXPO_PUBLIC_API_BASE_URL=${NEW_URL}" >> "$MOBILE_DIR/.env"
  fi
  echo "▶  API URL     $NEW_URL"
fi

# ── Expo 시작 ─────────────────────────────────────────────────────

echo "▶  Expo 시작  ($MOBILE_DIR)"
echo ""
cd "$MOBILE_DIR" && npx expo start
