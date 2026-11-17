#!/usr/bin/env bash
# Checks commits.sh, the one reading of a commit, and the commit-msg hook that keeps titles readable
# by it. Run from anywhere; CI runs it too.
set -euo pipefail

# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/testlib.sh"
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/commits.sh"

check "feat: is a feature" feat "$(commit_type "feat: a new screen")"
check "a scope and a capital still read as a feature" feat "$(commit_type "Feat(shared): a new screen")"
check "fix! is a fix" fix "$(commit_type "fix!: amounts round the other way")"
check "build: is neither" other "$(commit_type "build: bump Room")"
check "a prefix with no space is not a type" other "$(commit_type "feat:no space")"
check "a title with no type is neither" other "$(commit_type "add git ignore")"

breaks() { if commit_breaks "$@"; then echo yes; else echo no; fi; }
check "type! breaks" yes "$(breaks "refactor!: the backup format")"
check "a BREAKING CHANGE line breaks" yes "$(breaks "fix: amounts" "BREAKING CHANGE: old backups no longer import")"
check "a plain feature does not break" no "$(breaks "feat: a new screen" "Some body text.")"

r=$(repo scenes)
commit "$r" "fix: a flaky test" "" core/src/commonTest/ATest.kt
check "a commit that only touches tests is behind the scenes" yes "$(cd "$r" && behind_the_scenes HEAD && echo yes || echo no)"
commit "$r" "fix: the amount" "" core/src/commonMain/Amount.kt
check "a commit that touches app code is not" no "$(cd "$r" && behind_the_scenes HEAD && echo yes || echo no)"
commit "$r" "chore: nothing in it"
check "an empty commit counts as a real change" no "$(cd "$r" && behind_the_scenes HEAD && echo yes || echo no)"

hook="$release_dir/../../.githooks/commit-msg"
title() {
  printf '%s\n\n# Please enter the commit message.\n' "$1" > "$work/MSG"
  if "$hook" "$work/MSG" 2> /dev/null; then echo pass; else echo refused; fi
}
check "the hook passes a typed title" pass "$(title "fix: the add sheet keeps its amount")"
check "the hook passes a scope and a breaking mark" pass "$(title "feat(shared)!: groups keep their money")"
check "the hook passes the titles Git writes" pass "$(title "Revert \"feat: one\"")"
check "the hook refuses a title with no type" refused "$(title "add git ignore")"
check "the hook refuses a type with no space after it" refused "$(title "fix:no space")"

finish
