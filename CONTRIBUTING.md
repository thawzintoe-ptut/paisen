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
| `commit-msg` | instantly | a title that does not start with a type, since the type sets the next version |
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

## Dependency checksums

Gradle refuses any library or plugin whose SHA-256 is not in `gradle/verification-metadata.xml`.
After you change a version in `gradle/libs.versions.toml`, refresh the file from an empty Gradle
home, so it lists what a clean CI runner downloads, and commit it with the bump:

```bash
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 help :buildSrc:test \
  spotlessCheck testAndroidHostTest :androidApp:assembleRelease :androidApp:bundleRelease \
  :androidApp:lintDebug compileKotlinIosSimulatorArm64 :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  iosArm64ResolveResourcesFromDependencies
```

The last task pulls the iPhone builds' Compose resources, which only Xcode and the release
framework ask for. Then build the app once in Xcode, or with `xcodebuild` for the simulator.

A Mac never downloads the Linux `aapt2` that CI uses, so after an AGP bump add it to the `aapt2`
entry by hand: the `shasum -a 256` of `aapt2-<version>-linux.jar` from
`https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/<version>/`. Dependabot
cannot refresh the file, so its Gradle PRs carry the `needs-checksums` label and fail CI until you
run the command on the PR branch and push the result.

## Commit messages and pull request titles

```
<type>: <what changed, in the present tense, no full stop>
```

Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `build`, `style`, `ci`, `perf`. Add `!`
after the type (`feat!:`) for a change that breaks something people rely on. Keep the title under
72 characters, and say what changed and why.

The pull request title becomes the commit message on `main` when it is squashed, so write it in the
same format. The prefix sets the next version, and every `feat:` and `fix:` title, and every
breaking one, is a line in the release notes, so write those for the people who use the app. A
commit that only touches tests, CI, scripts or docs stays out of the notes and makes no tester build.

## License

PaiSeN is licensed under [GPL-3.0](LICENSE). By sending a pull request, you agree that your
contribution is licensed under GPL-3.0 too.

## For the maintainer

### Releases

Releases are tags on `main`, and the **Nightly Beta Release**, **Beta Tag** and **Production Tag**
workflows make them; nobody numbers a release by hand. The tag starts **Beta Release** (an rc tag)
or **Production Release**: it runs the same checks as a pull request, builds a signed APK and AAB
with the version taken from the tag, and creates the GitHub release with the APK. There are no
release branches until one is needed.

| Tag | Release | Notes come from | versionCode |
|---|---|---|---|
| `v0.2.0-rc.1` | pre-release (beta) | `feat:` and `fix:` titles since the tag before it | 20001 |
| `v0.2.0` | release (production) | `feat:` and `fix:` titles since the last production release | 20099 |

versionCode = MAJOR × 1,000,000 + MINOR × 10,000 + PATCH × 100 + the rc number (99 for
production). Don't edit `versionName` or `versionCode` in `androidApp/build.gradle.kts`.

