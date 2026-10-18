import { HttpError } from './http';

export interface Device {
  token: string;
  uid: string;
  platform: 'android' | 'ios';
  language: 'en' | 'my';
  /** When the phone last registered, in epoch millis; 0 when the document has none. */
  updatedAt: number;
}

// Firestore allows at most 30 values in one `in` filter.
const IN_CHUNK = 30;

interface FirestoreValue {
  stringValue?: string;
  booleanValue?: boolean;
  integerValue?: string;
  arrayValue?: { values?: FirestoreValue[] };
}

interface FirestoreDocument {
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

/** Every registered phone of the given accounts. */
export async function devicesFor(
  projectId: string,
  accessToken: string,
  uids: string[],
): Promise<Device[]> {
  const devices: Device[] = [];
  for (let start = 0; start < uids.length; start += IN_CHUNK) {
    devices.push(...(await queryDevices(projectId, accessToken, uids.slice(start, start + IN_CHUNK))));
  }
  return devices;
}

export async function deleteDevice(projectId: string, accessToken: string, token: string): Promise<void> {
  await fetch(`${documentsUrl(projectId)}/devices/${encodeURIComponent(token)}`, {
    method: 'DELETE',
    headers: authHeaders(accessToken),
  });
}

async function queryDevices(projectId: string, accessToken: string, uids: string[]): Promise<Device[]> {
  const query = {
    structuredQuery: {
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
  if (!uid || (platform !== 'android' && platform !== 'ios')) return undefined;
  return {
    token: document.name.slice(document.name.lastIndexOf('/') + 1),
    uid,
    platform,
    language: fields.language?.stringValue === 'my' ? 'my' : 'en',
    updatedAt: Number(fields.updatedAt?.integerValue ?? 0) || 0,
  };
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
