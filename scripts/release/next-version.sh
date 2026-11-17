#!/usr/bin/env bash
# Prints the next release tag from the commit prefixes, by SemVer: a feature is MINOR, anything
# else is PATCH, and a breaking change (type!: or a BREAKING CHANGE: line) is MAJOR — MINOR while
# the version is below 1.0.0, where anything may change. The tag goes to stdout, why to stderr.
#
#   next-version.sh                    the next release candidate
#   next-version.sh --auto             the same, but only when a feat: or fix: landed since the last tag
#   next-version.sh --final            the latest candidate as the production release; tag it on that
#                                      candidate's own commit, so production ships what testers had
#   next-version.sh --bump major       a chosen bump, for 1.0.0 or a version the rule cannot see
#   next-version.sh --latest           the newest release tag this commit grew from
#   next-version.sh --first            allow a repository with no release tag yet
#
# A candidate freezes its version's features: a feat after the first vX.Y.Z-rc starts X.(Y+1).0.
# Exit status: 0 a tag, 1 refused, 2 bad arguments, 3 nothing to release.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/commits.sh"

here=$(dirname "$0")
mode=candidate
forced=""
first_release=false
while (( $# )); do
  case "$1" in
    --auto) mode=auto ;;
    --final) mode=final ;;
    --latest) mode=latest ;;
    --first) first_release=true ;;
    --bump) forced=${2:?--bump needs major, minor or patch}; shift ;;
    *) echo "usage: next-version.sh [--auto | --final | --latest | --bump major|minor|patch] [--first]" >&2; exit 2 ;;
  esac
  shift
done
[[ -z "$forced" || "$forced" =~ ^(major|minor|patch)$ ]] || { echo "--bump takes major, minor or patch, not $forced" >&2; exit 2; }
[[ -z "$forced" || "$mode" == candidate ]] || { echo "--bump goes alone: it picks a candidate's version by hand" >&2; exit 2; }

refuse() { echo "$1" >&2; exit 1; }
nothing() { echo "$1" >&2; exit 3; }

# Without the history or the tags, every answer below would be a confident guess.
[[ "$(git rev-parse --is-shallow-repository)" == false ]] || refuse "This is a shallow clone, so the tags and the commits since them are missing. Fetch everything: git fetch --unshallow --tags"

# Release tags this commit grew from, lowest first, in version.sh's own order. A hotfix branch sees
# only its own line's tags.
tags=()
while read -r _ tag; do tags+=("$tag"); done < <(
  for tag in $(git tag -l 'v*' --merged HEAD); do
    order=$("$here/version.sh" --order "$tag") && echo "$order $tag"
  done | sort -n
)
if (( ${#tags[@]} == 0 )); then
  [[ "$first_release" == true ]] || refuse "No release tag reaches this commit. Fetch them (git fetch --tags), or pass --first for the very first release."
  latest=""; major=0; minor=0; patch=0; rc=""
else
  latest=${tags[${#tags[@]} - 1]}
  # version.sh already accepted every tag in the list, so this only takes it apart.
  [[ "$latest" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.([0-9]+))?$ ]]
  major=${BASH_REMATCH[1]}; minor=${BASH_REMATCH[2]}; patch=${BASH_REMATCH[3]}; rc=${BASH_REMATCH[5]}
fi
core="$major.$minor.$patch"

if [[ "$mode" == latest ]]; then
  [[ -n "$latest" ]] || refuse "No release tag reaches this commit"
  echo "$latest"
  exit 0
fi

# The largest change in a range of commits: major, minor, patch, or none when it is empty.
change_in() {
  local level=none sha title body
  while IFS=$'\x1f' read -r -d $'\x1e' sha title body; do
    if commit_breaks "$title" "$body"; then
      echo major; return
    elif [[ "$(commit_type "$title")" == feat ]]; then
      level=minor
    elif [[ "$level" == none ]]; then
      level="patch"
    fi
  done < <(commits_in "$1")
  echo "$level"
}

# True when a range holds a change people use: a feat, a fix or a breaking change that touches
# more than tests, CI, scripts or docs. Only such a change makes a build for testers.
ships_something() {
  local sha title body
  while IFS=$'\x1f' read -r -d $'\x1e' sha title body; do
    sha=${sha#$'\n'}
    if [[ "$(commit_type "$title")" != other ]] || commit_breaks "$title" "$body"; then
      behind_the_scenes "$sha" || return 0
    fi
  done < <(commits_in "$1")
  return 1
}

# The first candidate of this version: features after it belong to the next one.
first_candidate() {
  local tag
  for tag in "${tags[@]}"; do [[ "$tag" == "v$core-rc."* ]] && { echo "$tag"; return; }; done
}

if [[ "$mode" == final ]]; then
  [[ -n "$rc" ]] || refuse "${latest:-No tag} is not a release candidate, so there is nothing to promote"
  first=$(first_candidate)
  # A feature after the first candidate makes the next minor, never this version.
  if [[ "$first" != "$latest" ]] && [[ "$(change_in "$first..$latest")" =~ ^(minor|major)$ ]]; then
    refuse "A feature landed between $first and $latest, so it belongs to $major.$((minor + 1)).0. Release a candidate of that first."
  fi
  next="v$core"
  why="$latest becomes the production release, on its own commit"
else
  [[ -z "$latest" || -n "$(git log -1 --format=%h "$latest..HEAD")" ]] || nothing "Nothing was committed since $latest"
  if [[ "$mode" == auto ]]; then
    # Only code people use makes a build for testers; docs, tests and tooling wait for the next one.
    ships_something "${latest:+$latest..}HEAD" \
      || nothing "No feat: or fix: that changes the app since ${latest:-the first commit}"
  fi
  if [[ -n "$forced" ]]; then
    level=$forced
    why="--bump $forced"
  elif [[ -n "$rc" ]]; then
    first=$(first_candidate)
    level=$(change_in "$first..HEAD")
    why="since $first: $level"
  else
    level=$(change_in "${latest:+$latest..}HEAD")
    why="since ${latest:-the first commit}: $level"
  fi
  # Below 1.0.0 a breaking change is MINOR; 1.0.0 itself is chosen with --bump major.
  [[ "$level" != major || "$major" -ge 1 || "$forced" == major ]] || { level=minor; why+=" (breaking, below 1.0.0)"; }

  if [[ -n "$rc" && -z "$forced" && "$level" == patch ]]; then
    next="v$core-rc.$(( rc + 1 ))"
  else
    case "$level" in
      major) next="v$(( major + 1 )).0.0-rc.1" ;;
      minor) next="v$major.$(( minor + 1 )).0-rc.1" ;;
      patch) next="v$major.$minor.$(( patch + 1 ))-rc.1" ;;
    esac
  fi
fi

# version.sh is what the release runs on the tag: its limits and ordering hold here too.
"$here/version.sh" "$next" > /dev/null || exit 1
echo "$why" >&2
echo "$next"
