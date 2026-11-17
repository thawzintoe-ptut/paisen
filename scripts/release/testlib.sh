# shellcheck shell=bash
# Throwaway repositories and checks for the release scripts' tests, sourced by each *.test.sh.

# shellcheck disable=SC2034 # read by the test files that source this one
release_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

# A fresh repository with no hooks and no signing, in its own folder.
repo() {
  local dir="$work/$1"
  git init -q "$dir"
  git -C "$dir" config user.name test
  git -C "$dir" config user.email test@example.invalid
  git -C "$dir" config commit.gpgsign false
  git -C "$dir" config tag.gpgSign false
  git -C "$dir" config core.hooksPath /dev/null
  echo "$dir"
}

# An empty commit, or one that writes [file] when given: commit <repo> <title> [body] [file]
commit() {
  if [[ -n "${4:-}" ]]; then
    mkdir -p "$(dirname "$1/$4")"
    echo "$2" >> "$1/$4"
    git -C "$1" add "$4"
    git -C "$1" commit -q -m "$2" ${3:+-m "$3"}
  else
    git -C "$1" commit -q --allow-empty -m "$2" ${3:+-m "$3"}
  fi
}

tag() { git -C "$1" tag -a "$2" -m "$2"; }

check() {
  local name=$1 want=$2 got=$3
  if [[ "$got" == "$want" ]]; then
    echo "ok   $name"
  else
    printf 'FAIL %s\n  wanted: %s\n  got:    %s\n' "$name" "$want" "$got"
    failures=$((failures + 1))
  fi
}

finish() {
  if (( failures )); then
    echo "$failures failed"
    exit 1
  fi
  echo "all passed"
}
