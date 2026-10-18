# Shared groups backend (#187–#191)

Shared groups sync through Firebase: Auth for sign-in, Firestore for the group log, Hosting for
invite links. The app talks to all three over REST. The one Firebase SDK in the apps is Messaging,
for push (section 8).

Without `firebase.properties` the app builds and runs, and shared groups stay on this phone.

## Account

Create the project **only under developer.ptut@gmail.com**. Do not use another Google account.

## 1. Project and Firestore

1. Firebase console → Add project. Turn Google Analytics **off** (the app sends no analytics).
2. Build → Firestore Database → Create database → production mode.
   Pick the region once; it cannot change later. `asia-southeast1` (Singapore) is closest to Thailand.
3. Put that region's plain name in `region=` (the privacy notice shows it, for example `Singapore`).

## 2. Sign-in providers

Build → Authentication → Sign-in method:

- **Google**: enable it. Firebase creates a *Web client* OAuth ID; copy it into `googleWebClientId=`.
- **Apple**: enable it and save. Leave the Services ID and the OAuth code flow fields empty: they are
  for web and Android, and only iOS offers Apple here.

Settings → Authorized domains must keep `localhost`. The REST sign-in sends it as its return address.

## 3. OAuth clients

Google Cloud console (same project) → APIs & Services → Credentials → Create OAuth client ID:

- **iOS**, bundle ID `com.ptut.expense` → `googleIosClientId=`.
- **Android**, package `com.ptut.expense`, with the SHA-1 of each signing key (debug and release).
  Credential Manager needs it to exist; the app never names it.

## 4. Apps and API key

Project settings → General → Add app (Android `com.ptut.expense`, iOS `com.ptut.expense`).
Copy *Web API key* into `apiKey=` and the project ID into `projectId=`.
The key shows only after Authentication is set up. Leave its application restriction at *None*
in Google Cloud: the app calls REST without Android or iOS app headers, so a restricted key fails.
`androidApp/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist` are kept locally
(gitignored) as the source of the client IDs: the web client is the json's `client_type` 3, the iOS
client is the plist's `CLIENT_ID`. The app reads neither file, and no Google Services plugin is added.

## 5. Invite links (Hosting)

1. Build → Hosting → Get started. The default domain is `<projectId>.web.app`; put it in `linkDomain=`.
2. `hosting/.well-known/assetlinks.json`: replace the placeholder with the SHA-256 of each Android
   signing key (`keytool -list -v -keystore <keystore>`).
3. `hosting/.well-known/apple-app-site-association`: replace `REPLACE_WITH_TEAM_ID` with the Apple
   Team ID.
4. Xcode → iosApp target → Build Settings → `PAISEN_LINK_DOMAIN` = the same domain. The entitlements
   file reads it for Associated Domains. In the Apple Developer portal, turn on *Sign in with Apple*
   and *Associated Domains* for `com.ptut.expense` (automatic signing does this on first device build).

## 6. Deploy rules and hosting

The project is `paisen-expense`; `.firebaserc` already names it.

```bash
npm install -g firebase-tools
export PATH=/opt/homebrew/opt/node@22/bin:$PATH   # firebase-tools fails on Node 26
firebase login            # as developer.ptut@gmail.com
cd firebase
firebase firestore:databases:create "(default)" --location=asia-southeast1
firebase deploy --only firestore:rules,firestore:indexes,hosting
```

Create the database before the first deploy. A Firestore deploy on a project with no database
creates one in `nam5` (US) without asking, and a location never changes.

## 7. firebase.properties

Copy `firebase.properties.example` to `firebase.properties` at the repo root and fill it in.
It is gitignored. Gradle turns it into `FirebaseConfig` at build time; Android's App Link host comes
from `linkDomain` too.

The API key and client IDs are public identifiers, not secrets. Firestore rules
(`firestore.rules`) are what keep each group readable only by its members.

## 8. Push notifications

When a member changes a shared group, their phone tells a small Cloudflare Worker
(`push-worker/`), and the Worker sends one Firebase Cloud Messaging push to each other member's
phone, in that phone's language. Firestore holds one `devices/{token}` document per registered phone
(`uid`, `platform`, `language`, `updatedAt`); the rules let a phone write and list only its own
account's documents. The Worker reads them with a service-account key it holds as a secret. The app
holds no sending credentials. Nothing here needs the Blaze plan.

1. Google Cloud console → APIs & Services → Library: enable **Firebase Cloud Messaging API (V1)**
   for `paisen-expense`.
2. Firebase console → Project settings → Service accounts → **Generate new private key**. The JSON
   file is the Worker's secret only: never in the repo, never in `firebase.properties`.
3. Deploy the Worker as `push-worker/README.md` says: `npm install`, `npx wrangler login`,
   `npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < <the JSON file>`, `npx wrangler deploy`.
4. Put the URL wrangler prints in `pushUrl=`. Empty means no push: the app registers no device and
   sends no notice.
5. Android needs two more values, so it can start Firebase without `google-services.json`:
   `androidAppId=` from Project settings → General → Your apps → the Android app's *App ID*
   (`1:601753319449:android:…`), and `senderId=` from Project settings → Cloud Messaging →
   *Sender ID* (the project number, `601753319449`).
6. Deploy the rules (section 6); the `devices` block is part of `firestore.rules`.

iOS, once the Apple Developer membership is paid:

- Apple Developer portal → Identifiers → `com.ptut.expense` → turn on **Push Notifications**
  (automatic signing in Xcode does this on the first device build).
- Certificates, Identifiers & Profiles → Keys → new key with **Apple Push Notifications service
  (APNs)**; download the `.p8` once, note its Key ID.
- Firebase console → Project settings → Cloud Messaging → Apple app configuration → upload the
  `.p8` with the Key ID and the Team ID. FCM then relays to APNs; the app needs no Apple key.
- Then set `iosPushReady=true` in `firebase.properties` and rebuild. Until then an iPhone falls back to a
  background refresh (`IosSharedRefresh.kt`): iOS wakes the app when it chooses, minutes or hours
  apart, and the app posts a local notification for what other members added.

## What leaves the phone

Only shared groups: the group's name and type, member names, shared expenses and payments, and who
added each one. Your own transactions, slips, reports and the links between them never sync.
With push set up, the phone also stores its push token and app language in `devices/`, so it can be
told when someone changes a group.
The source rules (`verifySourceRules`) keep every network import under `sync/`, and
`:androidApp:verifyNoTelemetryUpload` fails the build if an SDK's telemetry backend comes back.
