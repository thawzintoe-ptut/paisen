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

Releases are tags on `main`. Pushing a tag starts the **Release** workflow: it runs the same
checks as a pull request, builds a signed APK and AAB with the version taken from the tag, and
creates the GitHub release with the APK. There are no release branches until one is needed.

| Tag | Release | Notes come from | versionCode |
|---|---|---|---|
| `v0.2.0-rc.1` | pre-release (beta) | `## [Unreleased]` | 20001 |
| `v0.2.0` | release (production) | `## [0.2.0]` | 20099 |

versionCode = MAJOR × 1,000,000 + MINOR × 10,000 + PATCH × 100 + the rc number (99 for
production). Don't edit `versionName` or `versionCode` in `androidApp/build.gradle.kts`.

1. For a beta, check that `## [Unreleased]` in `CHANGELOG.md` says what changed. For production,
   rename `[Unreleased]` to `[X.Y.Z] - YYYY-MM-DD` and add a new empty `[Unreleased]` above it.
2. Commit, push, and wait for CI to pass.
3. Tag and push the tag:

   ```bash
   git tag -a v0.2.0-rc.1 -m "v0.2.0-rc.1"
   git push origin v0.2.0-rc.1
   ```

4. The APK is on the GitHub release. The AAB for Play is in the workflow run's artifacts (7 days).
5. iOS keeps its own version: set `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in Xcode.
   Before an iOS release, run `./gradlew :composeApp:verifyNoNetworkSymbols` on your Mac. It links
   the release framework (about 7 minutes) and checks it uses no networking API outside sync.

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

`main` and the release tags are protected by three rulesets. Their JSON files are in [`.github/rulesets`](.github/rulesets).
To set them up again, go to **Settings → Rules → Rulesets → New ruleset → Import a ruleset** and
import each file.

| Ruleset | Rules | Who can bypass |
|---|---|---|
| `main: no rewrites` | no deletion, no force push, linear history | nobody |
| `main: checks` | pull request (0 approvals, squash or rebase), both CI jobs green | repository admins, so the maintainer can push small fixes directly |
| `release tags` | `v*` tags can't be created, moved or deleted | repository admins, so only the maintainer can start a release |
