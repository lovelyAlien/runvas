#!/usr/bin/env sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
git -C "$repo_root" config core.hooksPath .githooks

cat <<'EOF'
Git hooks are enabled for this repository.

The commit-msg hook now validates Conventional Commit messages.
EOF
