// The cases in ../rules-test-plan.md, run on the Firestore emulator by `npm test`.
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, writeBatch, arrayRemove, arrayUnion, serverTimestamp,
  collection, query, where,
} from 'firebase/firestore';
import { readFileSync } from 'node:fs';

const AUTH_TIME = 1_760_000_000;
const env = await initializeTestEnvironment({
  projectId: 'demo-paisen-rules',
  firestore: { rules: readFileSync(process.argv[2], 'utf8'), host: '127.0.0.1', port: 8085 },
});
const as = (uid) => env.authenticatedContext(uid, { auth_time: AUTH_TIME }).firestore();

async function seed({ group = {}, members = {} } = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'groups/g1'), {
      name: 'Trip', type: 'Trip', icon: 'group', simplifyDebts: true, createdAt: 1,
      creatorUid: 'alice', memberUids: ['alice', 'bob'], inviteCode: 'CODE1', inviteActive: true, ...group,
    });
    await setDoc(doc(db, 'invites/CODE1'), { groupId: 'g1', groupName: 'Trip', active: true, creatorUid: 'alice' });
    await setDoc(doc(db, 'groups/g1/members/mAlice'), { name: 'Alice', claimedByUid: 'alice', archived: false, createdAt: 1, ...members.mAlice });
    await setDoc(doc(db, 'groups/g1/members/mBob'), { name: 'Bob', claimedByUid: 'bob', archived: false, createdAt: 1, promptPay: '0812345678', ...members.mBob });
    await setDoc(doc(db, 'groups/g1/members/mCara'), { name: 'Cara', claimedByUid: null, archived: false, createdAt: 1, ...members.mCara });
    await setDoc(doc(db, 'groups/g1/joins/bob'), { code: 'CODE1', at: 1 });
    await setDoc(doc(db, 'groups/g1/joins/mallory'), { code: 'CODE1', at: 1 });
    await setDoc(doc(db, 'groups/g1/entries/e0'), { kind: 'version', authorUid: 'alice', createdAt: 1 });
    await setDoc(doc(db, 'devices/devA'), { uid: 'alice', platform: 'android', language: 'en', updatedAt: 1 });
  });
}

const results = [];
async function check(name, expect, setup, act) {
  await seed(setup);
  try {
    await (expect === 'allow' ? assertSucceeds(act()) : assertFails(act()));
    results.push(['PASS', expect, name]);
  } catch (error) {
    results.push(['FAIL', expect, name, String(error.message ?? error).split('\n')[0]]);
  }
}

const entry = (path, uid, fields) => setDoc(doc(as(uid), path), { authorUid: uid, createdAt: Date.now(), syncedAt: serverTimestamp(), ...fields });
const version = (over = {}) => ({
  kind: 'version', expenseId: 'x1', version: 1, description: 'Taxi', occurredAt: 1, categoryId: null, subCategoryId: null,
  note: null, currency: 'THB', amountMinor: 10000, rateUnscaled: null, rateScale: null, amountSatang: 10000,
  payerMemberId: 'mBob', splitMode: 'Equally', authorMemberId: 'mBob', removed: false, cause: null,
  shares: [{ memberId: 'mBob', weight: 1, amountMinor: 10000, amountSatang: 10000 }], ...over,
});
const payment = (over = {}) => ({
  kind: 'payment', fromMemberId: 'mBob', toMemberId: 'mAlice', amountSatang: 5000, method: 'Cash', occurredAt: 1,
  authorMemberId: 'mBob', ...over,
});
const shares = (n) => Array.from({ length: n }, (_, i) => ({ memberId: `m${i}`, weight: 1, amountMinor: 1, amountSatang: 1 }));

