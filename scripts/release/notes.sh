#!/usr/bin/env bash
# Prints the CHANGELOG.md section a release ships with: [Unreleased] for a beta, [X.Y.Z] for production.
set -euo pipefail

kind=${1:?usage: notes.sh <beta|production> <versionName>}
name=${2:?usage: notes.sh <beta|production> <versionName>}
if [[ "$kind" == beta ]]; then heading="Unreleased"; else heading="$name"; fi

notes=$(awk -v h="## [$heading]" '
  index($0, h) == 1 { found = 1; next }
  found && (/^## \[/ || /^\[[^]]+\]: /) { exit }
  found { print }
' CHANGELOG.md | sed '/./,$!d')

[[ -n "${notes//[[:space:]]/}" ]] || { echo "::error::CHANGELOG.md has no text under ## [$heading]" >&2; exit 1; }
printf '%s\n' "$notes"
