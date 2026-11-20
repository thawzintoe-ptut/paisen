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
its own copy of the group. Builds up to v0.4.0-rc.1 cannot, so `body` is "1 new" in the phone's
language.

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
- A Cloudflare account (the free plan is enough).

## The Worker's own service account (M9)

The Worker reads and deletes Firestore documents and sends FCM messages; nothing more. It holds
its own key with only those rights, never the Firebase Admin SDK key, which can do anything in the
project.

1. Google Cloud console → IAM & Admin → Service accounts, project `paisen-expense` →
   **Create service account**. Name `paisen-push-worker`, description "Push Worker: Firestore
   reads and deletes, FCM sends". Grant no role on this screen; press **Done**.
2. IAM & Admin → IAM → **Grant access**. Principal: the new account's email
   (`paisen-push-worker@paisen-expense.iam.gserviceaccount.com`). Roles:
   - **Cloud Datastore User** (`roles/datastore.user`): `groups`, `devices` and `invites` reads,
     the dead-device delete, and the daily clean-up's deletes and `deletedAt` stamp.
   - **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`): it holds
     `cloudmessaging.messages.create`, the send permission. To narrow further, make a custom role
     with only `cloudmessaging.messages.create` and grant that instead.
   Save.
3. Service accounts → `paisen-push-worker` → Keys → **Add key** → Create new key → JSON. Keep the
   file outside the repo; it goes only into the Worker secret below.
4. Deploy (next section) with this file, send a test notice, and only then delete the old key:
   Service accounts → `firebase-adminsdk-…` → Keys → delete the key the Worker used (the one in
   `~/Downloads/paisen-expense-firebase-adminsdk.json`). Do not delete the account itself; Firebase
   uses it.

## Deploy

Run these in `firebase/push-worker/`, with Node 22 on PATH:

```bash
npm install
npx wrangler login
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < ~/Downloads/paisen-push-worker.json
npx wrangler deploy
```

Point the `<` at the key file from step 3. Without the redirect the command prompts for the value,
and the JSON must then go in as one line.

`wrangler deploy` prints the Worker's URL, like `https://paisen-push.<account>.workers.dev`, and
the cron trigger. Put the URL in `firebase.properties` at the repo root:

```
pushUrl=https://paisen-push.<account>.workers.dev
```

Leave `pushUrl=` empty for a build without push: the app then registers no device and sends no
notice.

To replace the key later, add a new key to `paisen-push-worker`, run `secret put` again, deploy,
then delete the old key; the Worker reads the secret at each request.

## Daily clean-up (H3)

`[triggers] crons` runs `scheduled` once a day. A group with `deleted == true` and no `deletedAt`
(deleted by an app build before the stamp existed) gets `deletedAt` = now. A group whose
`deletedAt` is over 30 days old loses every subcollection (members, pay, entries, repeats, joins,
any other), every invite naming it, and then its own document, in batch writes of at most 300.
Each run makes at most 40 Firestore calls and carries on the next day where it stopped. The log
holds counts only. To run it once by hand: `npx wrangler dev --test-scheduled`, then open
`/__scheduled` (this touches the real project).

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
- `src/cleanup.ts`: the daily clean-up of deleted groups.
- `src/auth.ts`: ID token check against Google's `securetoken` certificates, service-account
  access token, both cached in the isolate.
- `src/firestore.ts`: `groups/{gid}` read, `devices` query in chunks of 30 capped at 5 per account,
  device delete, and the query, collection-list and batch-write calls the clean-up uses.
- `src/fcm.ts`: one FCM HTTP v1 data message per device, sent to its Firebase installation ID
  (`fid`); an ID FCM rejects marks the device dead. iPhones get a background push and post the
  notice themselves.
- `wrangler.toml`: name, `FIREBASE_PROJECT_ID`, `LEGACY_NOTICES`, both limiters, the daily cron.
  The secret is never in this file.