// What SharedSync.archive sends: a new invite first while one is live (newInvite), then one commit
// that lets the name go and takes the account out.
async function removal(uid, { rotate = true, uids = ['bob'], member = 'mBob' } = {}) {
  const db = as(uid);
  if (rotate) {
    await updateDoc(doc(db, 'invites/CODE1'), { active: false });
    await setDoc(doc(db, 'invites/CODE2'), { groupId: 'g1', groupName: 'Trip', active: true, creatorUid: uid });
    await updateDoc(doc(db, 'groups/g1'), { inviteCode: 'CODE2', inviteActive: true });
  }
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/g1/members/${member}`), { archived: true, claimedByUid: null, promptPay: null });
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayRemove(...uids) });
  return batch.commit();
}
async function removedThen(act) {
  // A refused setup must not pass as the "deny" under test.
  try { await removal('alice'); } catch { throw new Error('setup: the removal itself was refused'); }
  return act();
}

// 1. memberUids
await check('1.1 joiner sets memberUids to only themselves', 'deny', {}, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: ['mallory'] }));
await check('1.2 joiner adds themselves, keeping everyone', 'allow', {}, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory') }));
await check('1.3 joiner adds themselves and a friend', 'deny', {}, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory', 'eve') }));
await check('1.4 joiner adds themselves and drops the creator', 'deny', {}, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: ['bob', 'mallory'] }));
await check('1.5 member leaves', 'allow', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: arrayRemove('bob') }));
await check('1.6 member leaves and adds someone', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: ['alice', 'eve'] }));
await check('1.7 member drops another member', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: ['bob'] }));
await check('1.8 app join commit (claim a free name + add self)', 'allow', {}, () => {
  const db = as('mallory');
  const batch = writeBatch(db);
  batch.update(doc(db, 'groups/g1/members/mCara'), { claimedByUid: 'mallory' });
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayUnion('mallory') });
  return batch.commit();
});
await check('1.9 app leave commit (let the name go + leave)', 'allow', {}, () => {
  const db = as('bob');
  const batch = writeBatch(db);
  batch.update(doc(db, 'groups/g1/members/mBob'), { claimedByUid: null, promptPay: null });
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayRemove('bob') });
  return batch.commit();
});

// 2. Remove member
await check('2.1 creator removes a member with a new code', 'allow', {}, () => removal('alice'));
await check('2.2 creator removes a member and keeps the live code', 'deny', {}, () => removal('alice', { rotate: false }));
await check('2.3 creator removes a member while the invite is off', 'allow', { group: { inviteActive: false } }, () => removal('alice', { rotate: false }));
await check('2.4 creator archives a claimed name only (old app)', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1/members/mBob'), { archived: true }));
await check('2.5 creator archives an unclaimed name', 'allow', {}, () => updateDoc(doc(as('alice'), 'groups/g1/members/mCara'), { archived: true }));
await check('2.6 creator puts a removed name back', 'allow', { members: { mCara: { archived: true } } }, () => updateDoc(doc(as('alice'), 'groups/g1/members/mCara'), { archived: false }));
await check('2.7 creator removes their own account', 'deny', {}, () => removal('alice', { uids: ['alice'], member: 'mAlice' }));
await check('2.8 creator removes two accounts at once', 'deny', { group: { memberUids: ['alice', 'bob', 'carol'] } }, () => removal('alice', { uids: ['bob', 'carol'] }));
await check('2.9 a member who is not the creator removes someone', 'deny', { group: { memberUids: ['alice', 'bob', 'carol'] } }, () => removal('bob', { rotate: false, uids: ['carol'], member: 'mCara' }));
await check('2.10 removed member can no longer read the group', 'deny', {}, () => removedThen(() => getDoc(doc(as('bob'), 'groups/g1'))));
await check('2.11 removed member can no longer read entries', 'deny', {}, () => removedThen(() => getDocs(collection(as('bob'), 'groups/g1/entries'))));
await check('2.12 removed member re-adds themselves with the old code', 'deny', {}, () => removedThen(() => updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: arrayUnion('bob') })));
await check('2.13 removed member\'s restore query no longer finds the group', 'allow', {}, () => removedThen(async () => {
  const found = await getDocs(query(collection(as('bob'), 'groups'), where('memberUids', 'array-contains', 'bob')));
  if (found.size !== 0) throw new Error(`found ${found.size}`);
}));
await check('2.14 cleanup of a name archived before this fix', 'allow', { members: { mBob: { archived: true } } }, () => removal('alice'));

// The exact REST calls SharedSync sends to remove Bob, with an unsigned emulator token.
const DB = 'projects/demo-paisen-rules/databases/(default)';
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const token = (uid) => `${b64({ alg: 'none', typ: 'JWT' })}.${b64({
  iss: 'https://securetoken.google.com/demo-paisen-rules', aud: 'demo-paisen-rules', sub: uid, user_id: uid,
  auth_time: AUTH_TIME, iat: AUTH_TIME, exp: AUTH_TIME + 3600, firebase: { sign_in_provider: 'custom', identities: {} },
})}.`;
const rest = async (method, path, body) => {
  const response = await fetch(`http://127.0.0.1:8085/v1/${DB}/documents${path}`, {
    method, headers: { Authorization: `Bearer ${token('alice')}`, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status} ${(await response.text()).slice(0, 200)}`);
};
const mask = (fields) => Object.keys(fields).map((f) => `updateMask.fieldPaths=${f}`).join('&');
const restTakeOut = async () => {
  // newInvite: the old code off, a new one made, the group pointed at it.
  const off = { active: { booleanValue: false } };
  await rest('PATCH', `/invites/CODE1?${mask(off)}&currentDocument.exists=true`, { fields: off });
  await rest('POST', '/invites?documentId=CODE2', {
    fields: { groupId: { stringValue: 'g1' }, groupName: { stringValue: 'Trip' }, active: { booleanValue: true }, creatorUid: { stringValue: 'alice' } },
  });
  const invite = { inviteCode: { stringValue: 'CODE2' }, inviteActive: { booleanValue: true } };
  await rest('PATCH', `/groups/g1?${mask(invite)}&currentDocument.exists=true`, { fields: invite });
  // uploadMember: the name as it is, then archive's one commit.
  const name = { name: { stringValue: 'Bob' } };
  await rest('PATCH', `/groups/g1/members/mBob?${mask(name)}&currentDocument.exists=true`, { fields: name });
  const gone = { archived: { booleanValue: true }, claimedByUid: { nullValue: null }, promptPay: { nullValue: null } };
  await rest('POST', ':commit', {
    writes: [
      { update: { name: `${DB}/documents/groups/g1/members/mBob`, fields: gone }, updateMask: { fieldPaths: Object.keys(gone) }, currentDocument: { exists: true } },
      { transform: { document: `${DB}/documents/groups/g1`, fieldTransforms: [{ fieldPath: 'memberUids', removeAllFromArray: { values: [{ stringValue: 'bob' }] } }] } },
    ],
  });
  let after;
  await env.withSecurityRulesDisabled(async (ctx) => { after = (await getDoc(doc(ctx.firestore(), 'groups/g1'))).data(); });
  if (JSON.stringify(after.memberUids) !== '["alice"]' || after.inviteCode !== 'CODE2') throw new Error(`group after: ${JSON.stringify(after)}`);
};
await check('2.15 the app\'s removal calls over REST', 'allow', {}, restTakeOut);

// 3. Author
await check('3.1 version naming your own name', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version()));
await check('3.2 version naming another member', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ authorMemberId: 'mAlice' })));
await check('3.3 repeat with no author', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version({ authorMemberId: null, cause: 'repeat' })));
await check('3.4 version with no author, not a repeat', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ authorMemberId: null })));
await check('3.5 payment naming your own name', 'allow', {}, () => entry('groups/g1/entries/p1', 'bob', payment()));
await check('3.6 payment naming an unclaimed name', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ authorMemberId: 'mCara' })));
await check('3.7 payment naming a name that does not exist', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ authorMemberId: 'mNobody' })));
await check('3.8 payment removal naming your own name', 'allow', {}, () => entry('groups/g1/entries/r1', 'bob', { kind: 'paymentRemoval', paymentId: 'p1', removed: true, authorMemberId: 'mBob' }));

// 4. Values
await check('4.1 unknown splitMode', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ splitMode: 'Bogus' })));
await check('4.2 rateScale 10', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ rateUnscaled: 138, rateScale: 10 })));
await check('4.3 rateScale 9 and a rate above zero', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version({ rateUnscaled: 138, rateScale: 9 })));
await check('4.4 rateUnscaled 0', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ rateUnscaled: 0, rateScale: 2 })));
await check('4.5 amountSatang not an int', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ amountSatang: 1.5 })));
await check('4.6 51 shares', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ shares: shares(51) })));
await check('4.7 50 shares', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version({ shares: shares(50) })));
await check('4.8 shares not a list', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ shares: 'all' })));
await check('4.9 unknown payment method', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ method: 'Crypto' })));
await check('4.10 unknown group type', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { type: 'Castle' }));
await check('4.11 group name of 101 characters', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { name: 'x'.repeat(101) }));
await check('4.12 group name of 100 characters', 'allow', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { name: 'x'.repeat(100) }));
await check('4.13 member name of 61 characters', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), { name: 'x'.repeat(61), claimedByUid: null, archived: false, createdAt: 1 }));
await check('4.14 member name of 60 characters', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), { name: 'x'.repeat(60), claimedByUid: null, archived: false, createdAt: 1 }));
await check('4.15 rename to 61 characters', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1/members/mCara'), { name: 'x'.repeat(61) }));
await check('4.16 new group with an unknown type', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g2'), { name: 'Home', type: 'Castle', icon: 'group', simplifyDebts: true, createdAt: 1, creatorUid: 'carol', memberUids: ['carol'], inviteCode: null, inviteActive: false }));
await check('4.17 new group as the app shares it', 'allow', {}, () => setDoc(doc(as('carol'), 'groups/g2'), { name: 'Home', type: 'Flat', icon: 'group', simplifyDebts: true, createdAt: 1, creatorUid: 'carol', memberUids: ['carol'], inviteCode: null, inviteActive: false }));

// 5. Devices, sessions, deleted groups
await check('5.1 someone else rewrites a phone\'s device document', 'deny', {}, () => setDoc(doc(as('bob'), 'devices/devA'), { uid: 'bob', platform: 'ios', language: 'en', updatedAt: 2 }));
await check('5.2 the owner rewrites their device document', 'allow', {}, () => setDoc(doc(as('alice'), 'devices/devA'), { uid: 'alice', platform: 'android', language: 'my', updatedAt: 2 }));
await check('5.3 a new phone registers', 'allow', {}, () => setDoc(doc(as('bob'), 'devices/devB'), { uid: 'bob', platform: 'ios', language: 'en', updatedAt: 2 }));
await check('5.4 session stores this sign-in\'s auth_time', 'allow', {}, () => setDoc(doc(as('alice'), 'sessions/alice'), { signedInAt: AUTH_TIME }));
await check('5.5 session stores a time still to come', 'deny', {}, () => setDoc(doc(as('alice'), 'sessions/alice'), { signedInAt: AUTH_TIME + 86_400 }));
await check('5.6 deleted group: member adds an expense', 'deny', { group: { deleted: true } }, () => entry('groups/g1/entries/e1', 'bob', version()));
await check('5.7 deleted group: member renames it', 'deny', { group: { deleted: true } }, () => updateDoc(doc(as('bob'), 'groups/g1'), { name: 'New' }));
await check('5.8 deleted group: member adds a name', 'deny', { group: { deleted: true } }, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), { name: 'Dan', claimedByUid: null, archived: false, createdAt: 1 }));
await check('5.8b deleted group: joiner with the still-live code adds themselves', 'deny', { group: { deleted: true } }, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory') }));
await check('5.9 deleted group: member leaves', 'allow', { group: { deleted: true } }, () => updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: arrayRemove('bob') }));
await check('5.10 deleted group: member lets their name go', 'allow', { group: { deleted: true } }, () => updateDoc(doc(as('bob'), 'groups/g1/members/mBob'), { claimedByUid: null, promptPay: null }));
await check('5.12 share: creator adds their own claimed name', 'allow', {}, () => setDoc(doc(as('alice'), 'groups/g1/members/mNew'), { name: 'Alice', claimedByUid: 'alice', archived: false, createdAt: 1 }));
await check('5.13 member sets up a repeat', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), { authorUid: 'bob', createdAt: 1, syncedAt: serverTimestamp(), kind: 'Expense', active: true }));
await check('5.14 deleted group: member sets up a repeat', 'deny', { group: { deleted: true } }, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), { authorUid: 'bob', createdAt: 1, syncedAt: serverTimestamp(), kind: 'Expense', active: true }));
await check('5.11 app delete-for-everyone', 'allow', {}, () => {
  const db = as('alice');
  const batch = writeBatch(db);
  batch.update(doc(db, 'invites/CODE1'), { active: false });
  batch.update(doc(db, 'groups/g1'), { deleted: true, inviteActive: false });
  return batch.commit();
});

await env.cleanup();
for (const [status, expect, name, why] of results) console.log(`${status}  ${expect.padEnd(5)}  ${name}${why ? `  -> ${why}` : ''}`);
const failed = results.filter(([status]) => status === 'FAIL').length;
console.log(`\n${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
