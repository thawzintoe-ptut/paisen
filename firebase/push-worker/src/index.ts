import { accessToken, verifyIdToken } from './auth';
import { endOldSignIns } from './sessions';
import { cleanUp } from './cleanup';
import { sendToDevice } from './fcm';
import { deleteDevice, devicesFor, readGroup, type Device, type Group } from './firestore';
import { HttpError, json } from './http';
import { MAX_BODY_BYTES, RefusalCache, clientIp, declaredLength, limit, readJsonBody } from './limits';
import { parseNotice } from './notice';

export interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_SERVICE_ACCOUNT: string;
  /** Per account, so one looping phone cannot use up the day's Worker and Firestore quota. */
  PUSH_LIMITER?: RateLimit;
  /** Per address, before the token check, so a flood of made-up tokens costs no certificate work. */
  IP_LIMITER?: RateLimit;
  /** "true" only in `.dev.vars`: without it a missing limiter refuses every call. */
  ALLOW_NO_LIMITER?: string;
  /** "accept" while builds up to v0.4.0-rc.1 still send their own sentences; "refuse" after. */
  LEGACY_NOTICES?: string;
}

// Phones one account can be sent to. One phone holds an account at a time; the rest are reinstalls.
const DEVICES_PER_ACCOUNT = 5;
const refusals = new RefusalCache(5 * 60_000, 5_000);

export default {
  async fetch(request, env, ctx): Promise<Response> {
    try {
      return await notify(request, env, ctx);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      console.error(error);
      return json({ error: 'internal' }, 500);
    }
  },

  // H3, daily (`[triggers] crons`): counts only in the log, never a group's id or name.
  async scheduled(controller, env): Promise<void> {
    try {
      const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT);
      const counts = await cleanUp(env.FIREBASE_PROJECT_ID, token, controller.scheduledTime);
      console.log(JSON.stringify({ cleanup: counts }));
    } catch (error) {
      console.error(`cleanup failed: ${error instanceof HttpError ? error.message : 'internal'}`);
    }
    // Apart from the cleanup, so a failed purge never keeps an old phone signed in.
    try {
      const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT);
      const ended = await endOldSignIns(env.FIREBASE_PROJECT_ID, token, controller.scheduledTime);
      console.log(JSON.stringify({ sessions: { ended } }));
    } catch (error) {
      console.error(`session end failed: ${error instanceof HttpError ? error.message : 'internal'}`);
    }
  },
} satisfies ExportedHandler<Env>;

async function notify(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (new URL(request.url).pathname !== '/notify') throw new HttpError(404, 'not found');
  if (request.method !== 'POST') return json({ error: 'method not allowed' }, 405, { allow: 'POST' });

  await limit(env.IP_LIMITER, clientIp(request), env.ALLOW_NO_LIMITER);
  if (declaredLength(request) > MAX_BODY_BYTES) throw new HttpError(413, 'body too large');
  const uid = await verifyIdToken(bearerToken(request), env.FIREBASE_PROJECT_ID);
  // Before any Firestore read, so a flood costs nothing past this line.
  await limit(env.PUSH_LIMITER, uid, env.ALLOW_NO_LIMITER);
  const notice = parseNotice(await readJsonBody(request), env.LEGACY_NOTICES !== 'refuse');
  const now = Date.now();
  const known = refusals.get(notice.groupId, uid, now);
  if (known) throw known;
  const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT);

  const group = await readGroup(env.FIREBASE_PROJECT_ID, token, notice.groupId);
  const refusal = refusalFor(group, uid);
  if (refusal !== undefined || group === null) {
    const error = refusal ?? new HttpError(404, 'group not found');
    refusals.remember(notice.groupId, uid, error, now);
    throw error;
  }

  // The title is the group's stored name; an old build's own title only when there is none.
  const title = group.name ?? (notice.shape === 'legacy' ? notice.title : 'PaiSeN');
  const others = group.memberUids.filter((member) => member !== uid);
  const devices =
    others.length === 0
      ? []
      : newestPerAccount(await devicesFor(env.FIREBASE_PROJECT_ID, token, others, DEVICES_PER_ACCOUNT));
  const outgoing = { notice, title, senderUid: uid };
  const results = await Promise.all(
    devices.map((device) => sendToDevice(env.FIREBASE_PROJECT_ID, token, device, outgoing)),
  );

  const dead = devices.filter((_, index) => results[index] === 'dead');
  ctx.waitUntil(
    Promise.all(dead.map((device) => deleteDevice(env.FIREBASE_PROJECT_ID, token, device.token))),
  );
  return json({ sent: results.filter((result) => result === 'sent').length });
}

/** Why [uid] may not notify [group], or undefined when it may. */
export function refusalFor(group: Group | null, uid: string): HttpError | undefined {
  if (group === null) return new HttpError(404, 'group not found');
  if (!group.memberUids.includes(uid)) return new HttpError(403, 'not a member');
  // The creator's phone sends "deleted for everyone" before it marks the group, so nothing after that is news.
  if (group.deleted) return new HttpError(410, 'group deleted');
  return undefined;
}

/** At most [DEVICES_PER_ACCOUNT] phones per account, the most recently registered first. */
function newestPerAccount(devices: Device[]): Device[] {
  const byAccount = new Map<string, Device[]>();
  for (const device of devices) byAccount.set(device.uid, [...(byAccount.get(device.uid) ?? []), device]);
  return [...byAccount.values()].flatMap((phones) =>
    phones.sort((a, b) => b.updatedAt - a.updatedAt).slice(0, DEVICES_PER_ACCOUNT),
  );
}

function bearerToken(request: Request): string {
  const header = request.headers.get('authorization') ?? '';
  if (!header.startsWith('Bearer ')) throw new HttpError(401, 'missing token');
  return header.slice('Bearer '.length).trim();
}
