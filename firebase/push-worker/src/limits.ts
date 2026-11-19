import { HttpError } from './http';

export const MAX_BODY_BYTES = 8 * 1024;

export interface Limiters {
  /** Per CF-Connecting-IP, before the token is checked. */
  ip?: RateLimit;
  /** Per account, before any Firestore read. */
  account?: RateLimit;
  /** "true" only in `.dev.vars`, for a local run without the bindings. */
  allowMissing?: string;
}

/** M8: a missing binding refuses everything (503) unless a local run says otherwise. */
export async function limit(limiter: RateLimit | undefined, key: string, allowMissing: string | undefined): Promise<void> {
  if (!limiter) {
    if (allowMissing === 'true') return;
    throw new HttpError(503, 'limiter unavailable');
  }
  if (!(await limiter.limit({ key })).success) throw new HttpError(429, 'too many notices');
}

/** The caller's address as Cloudflare saw it; one shared bucket when the header is missing. */
export function clientIp(request: Request): string {
  return request.headers.get('cf-connecting-ip') ?? 'unknown';
}

/** L: the JSON body, refused past [MAX_BODY_BYTES] whether or not the phone declared its length. */
export async function readJsonBody(request: Request): Promise<unknown> {
  if (declaredLength(request) > MAX_BODY_BYTES) throw tooLarge();
  const reader = request.body?.getReader();
  if (!reader) return undefined;
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BODY_BYTES) {
      await reader.cancel();
      throw tooLarge();
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return undefined;
  }
}

export function declaredLength(request: Request): number {
  const length = Number(request.headers.get('content-length') ?? '0');
  return Number.isFinite(length) ? length : 0;
}

function tooLarge(): HttpError {
  return new HttpError(413, 'body too large');
}

/**
 * M8: a refusal for an account and group, kept briefly in this isolate so a stranger who repeats
 * the call costs no Firestore read. Short-lived, since a removed member may be added back.
 */
export class RefusalCache {
  private readonly entries = new Map<string, { error: HttpError; until: number }>();

  constructor(
    private readonly ttlMs: number,
    private readonly maxEntries: number,
  ) {}

  get(groupId: string, uid: string, now: number): HttpError | undefined {
    const key = cacheKey(groupId, uid);
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (entry.until > now) return entry.error;
    this.entries.delete(key);
    return undefined;
  }

  remember(groupId: string, uid: string, error: HttpError, now: number): void {
    // A Map keeps insertion order, so the first key is the oldest.
    if (this.entries.size >= this.maxEntries) {
      const oldest = this.entries.keys().next().value;
      if (oldest !== undefined) this.entries.delete(oldest);
    }
    this.entries.set(cacheKey(groupId, uid), { error, until: now + this.ttlMs });
  }
}

function cacheKey(groupId: string, uid: string): string {
  return `${uid}\n${groupId}`;
}
