import { accessToken, verifyIdToken } from './auth';
import { sendToDevice, type Notice } from './fcm';
import { deleteDevice, devicesFor, readGroup, type Device } from './firestore';
import { HttpError, json } from './http';

export interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_SERVICE_ACCOUNT: string;
  /** Per account, so one looping phone cannot use up the day's Worker and Firestore quota. Absent in `wrangler dev`. */
  PUSH_LIMITER?: RateLimit;
}

const MAX_ID_LENGTH = 1500;
const MAX_TITLE_LENGTH = 200;
const MAX_BODY_LENGTH = 1000;
// What reaches a lock screen: the app's own sentences are far shorter.
const SENT_BODY_LENGTH = 300;
// Phones one account can be sent to. One phone holds an account at a time; the rest are reinstalls.
const DEVICES_PER_ACCOUNT = 5;

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
} satisfies ExportedHandler<Env>;

async function notify(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (new URL(request.url).pathname !== '/notify') throw new HttpError(404, 'not found');
  if (request.method !== 'POST') return json({ error: 'method not allowed' }, 405, { allow: 'POST' });

  const uid = await verifyIdToken(bearerToken(request), env.FIREBASE_PROJECT_ID);
  // Before any Firestore read, so a flood costs nothing past this line.
  if (env.PUSH_LIMITER && !(await env.PUSH_LIMITER.limit({ key: uid })).success) {
    throw new HttpError(429, 'too many notices');
  }
  const notice = parseNotice(await request.json().catch(() => undefined));
  const token = await accessToken(env.FIREBASE_SERVICE_ACCOUNT);

  const group = await readGroup(env.FIREBASE_PROJECT_ID, token, notice.groupId);
  if (group === null) throw new HttpError(404, 'group not found');
  if (!group.memberUids.includes(uid)) throw new HttpError(403, 'not a member');
  // The creator's phone sends "deleted for everyone" before it marks the group, so nothing after that is news.
  if (group.deleted) throw new HttpError(410, 'group deleted');

  // The title is the group's stored name; only the sentence comes from the member's phone.
  const titled = { ...notice, title: group.name ?? notice.title };
  const others = group.memberUids.filter((member) => member !== uid);
  const devices = others.length === 0 ? [] : newestPerAccount(await devicesFor(env.FIREBASE_PROJECT_ID, token, others));
  const results = await Promise.all(
    devices.map((device) => sendToDevice(env.FIREBASE_PROJECT_ID, token, device, titled)),
  );

  const dead = devices.filter((_, index) => results[index] === 'dead');
  ctx.waitUntil(
    Promise.all(dead.map((device) => deleteDevice(env.FIREBASE_PROJECT_ID, token, device.token))),
  );
  return json({ sent: results.filter((result) => result === 'sent').length });
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

function parseNotice(body: unknown): Notice {
  const fields = asRecord(body);
  const sentences = asRecord(fields?.body);
  const groupId = text(fields?.groupId, MAX_ID_LENGTH);
  const title = text(fields?.title, MAX_TITLE_LENGTH);
  const en = text(sentences?.en, MAX_BODY_LENGTH);
  const my = text(sentences?.my, MAX_BODY_LENGTH);
  if (!groupId || groupId.includes('/') || !title || !en || !my) throw new HttpError(400, 'bad body');
  return { groupId, title, body: { en: clip(en), my: clip(my) } };
}

/** A long description still sends, cut to what a banner shows. */
function clip(sentence: string): string {
  const characters = Array.from(sentence);
  return characters.length <= SENT_BODY_LENGTH ? sentence : `${characters.slice(0, SENT_BODY_LENGTH - 1).join('')}…`;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown, maxLength: number): string | undefined {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength ? value : undefined;
}
