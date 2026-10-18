## What and why

<!-- One topic per pull request. Link the issue, for example: Closes #12 -->

## How you checked it

<!-- The commands you ran, and screenshots or a short video for anything on screen. -->

## Checklist

- [ ] The title follows `<type>: <what changed>` (it becomes the commit on `main`)
- [ ] The branch is rebased on `main` and less than 2 days old
- [ ] `./gradlew spotlessCheck testAndroidHostTest` passes
- [ ] `./gradlew iosSimulatorArm64Test` passes, if shared or iOS code changed
- [ ] Unfinished work runs in debug builds only
- [ ] No keys, real account numbers or slip images
- [ ] `CHANGELOG.md` has a line under `[Unreleased]`, if users will notice the change
