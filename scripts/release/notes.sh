#!/usr/bin/env bash
# Prints the notes a release ships with, from the commit titles: feat commits under "New", fix
# commits under "Fixed", and any other breaking change under "New" too, since people meet it. A
# beta lists what changed since the tag before it, so each rc says only its own changes; a
# production release lists everything since the production release before it. Commits that touch
# only tests, CI, scripts or docs, and other types, are left out. commits.sh decides all of it.
# A first beta has nothing before it, so it falls back to [Unreleased].
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/commits.sh"

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

# The release before this one: any tag for a beta, only a production tag for production.
previous_tag() {
  if [[ "$kind" == beta ]]; then
    git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null
  else
    git describe --tags --abbrev=0 --match 'v*' --exclude '*-rc.*' "$tag^" 2>/dev/null
  fi
}

commit_notes() {
  local previous range
  if previous=$(previous_tag); then
    range="$previous..$tag"
  elif [[ "$kind" == production ]]; then
    # The first production release says everything that came before it.
    previous=""
    range=$tag
  else
    return 1
  fi
  local new="" fixed="" sha title body
  while IFS=$'\x1f' read -r -d $'\x1e' sha title body; do
    sha=${sha#$'\n'}
    behind_the_scenes "$sha" && continue
    case "$(commit_type "$title")" in
      feat) new+=$(as_line "$title")$'\n' ;;
      fix) fixed+=$(as_line "$title")$'\n' ;;
      *) ! commit_breaks "$title" "$body" || new+=$(as_line "$title")$'\n' ;;
    esac
  done < <(commits_in "$range")
  if [[ -z "$new$fixed" ]]; then
    printf 'Dependency and build updates only. Nothing changes in the app.\n\n'
  fi
  [[ -z "$new" ]] || printf '### New\n\n%s\n' "$new"
  [[ -z "$fixed" ]] || printf '### Fixed\n\n%s\n' "$fixed"
  if [[ -n "${GITHUB_REPOSITORY:-}" && -n "$previous" ]]; then
    printf 'Every change: https://github.com/%s/compare/%s...%s\n' "$GITHUB_REPOSITORY" "$previous" "${GITHUB_REF_NAME:-$tag}"
  fi
}

notes=$(commit_notes || changelog_section Unreleased)
[[ -n "${notes//[[:space:]]/}" ]] || { echo "::error::No release notes for $name: no commits since the last release, and nothing under ## [Unreleased]" >&2; exit 1; }
printf '%s\n' "$notes"
