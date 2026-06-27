#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <commit-message-file>" >&2
  exit 2
fi

message_file=$1
subject=$(sed -n '1p' "$message_file")

if [ -z "$subject" ]; then
  echo "Commit subject must not be empty." >&2
  exit 1
fi

case "$subject" in
  Merge\ *|Revert\ \"*|revert:\ *)
    exit 0
    ;;
esac

if ! printf '%s\n' "$subject" | grep -Eq '^(feat|fix|docs|refactor|test|chore|perf|ci|style|build)(\([a-z0-9][a-z0-9._-]*\))?!?: .{1,100}$'; then
  cat >&2 <<'EOF'
Invalid commit message subject.

Use Conventional Commits:
  docs: define backend architecture
  feat(auth): 카카오 로그인 추가
  fix(api): 인증 에러 응답 정리

Allowed types:
  feat, fix, docs, refactor, test, chore, perf, ci, style, build

Keep the subject at 100 characters or fewer.
EOF
  exit 1
fi

if printf '%s\n' "$subject" | grep -Eiq '(codex|claude|generated-by|co-authored-by)'; then
  echo "Commit subject must not include tool or authorship attribution." >&2
  exit 1
fi

if [ "$(sed -n '2p' "$message_file")" != "" ] && [ -n "$(sed -n '2p' "$message_file")" ]; then
  echo "Commit body must be separated from the subject by one blank line." >&2
  exit 1
fi

if grep -Eiq '(generated-by|co-authored-by|written by codex|written by claude)' "$message_file"; then
  echo "Commit message must not include tool or authorship attribution." >&2
  exit 1
fi
