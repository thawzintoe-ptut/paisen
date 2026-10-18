# Contributing to PaiSeN

Bug reports, ideas and pull requests are welcome. For anything bigger than a small fix, please
open an issue first, so we agree on the change before you spend time on it. Report security
problems privately, as [SECURITY.md](SECURITY.md) describes.

## How we branch: trunk-based

`main` is the only long-lived branch, and it is always releasable.

1. **Keep branches short.** Open the pull request within 2 days of creating the branch. One topic
   per branch. If it cannot be finished in 2 days, split it.
2. **Never let `main` go red.** If CI fails on `main`, fixing it comes before any other work.
3. **Unfinished work ships switched off, not on a long branch.** Wire it into debug builds only:
   register it under `if (build.debug)` in the app's Koin modules
   (`composeApp/src/commonMain/kotlin/com/ptut/expense/di/`), or put Android-only code in
   `androidApp/src/debug`. A release build then never reaches it.
4. **History stays linear.** Pull requests are merged with squash or rebase. There are no merge
   commits.

Branch names say what the branch does:

```
feat/slip-banner
fix/amount-rounding
docs/privacy-wording
chore/bump-agp
```

## Set up

You need JDK 17 and Android Studio. For the iOS app you also need a Mac with Xcode. The
[README](README.md#build-it-yourself) has the build steps.

After you clone your fork, turn on the repo's hooks and settings:

```bash
git config core.hooksPath .githooks
git config commit.template .gitmessage
git config pull.rebase true
git config push.autoSetupRemote true
```

| Hook | Runs | Blocks |
|---|---|---|
| `pre-commit` | instantly | `local.properties`, signing keys, raw slip images, files over 1 MB, and account, card, phone or ID numbers in test files |
| `pre-push` | a minute or more | a push whose source checks, network checks, bundled fonts or host tests fail |

`pre-push` does not run the iOS tests, because they take minutes. If you changed shared code or
iOS code, run them yourself on a Mac:

```bash
./gradlew iosSimulatorArm64Test
```

## Day to day

```bash
git switch -c fix/amount-rounding
# small commits
git pull --rebase origin main     # stay close to main
git push                          # pre-push runs the checks
```

Then open a pull request into `main`. CI runs the Android and iOS jobs, and both must pass before
the pull request can be merged.

## Commit messages and pull request titles

```
<type>: <what changed, in the present tense, no full stop>
```

Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `build`. Keep the title under 72
characters, and say what changed and why.

The pull request title becomes the commit message on `main` when it is squashed, so write it in the
same format. If the change is visible to users, add a line under `[Unreleased]` in
[CHANGELOG.md](CHANGELOG.md).

## License

PaiSeN is licensed under [GPL-3.0](LICENSE). By sending a pull request, you agree that your
contribution is licensed under GPL-3.0 too.

## For the maintainer

### Releases

Releases are tagged on `main`. There are no release branches until one is needed.

1. Set the version in `androidApp/build.gradle.kts` (`versionName`, `versionCode`) and in Xcode
   (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`).
2. In `CHANGELOG.md`, rename `[Unreleased]` to the version and date, and add a new empty
   `[Unreleased]` above it.
3. Commit, push, and wait for CI to pass.
4. Tag and push the tag:

   ```bash
   git tag -a v0.1.0 -m "v0.1.0"
   git push origin v0.1.0
   ```

5. Create the GitHub release from the tag, with the changelog section as its notes.

**Hotfix for a version already in the store**, while `main` has work that is not ready: branch
from the tag, fix, tag, and bring the fix back to `main`.

```bash
git switch -c release/0.1 v0.1.0
# fix and commit
git tag -a v0.1.1 -m "v0.1.1"
git push origin release/0.1 v0.1.1
git switch main
git cherry-pick <fix-commit>
```

Delete `release/0.1` when that version is no longer supported.

### Branch protection

`main` is protected by two rulesets. Their JSON files are in [`.github/rulesets`](.github/rulesets).
To set them up again, go to **Settings → Rules → Rulesets → New ruleset → Import a ruleset** and
import each file.

| Ruleset | Rules | Who can bypass |
|---|---|---|
| `main: no rewrites` | no deletion, no force push, linear history | nobody |
| `main: checks` | pull request (0 approvals, squash or rebase), both CI jobs green | repository admins, so the maintainer can push small fixes directly |
