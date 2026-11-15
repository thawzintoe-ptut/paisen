#!/usr/bin/env bash
# Prints the notes a release ships with. A production release keeps its CHANGELOG.md section
# ([X.Y.Z]). A beta lists what changed since the tag before it, so each rc says only its own
# changes: feat commits under "New", fix commits under "Fixed", from their titles. Commits that
# touch only tests, CI, scripts or docs, and build, ci, chore, docs and refactor commits, are left
# out. The first tag has nothing before it, so it falls back to [Unreleased].
set -euo pipefail

kind=${1:?usage: notes.sh <beta|production> <versionName> [tag]}
name=${2:?usage: notes.sh <beta|production> <versionName> [tag]}
tag=${3:-HEAD}

changelog_section() {
  awk -v h="## [$1]" '
    index($0, h) == 1 { found = 1; next }
    found && (/^## \[/ || /^\[[^]]+\]: /) { exit }
    found { print }
  ' CHANGELOG.md | sed '/./,$!d'
}

# "feat: the add sheet asks…" -> "- The add sheet asks…."
as_line() {
  local title=${1#*: }
  title="$(printf '%s' "${title:0:1}" | tr '[:lower:]' '[:upper:]')${title:1}"
  [[ "$title" =~ [.!?]$ ]] || title+="."
  printf -- '- %s\n' "$title"
}

behind_the_scenes() {
  local files
  files=$(git show --name-only --format= "$1")
  [[ -n "$files" ]] && ! grep -qvE '(/[A-Za-z]*[Tt]est/|Test\.kt$|/test/|^\.github/|^scripts/|^docs/)' <<< "$files"
}

beta_notes() {
  local previous
  previous=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null) || return 1
  local new="" fixed="" sha subject
  while IFS=$'\t' read -r sha subject; do
    behind_the_scenes "$sha" && continue
    case "$subject" in
      feat:* | feat\(*) new+=$(as_line "$subject")$'\n' ;;
      fix:* | fix\(*) fixed+=$(as_line "$subject")$'\n' ;;
    esac
  done < <(git log --reverse --no-merges --format=$'%h\t%s' "$previous..$tag")
  if [[ -z "$new$fixed" ]]; then
    printf 'Dependency and build updates only. Nothing changes in the app.\n\n'
  fi
  [[ -z "$new" ]] || printf '### New\n\n%s\n' "$new"
  [[ -z "$fixed" ]] || printf '### Fixed\n\n%s\n' "$fixed"
  if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
    printf 'Every change: https://github.com/%s/compare/%s...%s\n' "$GITHUB_REPOSITORY" "$previous" "${GITHUB_REF_NAME:-$tag}"
  fi
}

if [[ "$kind" == beta ]]; then
  notes=$(beta_notes || changelog_section Unreleased)
else
  notes=$(changelog_section "$name")
fi

if [[ "$kind" == beta ]]; then heading="Unreleased"; else heading="$name"; fi
[[ -n "${notes//[[:space:]]/}" ]] || { echo "::error::CHANGELOG.md has no text under ## [$heading]" >&2; exit 1; }
printf '%s\n' "$notes"
