# shellcheck shell=bash
# The one reading of a commit, sourced by next-version.sh and notes.sh, so the version a release
# takes and the notes it ships with never disagree about what a commit is.

# A title's type: feat, fix or other. Any case counts, so "Feat:" is a feature.
commit_type() {
  local title
  title=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  if [[ "$title" =~ ^feat(\([^\)]*\))?!?:\  ]]; then
    echo feat
  elif [[ "$title" =~ ^fix(\([^\)]*\))?!?:\  ]]; then
    echo fix
  else
    echo other
  fi
}

# True for a breaking change: "type!:" in the title [$1], or a BREAKING CHANGE: line in the body [$2].
commit_breaks() {
  local title
  title=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  [[ "$title" =~ ^[a-z]+(\([^\)]*\))?!:\  ]] || grep -qE '^BREAKING[ -]CHANGE: ' <<< "${2:-}"
}

# True when commit [$1] changes only tests, CI, scripts or docs: nothing a user runs. A commit with
# no files, such as an empty one, counts as a real change.
behind_the_scenes() {
  local files
  files=$(git show --name-only --format= "$1")
  [[ -n "$files" ]] && ! grep -qvE '(/[A-Za-z]*[Tt]est/|Test\.kt$|/test/|^\.github/|^scripts/|^docs/|\.md$)' <<< "$files"
}

# Each commit in range [$1], oldest first, as "sha<US>title<US>body<RS>" for read -d $'\x1e'.
commits_in() {
  git log --reverse --no-merges --format='%H%x1f%s%x1f%b%x1e' "$1"
}
