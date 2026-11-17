#!/usr/bin/env bash
# Checks next-version.sh against throwaway repositories. Run from anywhere; CI runs it too.
set -euo pipefail

# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/testlib.sh"
script="$release_dir/next-version.sh"

# "(refused)" is exit 1, "(nothing)" is exit 3: the two ways a run makes no tag.
expect() {
  local name=$1 dir=$2 want=$3; shift 3
  local got status=0
  got=$(cd "$dir" && "$script" "$@" 2> /dev/null) || status=$?
  case "$status" in
    0) ;;
    1) got="(refused)" ;;
    3) got="(nothing)" ;;
    *) got="(exit $status)" ;;
  esac
  check "$name" "$want" "$got"
}

# First release

r=$(repo first-feature); commit "$r" "feat: slips become rows"
expect "a first feature starts at 0.1.0" "$r" v0.1.0-rc.1 --first

r=$(repo first-fix); commit "$r" "fix: a crash on launch"
expect "a first fix starts at 0.0.1" "$r" v0.0.1-rc.1 --first

r=$(repo no-tags); commit "$r" "feat: one"
expect "no tag and no --first is refused, since the tags may only be missing" "$r" "(refused)"

r=$(repo shallow-source); commit "$r" "feat: one"; tag "$r" v0.3.0; commit "$r" "fix: two"
git clone -q --depth 1 "file://$r" "$work/shallow" 2> /dev/null
git -C "$work/shallow" config core.hooksPath /dev/null
expect "a shallow clone is refused" "$work/shallow" "(refused)"

# Candidates

r=$(repo fix-after-rc); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "fix: two"
expect "a fix after a candidate is the next candidate" "$r" v0.1.0-rc.2

r=$(repo docs-after-rc); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "docs: notes"
expect "docs after a candidate are the next candidate" "$r" v0.1.0-rc.2

r=$(repo feat-after-rc); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "feat: two"
expect "a feature after a candidate starts the next minor" "$r" v0.2.0-rc.1

r=$(repo feat-between-rcs)
commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "feat: two"; tag "$r" v0.1.0-rc.2; commit "$r" "fix: three"
expect "a feature anywhere after the first candidate still starts the next minor" "$r" v0.2.0-rc.1

r=$(repo nothing-new); commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1
expect "nothing since the candidate makes no tag" "$r" "(nothing)"

r=$(repo released-already)
commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "feat: two"; tag "$r" v0.1.0-rc.2
expect "a commit that is already a candidate is never tagged again" "$r" "(nothing)"

r=$(repo after-production); commit "$r" "feat: one"; tag "$r" v1.2.3; commit "$r" "feat: two"
expect "a feature after a release is the next minor" "$r" v1.3.0-rc.1

r=$(repo fix-after-production); commit "$r" "feat: one"; tag "$r" v1.2.3; commit "$r" "fix: two"
expect "a fix after a release is the next patch" "$r" v1.2.4-rc.1

r=$(repo bang-after-production); commit "$r" "feat: one"; tag "$r" v1.2.3; commit "$r" "feat!: backups change shape"
expect "feat! after 1.0 is the next major" "$r" v2.0.0-rc.1

r=$(repo footer-after-production); commit "$r" "feat: one"; tag "$r" v1.2.3; commit "$r" "fix: two" "BREAKING CHANGE: old backups no longer import"
expect "a BREAKING CHANGE line after 1.0 is the next major" "$r" v2.0.0-rc.1

r=$(repo breaking-below-one); commit "$r" "feat: one"; tag "$r" v0.3.0; commit "$r" "feat!: two"
expect "a breaking change below 1.0 is a minor" "$r" v0.4.0-rc.1

r=$(repo forced-major); commit "$r" "feat: one"; tag "$r" v0.9.0-rc.1; commit "$r" "fix: two"
expect "--bump major chooses 1.0.0" "$r" v1.0.0-rc.1 --bump major

r=$(repo hotfix-line)
commit "$r" "feat: one"; tag "$r" v1.0.0
git -C "$r" checkout -q -b release/1.0
commit "$r" "fix: on the old line"
git -C "$r" checkout -q -
commit "$r" "feat: two"; tag "$r" v1.1.0-rc.1
git -C "$r" checkout -q release/1.0
expect "a hotfix branch sees only its own line's tags" "$r" v1.0.1-rc.1

# --auto: what CI tags after a passing push

r=$(repo auto-fix); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "fix: two"
expect "--auto tags a fix" "$r" v0.2.0-rc.2 --auto

r=$(repo auto-feat); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "feat: two"
expect "--auto tags a feature as the next minor" "$r" v0.3.0-rc.1 --auto

r=$(repo auto-docs); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "docs: notes"; commit "$r" "build: bump a plugin"
expect "--auto makes no tag for docs and build changes" "$r" "(nothing)" --auto

r=$(repo auto-nothing); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1
expect "--auto makes no tag when nothing was committed" "$r" "(nothing)" --auto

r=$(repo auto-test-only); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "fix: a flaky test" "" core/src/commonTest/ATest.kt
expect "--auto makes no tag for a fix that only touches tests" "$r" "(nothing)" --auto

r=$(repo auto-capital); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "Feat: a new screen"
expect "--auto reads a type in any case" "$r" v0.3.0-rc.1 --auto

r=$(repo auto-docs-only-fix); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.1; commit "$r" "fix: a typo in the readme" "" README.md
expect "--auto makes no tag for a fix that only touches docs" "$r" "(nothing)" --auto

# --final: production from the latest candidate

r=$(repo final-clean); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.3; commit "$r" "docs: notes"
expect "--final promotes the latest candidate" "$r" v0.2.0 --final

r=$(repo final-after-code); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.3; commit "$r" "build: bump Room"
expect "--final still names the candidate when code landed after it, since it tags the candidate's commit" "$r" v0.2.0 --final

r=$(repo final-frozen)
commit "$r" "feat: one"; tag "$r" v0.1.0-rc.1; commit "$r" "feat: two"; tag "$r" v0.1.0-rc.2
expect "--final refuses a candidate holding a feature from after the freeze" "$r" "(refused)" --final

r=$(repo final-no-rc); commit "$r" "feat: one"; tag "$r" v0.2.0
expect "--final refuses when the latest tag is a release" "$r" "(refused)" --final

# --latest

r=$(repo latest); commit "$r" "feat: one"; tag "$r" v0.2.0-rc.9; commit "$r" "fix: two"; tag "$r" v0.2.0-rc.10
expect "--latest orders rc.10 above rc.9" "$r" v0.2.0-rc.10 --latest

finish
