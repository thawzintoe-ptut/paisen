import { batchWrite, collectionIds, documentName, runQuery, type FirestoreDocument } from './firestore';

/**
 * H3: a group deleted for everyone keeps its documents for 30 days, so a phone that was offline
 * still reads `deleted` and lets the group go. After that every document under it goes for good.
 */
export const PURGE_AFTER_MS = 30 * 86_400_000;

// Names per read, under a batch write's 500.
const PAGE = 300;
// Firestore calls per run, under the free plan's 50 subrequests with the token fetch.
const CALLS_PER_RUN = 40;
const PURGE_PER_RUN = 20;
const STAMP_PER_RUN = 200;

export interface DeletedGroup {
  id: string;
  deleted: boolean;
  /** Epoch millis; absent for a group an old build deleted. */
  deletedAt?: number;
}

export interface Counts {
  stamped: number;
  purged: number;
  documents: number;
  /** A purge the call budget cut short; the next run carries on. */
  unfinished: number;
}

/** 'stamp' starts the 30 days for a group an old build deleted, from the first run that sees it. */
export function stepFor(group: DeletedGroup, now: number): 'purge' | 'stamp' | 'wait' | 'skip' {
  if (!group.deleted) return 'skip';
  if (group.deletedAt === undefined) return 'stamp';
  return now - group.deletedAt >= PURGE_AFTER_MS ? 'purge' : 'wait';
}

export function toDeletedGroup(document: FirestoreDocument): DeletedGroup {
  const fields = document.fields ?? {};
  const stamp = fields.deletedAt;
  const millis = stamp?.timestampValue !== undefined ? Date.parse(stamp.timestampValue) : Number(stamp?.integerValue);
  return {
    id: lastSegment(document.name),
    deleted: fields.deleted?.booleanValue === true,
    deletedAt: Number.isFinite(millis) ? millis : undefined,
  };
}

/** Counts Firestore calls, so one run stays inside the Worker's subrequest limit. */
export class Budget {
  private left: number;

  constructor(calls: number) {
    this.left = calls;
  }

  spend(calls = 1): boolean {
    if (this.left < calls) return false;
    this.left -= calls;
    return true;
  }
}

/** One daily run: stamps first, then purges oldest first. Safe to stop anywhere and run again. */
export async function cleanUp(projectId: string, accessToken: string, now: number): Promise<Counts> {
  const counts: Counts = { stamped: 0, purged: 0, documents: 0, unfinished: 0 };
  const budget = new Budget(CALLS_PER_RUN);

  budget.spend(2);
  const deleted = (
    await runQuery(projectId, accessToken, '', {
      from: [{ collectionId: 'groups' }],
      where: equals('deleted', { booleanValue: true }),
      select: { fields: [{ fieldPath: 'deleted' }, { fieldPath: 'deletedAt' }] },
      limit: STAMP_PER_RUN,
    })
  ).map(toDeletedGroup);
  const unstamped = deleted.filter((group) => stepFor(group, now) === 'stamp');
  const stampedAt = new Date(now).toISOString();
  await batchWrite(
    projectId,
    accessToken,
    unstamped.map((group) => ({
      update: { name: documentName(projectId, `groups/${group.id}`), fields: { deletedAt: { timestampValue: stampedAt } } },
      updateMask: { fieldPaths: ['deletedAt'] },
      currentDocument: { exists: true },
    })),
  );
  counts.stamped = unstamped.length;

  // A range on deletedAt alone needs no composite index and comes oldest first.
  budget.spend();
  const due = (
    await runQuery(projectId, accessToken, '', {
      from: [{ collectionId: 'groups' }],
      where: {
        fieldFilter: {
          field: { fieldPath: 'deletedAt' },
          op: 'LESS_THAN_OR_EQUAL',
          value: { timestampValue: new Date(now - PURGE_AFTER_MS).toISOString() },
        },
      },
      select: { fields: [{ fieldPath: 'deleted' }, { fieldPath: 'deletedAt' }] },
      limit: PURGE_PER_RUN,
    })
  )
    .map(toDeletedGroup)
    .filter((group) => stepFor(group, now) === 'purge');

  for (const group of due) {
    const result = await purge(projectId, accessToken, group.id, budget);
    counts.documents += result.documents;
    if (!result.finished) {
      counts.unfinished += 1;
      break;
    }
    counts.purged += 1;
  }
  return counts;
}

/** Every subcollection, then every invite naming the group, then the group itself, which marks it done. */
async function purge(
  projectId: string,
  accessToken: string,
  groupId: string,
  budget: Budget,
): Promise<{ documents: number; finished: boolean }> {
  const groupPath = `groups/${groupId}`;
  let documents = 0;
  if (!budget.spend()) return { documents, finished: false };
  const queries = (await collectionIds(projectId, accessToken, groupPath)).map((collectionId) => ({
    parent: groupPath,
    query: { from: [{ collectionId }] } as Record<string, unknown>,
  }));
  // Rotated invites too: each one still carries the group's name.
  queries.push({ parent: '', query: { from: [{ collectionId: 'invites' }], where: equals('groupId', { stringValue: groupId }) } });

  for (const { parent, query } of queries) {
    for (;;) {
      if (!budget.spend(2)) return { documents, finished: false };
      const page = await runQuery(projectId, accessToken, parent, {
        ...query,
        select: { fields: [{ fieldPath: '__name__' }] },
        limit: PAGE,
      });
      await batchWrite(projectId, accessToken, page.map((document) => ({ delete: document.name })));
      documents += page.length;
      if (page.length < PAGE) break;
    }
  }
  if (!budget.spend()) return { documents, finished: false };
  await batchWrite(projectId, accessToken, [{ delete: documentName(projectId, groupPath) }]);
  return { documents: documents + 1, finished: true };
}

function equals(fieldPath: string, value: Record<string, unknown>): Record<string, unknown> {
  return { fieldFilter: { field: { fieldPath }, op: 'EQUAL', value } };
}

function lastSegment(name: string): string {
  return name.slice(name.lastIndexOf('/') + 1);
}
