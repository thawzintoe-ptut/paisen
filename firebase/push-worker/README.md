# paisen-push

A Cloudflare Worker with one route, `POST /notify`. A phone calls it right after it writes a change
to a shared group. The Worker checks the phone's Firebase ID token, checks the caller is a member of
the group, reads the other members' phones from `devices/`, and sends one Firebase Cloud Messaging
(FCM) message per phone in that phone's language.

No runtime dependencies: WebCrypto checks the ID token and signs the service-account JWT, and
Firestore and FCM are called over REST.

Request: `Authorization: Bearer <Firebase ID token>` and a JSON body of at most 8 KB. The structured
shape names what happened, never a sentence:

```json
{ "groupId": "…", "kind": "paid", "about": "<payment id>", "from": "<member id>", "to": "<member id>", "amount": 32000, "currency": "THB" }
```

`kind` is one of the names in `src/notice.ts`; ids match `[A-Za-z0-9_-]{1,128}`, `amount` is whole
minor units. Each phone gets them as FCM data with `by` (the sender's uid), to word the sentence from
its own copy of the group. Until an app build does, and for builds up to v0.4.0-rc.1, `body` is
"1 new" in the phone's language.

Builds up to v0.4.0-rc.1 send the old shape, `{ "groupId", "title", "body": { "en", "my", "th"? } }`.
While `LEGACY_NOTICES = "accept"` it still goes out, with links and hidden characters removed and cut
to 160 characters. Set `"refuse"` once no such build is in use; it then gets 400.

The notice's title is the group's name as Firestore stores it.

Reply: `{ "sent": n }`. Status 400 for a bad body, 401 for a bad or missing token, 403 when the caller
is not a member, 404 when the group does not exist, 405 for any method but POST, 410 for a deleted
group, 413 for a body over 8 KB, 429 past a rate limit, 503 when a limiter binding is missing.

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
npx wrangler dev     # local run; put FIREBASE_SERVICE_ACCOUNT='{...}' in .dev.vars (gitignored here),
                     # and ALLOW_NO_LIMITER=true there if the limiters are not available locally
```

## Files

- `src/index.ts`: the route, membership check, fan-out.
- `src/notice.ts`: both body shapes, the old shape's clean-up, the FCM data fields.
- `src/limits.ts`: the per-address and per-account limiters, the 8 KB body cap, the short cache of
  refused account and group pairs.
- `src/auth.ts`: ID token check against Google's `securetoken` certificates, service-account
  access token, both cached in the isolate.
- `src/firestore.ts`: `groups/{gid}` read, `devices` query in chunks of 30 capped at 5 per account,
  device delete.
- `src/fcm.ts`: one FCM HTTP v1 message per device, sent to its Firebase installation ID (`fid`);
  an ID FCM rejects marks the device dead.
- `wrangler.toml`: name, `FIREBASE_PROJECT_ID`, `LEGACY_NOTICES`, both limiters. The secret is never
  in this file.
