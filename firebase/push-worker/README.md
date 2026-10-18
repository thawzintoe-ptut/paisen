# paisen-push

A Cloudflare Worker with one route, `POST /notify`. A phone calls it right after it writes a change
to a shared group. The Worker checks the phone's Firebase ID token, checks the caller is a member of
the group, reads the other members' phones from `devices/`, and sends one Firebase Cloud Messaging
(FCM) message per phone in that phone's language. The phone builds both sentences; the Worker holds
no copy.

No runtime dependencies: WebCrypto checks the ID token and signs the service-account JWT, and
Firestore and FCM are called over REST.

Request: `Authorization: Bearer <Firebase ID token>` and a JSON body

```json
{ "groupId": "…", "title": "Trip", "body": { "en": "Mali added Dinner · ฿320", "my": "…" } }
```

The notice's title is the group's name as Firestore stores it; the phone's `title` is used only when
the group has no name. The sentences in `body` come from the member's phone.

Reply: `{ "sent": n }`. Status 401 for a bad or missing token, 403 when the caller is not a member,
400 for a bad body, 404 when the group does not exist, 405 for any method but POST.

## Before the first deploy

- Google Cloud console → APIs & Services → Library: **Firebase Cloud Messaging API (V1)** must be
  enabled for `paisen-expense`.
- Firebase console → Project settings → Service accounts → **Generate new private key**. Keep the
  JSON file outside the repo; it goes only into the Worker secret below.
- A Cloudflare account (the free plan is enough).

## Deploy

Run these in `firebase/push-worker/`:

```bash
npm install
npx wrangler login
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < ~/Downloads/paisen-expense-firebase-adminsdk.json
npx wrangler deploy
```

Point the `<` at the JSON file the Firebase console downloaded. Without the redirect the command
prompts for the value, and the JSON must then go in as one line.

`wrangler deploy` prints the Worker's URL, like `https://paisen-push.<account>.workers.dev`. Put it
in `firebase.properties` at the repo root:

```
pushUrl=https://paisen-push.<account>.workers.dev
```

Leave `pushUrl=` empty for a build without push: the app then registers no device and sends no
notice.

To replace the key later, generate a new one in the Firebase console, run `secret put` again and
deploy; the Worker reads the secret at each request.

## Type check and local run

```bash
npx tsc --noEmit
npx wrangler dev     # local run; put FIREBASE_SERVICE_ACCOUNT='{...}' in .dev.vars (gitignored here)
```

## Files

- `src/index.ts`: the route, body check, membership check, fan-out.
- `src/auth.ts`: ID token check against Google's `securetoken` certificates, service-account
  access token, both cached in the isolate.
- `src/firestore.ts`: `groups/{gid}` read, `devices` query in chunks of 30, device delete.
- `src/fcm.ts`: one FCM HTTP v1 message per device, sent to its Firebase installation ID (`fid`);
  an ID FCM rejects marks the device dead.
- `wrangler.toml`: name, `FIREBASE_PROJECT_ID`. The secret is never in this file.
