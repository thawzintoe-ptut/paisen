import { HttpError } from './http';
import { runQuery, type FirestoreDocument } from './firestore';

/**
 * One phone per account, enforced on the server a week late. A newer sign-in on another phone ends the
 * old one's sign-ins after this long, so the old phone has a week to send changes still waiting.
 */
export const REVOKE_AFTER_SECONDS = 7 * 86_400;

/** Sessions that turned a week old in the last few days; a missed cron run still catches them. */
export const WINDOW_SECONDS = 3 * 86_400;

// Identity Toolkit calls per run, inside the free plan's 50 subrequests beside the cleanup's 40.
export const REVOKE_PER_RUN = 6;

export interface Session {
  uid: string;
  /** The newest sign-in's auth_time, in seconds. */
  signedInAt: number;
}

/** Sessions whose sign-in is between one week and one week plus [WINDOW_SECONDS] old, oldest first. */
export function dueSessionsQuery(nowSeconds: number): Record<string, unknown> {
  const newest = nowSeconds - REVOKE_AFTER_SECONDS;
  return {
    from: [{ collectionId: 'sessions' }],
    where: {
      compositeFilter: {
        op: 'AND',
        filters: [
          { fieldFilter: { field: { fieldPath: 'signedInAt' }, op: 'LESS_THAN_OR_EQUAL', value: { integerValue: String(newest) } } },
          { fieldFilter: { field: { fieldPath: 'signedInAt' }, op: 'GREATER_THAN', value: { integerValue: String(newest - WINDOW_SECONDS) } } },
        ],
      },
    },
    orderBy: [{ field: { fieldPath: 'signedInAt' }, direction: 'ASCENDING' }],
    limit: REVOKE_PER_RUN,
  };
}

export function toSession(document: FirestoreDocument): Session | undefined {
  const value = Number(document.fields?.signedInAt?.integerValue);
  const uid = document.name.slice(document.name.lastIndexOf('/') + 1);
  return Number.isFinite(value) && uid !== '' ? { uid, signedInAt: value } : undefined;
}

/**
 * Ends every sign-in older than the session's: an older phone's refresh token stops working, so its
 * ID token runs out within the hour and the app signs out. The newest phone signed in at
 * [Session.signedInAt] exactly, which is not before validSince, so it stays signed in.
 */
export async function revokeOlderSignIns(projectId: string, accessToken: string, session: Session): Promise<void> {
  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts:update`, {
    method: 'POST',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ localId: session.uid, validSince: String(session.signedInAt) }),
  });
  // A deleted account has nothing left to end.
  if (response.status === 400 || response.status === 404) return;
  if (!response.ok) throw new HttpError(502, `revoke failed: ${response.status}`);
}

/** The daily pass: how many accounts had older sign-ins ended. */
export async function endOldSignIns(projectId: string, accessToken: string, nowMillis: number): Promise<number> {
  const due = (await runQuery(projectId, accessToken, '', dueSessionsQuery(Math.floor(nowMillis / 1000))))
    .map(toSession)
    .filter((session): session is Session => session !== undefined);
  let ended = 0;
  for (const session of due) {
    await revokeOlderSignIns(projectId, accessToken, session);
    ended += 1;
  }
  return ended;
}
