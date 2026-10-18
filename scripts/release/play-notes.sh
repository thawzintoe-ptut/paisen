#!/usr/bin/env bash
# Writes Play's release notes, whatsnew/whatsnew-en-US, from notes.md. Play shows plain text and
# takes 500 characters at most, so headings and emphasis go, and long notes keep whole lines plus
# a link to the full notes on GitHub.
set -euo pipefail

notes=${1:?usage: play-notes.sh <notes.md> <full-notes-url>}
url=${2:?usage: play-notes.sh <notes.md> <full-notes-url>}
limit=500
more="Full notes: $url"

plain=$(sed -E 's/^#+ +//; s/[*`]//g' "$notes")
mkdir -p whatsnew
out=whatsnew/whatsnew-en-US

if (( ${#plain} <= limit )); then
  printf '%s\n' "$plain" > "$out"
  exit 0
fi

budget=$(( limit - ${#more} - 2 ))
kept=""
while IFS= read -r line; do
  (( ${#kept} + ${#line} + 1 <= budget )) || break
  kept+="$line"$'\n'
done <<< "$plain"
printf '%s\n%s\n' "$kept" "$more" > "$out"
