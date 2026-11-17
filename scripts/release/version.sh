#!/usr/bin/env bash
# Release tag -> versionName, versionCode and kind, as GitHub Actions outputs.
#   v0.2.0-rc.1 -> 0.2.0-rc.1, 20001, beta     v0.2.0 -> 0.2.0, 20099, production
set -euo pipefail

pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)(-rc\.([1-9][0-9]?))?$'

# The versionCode of a release tag; fails for anything else. rc stops at 98, so production's 99 stays above it.
code_of() {
  [[ "$1" =~ $pattern ]] || return 1
  local major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[3]} rc=${BASH_REMATCH[5]:-99}
  (( major <= 2099 )) || return 1
  [[ -z "${BASH_REMATCH[5]}" ]] || (( rc <= 98 )) || return 1
  echo $(( major * 1000000 + minor * 10000 + patch * 100 + rc ))
}

# `--order <tag>` prints only the versionCode, with no checks against other tags, so
# next-version.sh sorts tags by the same rule the release numbers them with.
if [[ "${1:-}" == --order ]]; then
  code_of "${2:?usage: version.sh --order <tag>}"
  exit
fi

tag=${1:?usage: version.sh <tag>}
code=$(code_of "$tag") || { echo "::error::$tag is not vX.Y.Z or vX.Y.Z-rc.N (Y, Z up to 99; N 1-98)" >&2; exit 1; }

# Play wants codes to go up. Inside one MAJOR.MINOR line a new tag must be above every other one;
# an older line (a hotfix branch) may sit below a newer line.
line=${tag%%-*}; line=${line%.*}
for other in $(git tag -l "$line.*"); do
  [[ "$other" == "$tag" ]] && continue
  other_code=$(code_of "$other") || continue
  (( code > other_code )) || { echo "::error::$tag (code $code) is not above $other (code $other_code)" >&2; exit 1; }
done

echo "name=${tag#v}"
echo "code=$code"
if [[ "$tag" == *-rc.* ]]; then echo "kind=beta"; else echo "kind=production"; fi
