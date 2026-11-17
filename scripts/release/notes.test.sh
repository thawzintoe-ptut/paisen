#!/usr/bin/env bash
# Checks notes.sh against throwaway repositories. Run from anywhere; CI runs it too.
set -euo pipefail

# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/testlib.sh"

# The notes as one line, so a check reads like the release page: "### New|- One.|..."
notes() { (cd "$1" && GITHUB_REPOSITORY="" "$release_dir/notes.sh" "$2" "$3" "$4" | tr '\n' '|' | sed 's/||*/|/g'); }

r=$(repo beta); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1
commit "$r" "feat: groups keep their own money"; commit "$r" "fix: the amount stays"; commit "$r" "build: bump Room"
tag "$r" v0.1.0-rc.2
check "a beta lists its own features and fixes, and not build changes" \
  "### New|- Groups keep their own money.|### Fixed|- The amount stays.|" "$(notes "$r" beta 0.1.0-rc.2 v0.1.0-rc.2)"

r=$(repo breaking); commit "$r" "feat: one"; tag "$r" v1.0.0; commit "$r" "feat!: backups change shape"; tag "$r" v2.0.0-rc.1
check "a breaking feature is in the notes" "### New|- Backups change shape.|" "$(notes "$r" beta 2.0.0-rc.1 v2.0.0-rc.1)"

r=$(repo breaking-other); commit "$r" "feat: one"; tag "$r" v1.0.0; commit "$r" "build!: Android 8 is no longer supported"; tag "$r" v2.0.0-rc.1
check "a breaking change of another type is in the notes" "### New|- Android 8 is no longer supported.|" "$(notes "$r" beta 2.0.0-rc.1 v2.0.0-rc.1)"

r=$(repo test-only); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1
commit "$r" "fix: a flaky test" "" core/src/commonTest/ATest.kt; tag "$r" v0.1.0-rc.2
check "a fix that only touches tests is left out" "Dependency and build updates only. Nothing changes in the app.|" "$(notes "$r" beta 0.1.0-rc.2 v0.1.0-rc.2)"

r=$(repo production)
commit "$r" "feat: one"; tag "$r" v0.1.0; commit "$r" "feat: two"; tag "$r" v0.2.0-rc.1
commit "$r" "fix: three"; tag "$r" v0.2.0-rc.2; tag "$r" v0.2.0
check "production lists everything since the last production release, across its candidates" \
  "### New|- Two.|### Fixed|- Three.|" "$(notes "$r" production 0.2.0 v0.2.0)"

r=$(repo first-production); commit "$r" "feat: one"; commit "$r" "fix: two"; tag "$r" v0.1.0
check "the first production release lists everything before it" \
  "### New|- One.|### Fixed|- Two.|" "$(notes "$r" production 0.1.0 v0.1.0)"

finish
