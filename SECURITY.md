# Security policy

## Report a problem privately

Please do not open a public issue for a security problem.

Report it through GitHub instead:
[**Report a vulnerability**](https://github.com/thawzintoe-ptut/PaiSen/security/advisories/new).
Only the maintainer can see the report. If you cannot use GitHub, email developer.ptut@gmail.com.

Please include:

- what an attacker can do, and what they need first (an account, a group invite, the phone)
- the steps to reproduce it, or a proof of concept
- the app version and platform, if it is in the app

You will get a reply within 7 days. Please give a fix time before you publish details. Once it is
fixed, you will be credited in the release notes, unless you would rather not be.

## Supported versions

Only the latest release gets security fixes.

## In scope

- the Android and iOS apps in this repository
- the Firestore security rules in [`firebase/firestore.rules`](firebase/firestore.rules)
- the push server in [`firebase/push-worker`](firebase/push-worker)

Examples: reading or changing a shared group you are not a member of, getting a push sent to a
group you are not in, data leaving the phone that [PRIVACY.md](PRIVACY.md) says stays there.

## Out of scope

- attacks that need a phone that is already unlocked, rooted or jailbroken
- the Firebase web API key in the app. It is public by design; the security rules protect the data.
- reports from automated scanners with no working attack
- denial of service and rate-limit tests against the live servers. Please do not run them.