**Which number goes up** follows [SemVer](https://semver.org) and the commit prefixes, and
`scripts/release/next-version.sh` works it out:

| Since the last tag | The next tag |
|---|---|
| a `feat:` commit | MINOR: `0.2.0` → `0.3.0-rc.1` |
| only `fix:`, `style:`, `build:`, `docs:`, `test:`, `chore:` or `refactor:` | PATCH: `0.2.0` → `0.2.1-rc.1`, or the next rc of a candidate: `0.2.0-rc.1` → `0.2.0-rc.2` |
| `feat!:` or a `BREAKING CHANGE:` line, from `1.0.0` on | MAJOR: `1.4.0` → `2.0.0-rc.1` |

- A candidate freezes its version's features: a `feat:` after the first `vX.Y.Z-rc` makes the next
  tag `X.(Y+1).0-rc.1`, not another rc of `X.Y.Z`.
- Below `1.0.0` anything may change, so a breaking change is MINOR there. `1.0.0` is chosen on
  purpose with `--bump major`.
- `--final` prints `vX.Y.Z` for the latest candidate, and the tag goes on that candidate's own
  commit, so production ships exactly what testers had. It refuses when a feature landed between
  the version's first candidate and its latest one, since that feature belongs to the next minor.
- It refuses a shallow clone, and a commit no release tag reaches unless `--first` is given, rather
  than guess without the tags.

After the GitHub release, each target runs only when its repository variable is `true`
(**Settings → Secrets and variables → Actions → Variables**):

| Target | Tag | Variable | Needs |
|---|---|---|---|
| Firebase App Distribution, group `testers` | beta and production | `FIREBASE_ENABLED` | secret `FIREBASE_DISTRIBUTION_SA_JSON` in `release` |
| Unsigned iPhone IPA on the GitHub release, for SideStore or AltStore | beta and production | `IOS_SIDELOAD_ENABLED` | secret `GOOGLE_SERVICE_INFO_PLIST` in `release` |
| Firebase App Distribution for iPhone, group `testers` | beta | `IOS_ENABLED` and `FIREBASE_ENABLED` | the paid Apple Developer Program, and in `release` the secrets `IOS_DIST_CERT_P12_BASE64`, `IOS_DIST_CERT_PASSWORD`, `IOS_ADHOC_PROFILE_BASE64` and `GOOGLE_SERVICE_INFO_PLIST` |
| Play track in `PLAY_BETA_TRACK` (`internal` or `alpha`) | beta | `PLAY_ENABLED` | secret `PLAY_SERVICE_ACCOUNT_JSON` in `release`, from an account that may only release to testing tracks; `PLAY_STATUS` is `draft` until the app is published |
| Play production, 20% staged rollout | production | `PLAY_PRODUCTION_ENABLED` | environment `production` with you as required reviewer, holding its own `PLAY_SERVICE_ACCOUNT_JSON` from a separate production account |

Play gets the notes cut to 500 characters, with a link to the full notes on GitHub.
`PLAY_BETA_TRACK` must be `internal`, `alpha` or `beta`; the workflow refuses anything else.

1. **Candidates are nightly.** At 02:17 Bangkok time, **Nightly Beta Release** tags the next
   candidate on `main`'s tip when it holds a `feat:` or `fix:` since the last tag, and starts
   **Beta Release**. Testers get at most one build a day. Only docs, tests or build changes wait
   for the next night. When a required check on the tip failed or is still running, the run fails
   and tags nothing.
2. **Production is a button.** **Actions → Production Tag → Run workflow** tags the latest
   candidate as `vX.Y.Z` on its own commit and starts **Production Release**. Play production still
   waits for your approval in the `production` environment.
3. **A candidate by hand**, for a build-only change, for `1.0.0`, or before the night:
   **Actions → Beta Tag → Run workflow**, with a bump if the commits cannot say it. It refuses a
   commit where a required check failed.
   Without the workflows, the same tags come from a full clone with its tags:

   ```bash
   tag=$(scripts/release/next-version.sh)   # a candidate on this commit; --bump major for 1.0.0
   git tag -a "$tag" -m "$tag"
   git push origin "$tag"
   ```

   Production goes on the latest candidate's own commit:

   ```bash
   tag=$(scripts/release/next-version.sh --final)
   git tag -a "$tag" -m "$tag" "$(scripts/release/next-version.sh --latest)^{}"
   git push origin "$tag"
   ```

   If a tag workflow made the tag but the release did not start, the run's summary says so: start
   it by hand with **Actions → Beta Release** (or **Production Release**) **→ Run workflow → Use
   workflow from** that tag.
4. The APK is on the GitHub release, and so is the unsigned IPA when `IOS_SIDELOAD_ENABLED` is on.
   The AAB for Play is in the workflow run's artifacts (7 days). To send a production APK to the
   Firebase testers again, run **Deploy Production Build to Firebase** on its `vX.Y.Z` tag. It
   takes the APK from the GitHub release and checks its build provenance first.
5. With `IOS_ENABLED`, a beta tag also builds an ad hoc IPA: `v0.2.0-rc.1` becomes version 0.2.0,
   build 20001. Only the iPhones in the ad hoc profile can install it. To add a tester's iPhone,
   register it in the Apple Developer portal, make the profile again, and replace
   `IOS_ADHOC_PROFILE_BASE64`. A build from Xcode keeps the `MARKETING_VERSION` (0.1.0) and
   `CURRENT_PROJECT_VERSION` (1) set there, as a local Android build keeps 0.1.0 (1). Before an iOS release, run `./gradlew :composeApp:verifyNoNetworkSymbols` on your Mac. It links
   the release framework (about 7 minutes) and checks it uses no networking API outside sync.

**Hotfix for a version already in the store**, while `main` has work that is not ready: branch
from the tag, fix, and bring the fix back to `main`. The nightly candidate is cut on `main` only, so
on the branch use the buttons: **Beta Tag → Run workflow → Use workflow from** `release/0.1`, then
**Production Tag** from the same branch once testers are happy. The rule reads only that branch's tags, so the next tag
is `v0.1.1-rc.1`.

```bash
git switch -c release/0.1 v0.1.0
# fix and commit
git push origin release/0.1
git switch main
git cherry-pick <fix-commit>
```

Delete `release/0.1` when that version is no longer supported.

### Branch protection

`main`, the `release/*` branches and the release tags are protected by four rulesets. Their JSON files are in [`.github/rulesets`](.github/rulesets).
To set them up again, go to **Settings → Rules → Rulesets → New ruleset → Import a ruleset** and
import each file.

| Ruleset | Rules | Who can bypass |
|---|---|---|
| `main: no rewrites` | no deletion, no force push, linear history | nobody |
| `main: checks` | pull request (0 approvals, squash or rebase), the five Pull Request and Unit Tests checks green | repository admins, so the maintainer can push small fixes directly |
| `release tags locked` | `v*` tags can't be moved or deleted | repository admins, so no workflow can rewrite or remove a release |
| `release branches locked` | `release/*` can't be created, pushed to or deleted; changes need a pull request | repository admins, so only the maintainer makes a hotfix branch |

The `release` environment holds the signing and Firebase keys. It has you as required reviewer and
accepts only `main` and `v*` tags, so no other branch can sign a build.

No ruleset blocks creating a `v*` tag. A repository owned by a personal account can't let GitHub
Actions bypass a ruleset, and the tag workflows have to create tags. They refuse to run from any
branch but `main` or `release/*`.
