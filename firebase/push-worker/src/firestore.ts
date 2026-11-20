import { HttpError } from './http';

export interface Device {
  token: string;
  uid: string;
  platform: 'android' | 'ios';
  language: 'en' | 'my' | 'th';
  /** When the phone last registered, in epoch millis; 0 when the document has none. */
  updatedAt: number;
}

// Firestore allows at most 30 values in one `in` filter.
const IN_CHUNK = 30;

export interface FirestoreValue {
  stringValue?: string;
  booleanValue?: boolean;
  integerValue?: string;
  timestampValue?: string;
  arrayValue?: { values?: FirestoreValue[] };
}

export interface FirestoreDocument {
  name: string;
  fields?: Record<string, FirestoreValue>;
}

export interface Group {
  memberUids: string[];
  /** The stored name, so a phone cannot put another title on the notice. */
  name?: string;
  /** Deleted for everyone by its creator. */
  deleted: boolean;
}

/** The group's members and name, or null when there is no such group. */
export async function readGroup(
  projectId: string,
  accessToken: string,
  groupId: string,
): Promise<Group | null> {
  const response = await fetch(`${documentsUrl(projectId)}/groups/${encodeURIComponent(groupId)}`, {
    headers: authHeaders(accessToken),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new HttpError(502, `firestore read failed: ${response.status}`);
  const document = (await response.json()) as FirestoreDocument;
  const name = document.fields?.name?.stringValue?.trim();
  return {
    memberUids: stringArray(document.fields?.memberUids),
    name: name || undefined,
    deleted: document.fields?.deleted?.booleanValue === true,
  };
}

/**
 * The registered phones of the given accounts, at most [perAccount] read for each (M7): a member
 * can register any number of device documents, and each one read costs quota.
 */
export async function devicesFor(
  projectId: string,
  accessToken: string,
  uids: string[],
  perAccount: number,
): Promise<Device[]> {
  const devices: Device[] = [];
  for (let start = 0; start < uids.length; start += IN_CHUNK) {
    const chunk = uids.slice(start, start + IN_CHUNK);
    const limit = chunk.length * perAccount;
    const found = await queryDevices(projectId, accessToken, chunk, limit);
    devices.push(...found);
    // A full page may hold one account's many documents and none of another's: those ask alone.
    for (const uid of crowdedOut(chunk, found, limit)) {
      devices.push(...(await queryDevices(projectId, accessToken, [uid], perAccount)));
    }
  }
  return devices;
}

/** Accounts a query capped at [limit] may have left out: none unless the page came back full. */
export function crowdedOut(uids: string[], found: Device[], limit: number): string[] {
  if (found.length < limit) return [];
  const seen = new Set(found.map((device) => device.uid));
  return uids.filter((uid) => !seen.has(uid));
}

export async function deleteDevice(projectId: string, accessToken: string, token: string): Promise<void> {
  await fetch(`${documentsUrl(projectId)}/devices/${encodeURIComponent(token)}`, {
    method: 'DELETE',
    headers: authHeaders(accessToken),
  });
}

async function queryDevices(projectId: string, accessToken: string, uids: string[], limit: number): Promise<Device[]> {
  // No orderBy: newest-first by updatedAt would need a composite index with uid.
  const query = {
    structuredQuery: {
      limit,
      from: [{ collectionId: 'devices' }],
      where: {
        fieldFilter: {
          field: { fieldPath: 'uid' },
          op: 'IN',
          value: { arrayValue: { values: uids.map((uid) => ({ stringValue: uid })) } },
        },
      },
    },
  };
  const response = await fetch(`${documentsUrl(projectId)}:runQuery`, {
    method: 'POST',
    headers: { ...authHeaders(accessToken), 'content-type': 'application/json' },
    body: JSON.stringify(query),
  });
  if (!response.ok) throw new HttpError(502, `firestore query failed: ${response.status}`);
  const rows = (await response.json()) as Array<{ document?: FirestoreDocument }>;
  const devices: Device[] = [];
  for (const row of rows) {
    const device = row.document && toDevice(row.document);
    if (device) devices.push(device);
  }
  return devices;
}

function toDevice(document: FirestoreDocument): Device | undefined {
  const fields = document.fields ?? {};
  const uid = fields.uid?.stringValue;
  const platform = fields.platform?.stringValue;
  const language = fields.language?.stringValue;
  if (!uid || (platform !== 'android' && platform !== 'ios')) return undefined;
  return {
    token: document.name.slice(document.name.lastIndexOf('/') + 1),
    uid,
    platform,
    language: language === 'my' || language === 'th' ? language : 'en',
    updatedAt: Number(fields.updatedAt?.integerValue ?? 0) || 0,
  };
}

/** Documents a structured query under [parent] ('' for the root) returns. */
export async function runQuery(
  projectId: string,
  accessToken: string,
  parent: string,
  structuredQuery: Record<string, unknown>,
): Promise<FirestoreDocument[]> {
  const at = parent === '' ? documentsUrl(projectId) : `${documentsUrl(projectId)}/${parent}`;
  const response = await fetch(`${at}:runQuery`, {
    method: 'POST',
    headers: { ...authHeaders(accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ structuredQuery }),
  });
  if (!response.ok) throw new HttpError(502, `firestore query failed: ${response.status}`);
  const rows = (await response.json()) as Array<{ document?: FirestoreDocument }>;
  return rows.flatMap((row) => (row.document ? [row.document] : []));
}

/** The names of the subcollections directly under [path]. */
export async function collectionIds(projectId: string, accessToken: string, path: string): Promise<string[]> {
  const response = await fetch(`${documentsUrl(projectId)}/${path}:listCollectionIds`, {
    method: 'POST',
    headers: { ...authHeaders(accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ pageSize: 100 }),
  });
  if (!response.ok) throw new HttpError(502, `firestore list failed: ${response.status}`);
  return ((await response.json()) as { collectionIds?: string[] }).collectionIds ?? [];
}

/** Up to 500 writes in one call; each one stands alone, so a run that stops halfway loses nothing. */
export async function batchWrite(projectId: string, accessToken: string, writes: unknown[]): Promise<void> {
  if (writes.length === 0) return;
  const response = await fetch(`${documentsUrl(projectId)}:batchWrite`, {
    method: 'POST',
    headers: { ...authHeaders(accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ writes }),
  });
  if (!response.ok) throw new HttpError(502, `firestore write failed: ${response.status}`);
}

/** A document's full resource name, as a batch write's delete takes it. */
export function documentName(projectId: string, path: string): string {
  return `projects/${projectId}/databases/(default)/documents/${path}`;
}

function stringArray(value: FirestoreValue | undefined): string[] {
  return (value?.arrayValue?.values ?? []).flatMap((item) =>
    typeof item.stringValue === 'string' ? [item.stringValue] : [],
  );
}

function documentsUrl(projectId: string): string {
  return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;
}

function authHeaders(accessToken: string): Record<string, string> {
  return { authorization: `Bearer ${accessToken}` };
}
