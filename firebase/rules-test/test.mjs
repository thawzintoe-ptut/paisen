// The cases in ../rules-test-plan.md, run on the Firestore emulator by `npm test`.
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, arrayRemove, arrayUnion, serverTimestamp,
  collection, query, where, Timestamp,
} from 'firebase/firestore';
import { readFileSync } from 'node:fs';

const AUTH_TIME = 1_760_000_000;
const env = await initializeTestEnvironment({
  projectId: 'demo-paisen-rules',
  firestore: { rules: readFileSync(process.argv[2], 'utf8'), host: '127.0.0.1', port: 8085 },
});
const as = (uid) => env.authenticatedContext(uid, { auth_time: AUTH_TIME }).firestore();

async function seed({ group = {}, members = {}, invite = {}, pay = { mBob: { promptPay: '0812345678', claimedByUid: 'bob' } } } = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'groups/g1'), {
      name: 'Trip', type: 'Trip', icon: 'group', simplifyDebts: true, createdAt: 1,
      creatorUid: 'alice', memberUids: ['alice', 'bob'], inviteCode: 'CODE1', inviteActive: true, ...group,
    });
    await setDoc(doc(db, 'invites/CODE1'), { groupId: 'g1', groupName: 'Trip', active: true, creatorUid: 'alice', ...invite });
    await setDoc(doc(db, 'groups/g1/members/mAlice'), { name: 'Alice', claimedByUid: 'alice', archived: false, createdAt: 1, ...members.mAlice });
    await setDoc(doc(db, 'groups/g1/members/mBob'), { name: 'Bob', claimedByUid: 'bob', archived: false, createdAt: 1, promptPay: '0812345678', ...members.mBob });
    await setDoc(doc(db, 'groups/g1/members/mCara'), { name: 'Cara', claimedByUid: null, archived: false, createdAt: 1, ...members.mCara });
    await setDoc(doc(db, 'groups/g1/joins/bob'), { code: 'CODE1', at: 1 });
    await setDoc(doc(db, 'groups/g1/joins/mallory'), { code: 'CODE1', at: 1 });
    await setDoc(doc(db, 'groups/g1/entries/e0'), { kind: 'version', authorUid: 'alice', createdAt: 1 });
    await setDoc(doc(db, 'devices/devA'), { uid: 'alice', platform: 'android', language: 'en', updatedAt: 1 });
    for (const [mid, fields] of Object.entries(pay)) await setDoc(doc(db, `groups/g1/pay/${mid}`), fields);
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
// What RepeatRule.fields sends, with the server's stamp.
const repeat = (over = {}) => ({
  authorUid: 'bob', createdAt: 1, syncedAt: serverTimestamp(), kind: 'Rent', frequency: 'Monthly', start: '2026-11-01',
  endKind: 'Never', endDate: null, endTimes: null, active: true, description: 'Rent', categoryId: null, currency: 'THB',
  amountMinor: 1200000, rateUnscaled: null, rateScale: null, payerMemberId: 'mBob', splitMode: 'Equally',
  weights: 'mAlice=1;mBob=1', ...over,
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
// #48: a group's own money, and its amounts as amountGroup instead of amountSatang.
const kyatGroup = (currency) => ({ name: 'Home', type: 'Flat', icon: 'group', simplifyDebts: true, createdAt: 1, creatorUid: 'carol', memberUids: ['carol'], inviteCode: null, inviteActive: false, currency });
await check('4.18 new group in kyat', 'allow', {}, () => setDoc(doc(as('carol'), 'groups/g3'), kyatGroup('MMK')));
await check('4.19 new group with a currency that is not a code', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g4'), kyatGroup('KYAT')));
await check('4.20 a member changes the group money (no update may touch it)', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { currency: 'MMK' }));
await check('4.20b a member renames a group made before #48, which has no currency field', 'allow', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { name: 'Old trip' }));
await check('4.20c new group with a number for its money', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g4'), kyatGroup(840)));
await check('4.20d new group with a lower-case code', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g4'), kyatGroup('thb')));
// The amount key is the group's: amountGroup in a kyat group, amountSatang in a baht one, never both.
const kyat = { group: { currency: 'MMK' } };
const groupVersion = () => {
  const { amountSatang, shares: _s, ...rest } = version();
  return { ...rest, currency: 'MMK', amountMinor: 29500, amountGroup: 29500, shares: [{ memberId: 'mBob', weight: 1, amountMinor: 29500, amountGroup: 29500 }] };
};
const groupPayment = () => {
  const { amountSatang, ...rest } = payment();
  return { ...rest, amountGroup: 29500 };
};
await check('4.21 kyat group: expense with amountGroup and no amountSatang', 'allow', kyat, () => entry('groups/g1/entries/e2', 'bob', groupVersion()));
await check('4.21b kyat group: expense with amountSatang, as a build from before #48 writes it', 'deny', kyat, () => entry('groups/g1/entries/e2', 'bob', version()));
await check('4.21c kyat group: expense with both keys', 'deny', kyat, () => entry('groups/g1/entries/e2', 'bob', { ...groupVersion(), amountSatang: 29500 }));
await check('4.21d baht group: expense with amountGroup', 'deny', {}, () => entry('groups/g1/entries/e2', 'bob', groupVersion()));
await check('4.22 expense with neither amount', 'deny', {}, () => {
  const { amountSatang, ...rest } = version();
  return entry('groups/g1/entries/e3', 'bob', rest);
});
await check('4.23 kyat group: payment with amountGroup', 'allow', kyat, () => entry('groups/g1/entries/p2', 'bob', groupPayment()));
await check('4.23b kyat group: payment with amountSatang', 'deny', kyat, () => entry('groups/g1/entries/p2', 'bob', payment()));
await check('4.23c baht group: payment with amountGroup', 'deny', {}, () => entry('groups/g1/entries/p2', 'bob', groupPayment()));
await check('4.23d baht group: payment with amountSatang still goes through', 'allow', {}, () => entry('groups/g1/entries/p2', 'bob', payment()));

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
await check('5.13 member sets up a repeat', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat()));
await check('5.14 deleted group: member sets up a repeat', 'deny', { group: { deleted: true } }, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat()));
await check('5.15 a phone set to Thai registers', 'allow', {}, () => setDoc(doc(as('bob'), 'devices/devB'), { uid: 'bob', platform: 'android', language: 'th', updatedAt: 2 }));
await check('5.16 a phone registers in a language no notice carries', 'deny', {}, () => setDoc(doc(as('bob'), 'devices/devB'), { uid: 'bob', platform: 'android', language: 'xx', updatedAt: 2 }));
await check('5.11 app delete-for-everyone', 'allow', {}, () => {
  const db = as('alice');
  const batch = writeBatch(db);
  batch.update(doc(db, 'invites/CODE1'), { active: false });
  batch.update(doc(db, 'groups/g1'), { deleted: true, inviteActive: false });
  return batch.commit();
});

// 6. H1: one name per account
const three = { group: { memberUids: ['alice', 'bob', 'carol'] } };
const joinCommit = (uid, claim) => {
  const db = as(uid);
  const batch = writeBatch(db);
  claim(batch, db);
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayUnion(uid) });
  return batch.commit();
};
const claimName = (uid, mid) => (batch, db) => batch.update(doc(db, `groups/g1/members/${mid}`), { claimedByUid: uid });
const addOwnName = (uid) => (batch, db) => batch.set(doc(db, 'groups/g1/members/mNew'), { name: 'Mal', claimedByUid: uid, archived: false, createdAt: 1 });
await check('6.1 a member who holds a name claims a second one', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1/members/mCara'), { claimedByUid: 'bob' }));
await check('6.2 the same, sent as a join commit', 'deny', {}, () => joinCommit('bob', claimName('bob', 'mCara')));
await check('6.3 a member who holds a name adds a second one already claimed', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), { name: 'Bob 2', claimedByUid: 'bob', archived: false, createdAt: 1 }));
await check('6.4 the same, sent as a join commit', 'deny', {}, () => joinCommit('bob', addOwnName('bob')));
await check('6.5 joiner claims a name without coming into the group', 'deny', {}, () => updateDoc(doc(as('mallory'), 'groups/g1/members/mCara'), { claimedByUid: 'mallory' }));
await check('6.6 app join commit, "I\'m not on the list"', 'allow', {}, () => joinCommit('mallory', addOwnName('mallory')));
await check('6.7 joiner adds their own name without coming into the group', 'deny', {}, () => setDoc(doc(as('mallory'), 'groups/g1/members/mNew'), { name: 'Mal', claimedByUid: 'mallory', archived: false, createdAt: 1 }));
await check('6.8 already in: the link again claims the same name and changes nothing', 'allow', {}, () => joinCommit('bob', claimName('bob', 'mBob')));
await check('6.9 leave, then join again under another name', 'allow', {}, async () => {
  const db = as('bob');
  const leave = writeBatch(db);
  leave.update(doc(db, 'groups/g1/members/mBob'), { claimedByUid: null, promptPay: null });
  leave.update(doc(db, 'groups/g1'), { memberUids: arrayRemove('bob') });
  await leave.commit();
  return joinCommit('bob', claimName('bob', 'mCara'));
});
await check('6.10 a member lets their name go and claims another without leaving', 'deny', {}, async () => {
  await updateDoc(doc(as('bob'), 'groups/g1/members/mBob'), { claimedByUid: null, promptPay: null });
  return updateDoc(doc(as('bob'), 'groups/g1/members/mCara'), { claimedByUid: 'bob' });
});
await check('6.11 joiner claims a name someone holds', 'deny', {}, () => joinCommit('mallory', claimName('mallory', 'mBob')));

// 7. M2: renaming a claimed name
await check('7.1 another member renames a claimed name', 'deny', three, () => updateDoc(doc(as('carol'), 'groups/g1/members/mBob'), { name: 'Alice' }));
await check('7.2 its holder renames it', 'allow', three, () => updateDoc(doc(as('bob'), 'groups/g1/members/mBob'), { name: 'Bobby' }));
await check('7.3 the creator renames it', 'allow', three, () => updateDoc(doc(as('alice'), 'groups/g1/members/mBob'), { name: 'Bobby' }));
await check('7.4 any member renames a name nobody claimed', 'allow', three, () => updateDoc(doc(as('carol'), 'groups/g1/members/mCara'), { name: 'Caroline' }));
await check('7.5 another member sends a claimed name as it is (the app\'s upload)', 'allow', three, () => updateDoc(doc(as('carol'), 'groups/g1/members/mBob'), { name: 'Bob' }));
await check('7.6 someone outside the group renames a name', 'deny', {}, () => updateDoc(doc(as('mallory'), 'groups/g1/members/mCara'), { name: 'Mal' }));

// 8. M3: amounts are ints up to 10^13 minor units
const CAP = 10_000_000_000_000;
// Each share is not checked: 50 of them cost more than the 1,000 expressions a request may evaluate.
const sized = (amount, share = amount) => version({ amountMinor: amount, amountSatang: amount, shares: [{ memberId: 'mBob', weight: 1, amountMinor: share, amountSatang: share }] });
await check('8.1 expense of exactly 10^13', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', sized(CAP)));
await check('8.2 expense one past 10^13', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', sized(CAP + 1, CAP)));
await check('8.3 amountMinor past the cap, group amount within it', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', { ...sized(CAP), amountMinor: CAP + 1 }));
await check('8.9 expense below zero', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', sized(-100, 0)));
await check('8.10 payment of exactly 10^13', 'allow', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ amountSatang: CAP })));
await check('8.11 payment one past 10^13', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ amountSatang: CAP + 1 })));
await check('8.12 payment that is not an int', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ amountSatang: 50.5 })));
await check('8.13 kyat group: amountGroup past the cap', 'deny', kyat, () => entry('groups/g1/entries/e2', 'bob', { ...groupVersion(), amountGroup: CAP + 1 }));
await check('8.14 repeat of exactly 10^13', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ amountMinor: CAP })));
await check('8.15 repeat one past 10^13', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ amountMinor: CAP + 1 })));

// 9. H2: named fields and capped sizes
const newGroup = (over = {}) => ({ name: 'Home', type: 'Flat', icon: 'group', simplifyDebts: true, createdAt: 1, creatorUid: 'carol', memberUids: ['carol'], inviteCode: null, inviteActive: false, ...over });
const many = (n) => ['alice', 'bob', ...Array.from({ length: n - 2 }, (_, i) => `u${i}`)];
const name = (over = {}) => ({ name: 'Dan', claimedByUid: null, archived: false, createdAt: 1, ...over });
await check('9.1 new group with a field the app never sends', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g2'), newGroup({ blob: 'x' })));
await check('9.2 new group with an icon of 65 characters', 'deny', {}, () => setDoc(doc(as('carol'), 'groups/g2'), newGroup({ icon: 'x'.repeat(65) })));
await check('9.3 new group with an icon of 64 characters', 'allow', {}, () => setDoc(doc(as('carol'), 'groups/g2'), newGroup({ icon: 'x'.repeat(64) })));
await check('9.4 member sets an icon of 65 characters', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { icon: 'x'.repeat(65) }));
await check('9.5 member sets the icon and the debts switch', 'allow', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { icon: 'home', simplifyDebts: false }));
await check('9.6 member stores text in the debts switch', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { simplifyDebts: 'x'.repeat(1000) }));
await check('9.7 member adds a field to the group', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { blob: 'x' }));
await check('9.8 creator stores text in the deleted mark', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1'), { deleted: 'x'.repeat(1000) }));
await check('9.9 creator sets an invite code of 65 characters', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1'), { inviteCode: 'X'.repeat(65) }));
await check('9.10 the 101st account joins', 'deny', { group: { memberUids: many(100) } }, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory') }));
await check('9.11 the 100th account joins', 'allow', { group: { memberUids: many(99) } }, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory') }));
await check('9.12 new name with a field the app never sends', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), name({ blob: 'x' })));
await check('9.13 new name with text for archived', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/members/mNew'), name({ archived: 'no' })));
await check('9.14 creator archives a name with text, not a switch', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1/members/mCara'), { archived: 'x'.repeat(1000) }));
await check('9.15 expense with a field the app never sends', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ blob: 'x' })));
await check('9.16 expense with a description of 501 characters', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ description: 'x'.repeat(501) })));
await check('9.17 expense with a description of 500 and a note of 2000', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version({ description: 'x'.repeat(500), note: 'x'.repeat(2000) })));
await check('9.18 expense with a note of 2001 characters', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ note: 'x'.repeat(2001) })));
await check('9.19 expense in a currency that is not a code', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ currency: 'baht' })));
await check('9.20 expense with a category id of 129 characters', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ categoryId: 'x'.repeat(129) })));
await check('9.23 no rate, and text where its scale goes', 'deny', {}, () => entry('groups/g1/entries/e1', 'bob', version({ rateScale: 'x'.repeat(1000) })));
await check('9.24 a foreign expense with its rate', 'allow', {}, () => entry('groups/g1/entries/e1', 'bob', version({ currency: 'USD', amountMinor: 300, rateUnscaled: 3333, rateScale: 2 })));
await check('9.25 payment with a field the app never sends', 'deny', {}, () => entry('groups/g1/entries/p1', 'bob', payment({ note: 'x' })));
await check('9.26 payment removal with a field the app never sends', 'deny', {}, () => entry('groups/g1/entries/r1', 'bob', { kind: 'paymentRemoval', paymentId: 'p1', removed: true, authorMemberId: 'mBob', blob: 'x' }));
await check('9.27 payment removal with a payment id of 129 characters', 'deny', {}, () => entry('groups/g1/entries/r1', 'bob', { kind: 'paymentRemoval', paymentId: 'x'.repeat(129), removed: true, authorMemberId: 'mBob' }));
await check('9.28 repeat with a field the app never sends', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ blob: 'x' })));
await check('9.29 repeat in a currency that is not a code', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ currency: 'baht' })));
await check('9.30 repeat with an unknown split', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ splitMode: 'Bogus' })));
await check('9.31 repeat with a description of 501 characters', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ description: 'x'.repeat(501) })));
await check('9.32 repeat with only an author and a kind', 'deny', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), { authorUid: 'bob', createdAt: 1, syncedAt: serverTimestamp(), kind: 'Rent', active: true }));
await check('9.33 foreign repeat with its rate, ending after 12 times', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat({ currency: 'USD', rateUnscaled: 3333, rateScale: 2, endKind: 'After', endTimes: 12 })));
const changeRepeat = (uid, over) => async () => {
  await setDoc(doc(as('bob'), 'groups/g1/repeats/r1'), repeat());
  return setDoc(doc(as(uid), 'groups/g1/repeats/r1'), repeat({ authorUid: uid, ...over }));
};
await check('9.34 another member stops a repeat and signs the change', 'allow', {}, changeRepeat('alice', { active: false }));
await check('9.35 a change that moves the repeat\'s creation time', 'deny', {}, changeRepeat('alice', { createdAt: 2 }));
await check('9.36 a change that adds a field', 'deny', {}, changeRepeat('alice', { blob: 'x' }));
await check('9.37 a change signed with someone else\'s account', 'deny', {}, changeRepeat('alice', { authorUid: 'bob' }));
await check('9.38 open an invite: the join document as the app writes it', 'allow', {}, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: Date.now() }));
await check('9.39 join document with a code that is not the group\'s', 'deny', {}, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'WRONG', at: 1 }));
await check('9.40 join document with a field the app never sends', 'deny', {}, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: 1, blob: 'x' }));
await check('9.41 join document written for another account', 'deny', {}, () => setDoc(doc(as('eve'), 'groups/g1/joins/bob'), { code: 'CODE1', at: 1 }));
await check('9.42 join document with a code that was switched off', 'deny', { group: { inviteActive: false } }, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: 1 }));
await check('9.43 device document with a field the app never sends', 'deny', {}, () => setDoc(doc(as('bob'), 'devices/devB'), { uid: 'bob', platform: 'ios', language: 'en', updatedAt: 2, blob: 'x' }));
await check('9.44 session with a second field', 'deny', {}, () => setDoc(doc(as('alice'), 'sessions/alice'), { signedInAt: AUTH_TIME, blob: 'x' }));
const invite = (over = {}) => ({ groupId: 'g1', groupName: 'Trip', active: true, creatorUid: 'alice', ...over });
await check('9.45 creator makes an invite as the app writes it', 'allow', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite()));
await check('9.46 invite with a field the app never sends', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ blob: 'x' })));
await check('9.47 invite with a group name of 101 characters', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ groupName: 'x'.repeat(101) })));
await check('9.48 a member who is not the creator makes an invite', 'deny', {}, () => setDoc(doc(as('bob'), 'invites/CODE2'), invite({ creatorUid: 'bob' })));
await check('9.49 a member who is not the creator switches the invite off', 'deny', {}, () => updateDoc(doc(as('bob'), 'invites/CODE1'), { active: false }));
await check('9.50 a member who is not the creator changes the group\'s code', 'deny', {}, () => updateDoc(doc(as('bob'), 'groups/g1'), { inviteActive: false }));
await check('9.51 creator points an invite at another group', 'deny', {}, () => updateDoc(doc(as('alice'), 'invites/CODE1'), { groupId: 'g2' }));

// 10. Reads
await check('10.1 a stranger reads the group', 'deny', {}, () => getDoc(doc(as('eve'), 'groups/g1')));
await check('10.2 a stranger lists the names', 'deny', {}, () => getDocs(collection(as('eve'), 'groups/g1/members')));
await check('10.3 a stranger lists the entries', 'deny', {}, () => getDocs(collection(as('eve'), 'groups/g1/entries')));
await check('10.4 a stranger lists the repeats', 'deny', {}, () => getDocs(collection(as('eve'), 'groups/g1/repeats')));
await check('10.5 a stranger lists the joins', 'deny', {}, () => getDocs(collection(as('eve'), 'groups/g1/joins')));
await check('10.6 a stranger reads someone\'s join document', 'deny', {}, () => getDoc(doc(as('eve'), 'groups/g1/joins/bob')));
await check('10.7 a stranger lists every group', 'deny', {}, () => getDocs(collection(as('eve'), 'groups')));
await check('10.8 someone with the live code reads the group and its names', 'allow', {}, async () => {
  await getDoc(doc(as('mallory'), 'groups/g1'));
  return getDocs(collection(as('mallory'), 'groups/g1/members'));
});
await check('10.9 someone with the live code lists the entries', 'deny', {}, () => getDocs(collection(as('mallory'), 'groups/g1/entries')));
await check('10.10 someone with the live code lists the repeats', 'deny', {}, () => getDocs(collection(as('mallory'), 'groups/g1/repeats')));
await check('10.11 a member reads entries, repeats and joins', 'allow', {}, async () => {
  await getDocs(collection(as('bob'), 'groups/g1/entries'));
  await getDocs(collection(as('bob'), 'groups/g1/repeats'));
  return getDocs(collection(as('bob'), 'groups/g1/joins'));
});
await check('10.12 another account reads a session', 'deny', {}, () => getDoc(doc(as('bob'), 'sessions/alice')));
await check('10.13 an account reads its own session', 'allow', {}, () => getDoc(doc(as('alice'), 'sessions/alice')));
await check('10.14 another account reads a phone\'s device document', 'deny', {}, () => getDoc(doc(as('bob'), 'devices/devA')));
await check('10.15 another account lists the device documents', 'deny', {}, () => getDocs(collection(as('bob'), 'devices')));
await check('10.16 an account lists its own device documents', 'allow', {}, () => getDocs(query(collection(as('alice'), 'devices'), where('uid', '==', 'alice'))));
await check('10.17 anyone signed in looks an invite up by its code', 'allow', {}, () => getDoc(doc(as('eve'), 'invites/CODE1')));
await check('10.18 nobody lists the invites', 'deny', {}, () => getDocs(collection(as('alice'), 'invites')));
await check('10.19 someone signed out reads the group', 'deny', {}, () => getDoc(doc(env.unauthenticatedContext().firestore(), 'groups/g1')));

// 11. H3: delete for everyone
const deleted = { group: { deleted: true, inviteActive: false } };
// What SharedSync.deleteForEveryone sends: one commit, every PromptPay ID cleared, the server's time stamped.
const deleteCommit = (uid, { at = serverTimestamp(), clear = ['mBob'] } = {}) => {
  const db = as(uid);
  const batch = writeBatch(db);
  batch.update(doc(db, 'invites/CODE1'), { active: false });
  batch.update(doc(db, 'groups/g1'), { deleted: true, inviteActive: false, deletedAt: at });
  clear.forEach((mid) => batch.update(doc(db, `groups/g1/members/${mid}`), { promptPay: null }));
  return batch.commit();
};
await check('11.1 app delete-for-everyone with the server\'s time and the PromptPay IDs cleared', 'allow', {}, () => deleteCommit('alice'));
await check('11.2 delete stamped with a time the phone chose', 'deny', {}, () => deleteCommit('alice', { at: Timestamp.fromMillis(1) }));
await check('11.3 a member who is not the creator sends the delete', 'deny', {}, () => deleteCommit('bob'));
await check('11.4 creator clears someone\'s PromptPay ID in a live group', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1/members/mBob'), { promptPay: null }));
await check('11.5 creator stamps a time without deleting', 'deny', {}, () => updateDoc(doc(as('alice'), 'groups/g1'), { deletedAt: serverTimestamp() }));
await check('11.6 deleted group: a member still reads its document', 'allow', deleted, () => getDoc(doc(as('bob'), 'groups/g1')));
await check('11.7 deleted group: a member lists the names', 'deny', deleted, () => getDocs(collection(as('bob'), 'groups/g1/members')));
await check('11.8 deleted group: a member lists the entries', 'deny', deleted, () => getDocs(collection(as('bob'), 'groups/g1/entries')));
await check('11.9 deleted group: a member lists the repeats', 'deny', deleted, () => getDocs(collection(as('bob'), 'groups/g1/repeats')));
await check('11.10 deleted group: the restore query still finds it, so the phone drops it', 'allow', deleted, () => getDocs(query(collection(as('bob'), 'groups'), where('memberUids', 'array-contains', 'bob'))));

// 12. L2: a creator who deletes their account switches the invite off as they leave
const leaveWith = (uid, mid, group) => {
  const db = as(uid);
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/g1/members/${mid}`), { claimedByUid: null, promptPay: null });
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayRemove(uid), ...group });
  return batch.commit();
};
await check('12.1 creator leaves and switches the invite off in one commit', 'allow', {}, () => leaveWith('alice', 'mAlice', { inviteActive: false }));
await check('12.2 creator leaves and switches the invite on', 'deny', { group: { inviteActive: false } }, () => leaveWith('alice', 'mAlice', { inviteActive: true }));
await check('12.3 a member who is not the creator leaves and switches the invite off', 'deny', {}, () => leaveWith('bob', 'mBob', { inviteActive: false }));
await check('12.4 creator leaves and also changes the code', 'deny', {}, () => leaveWith('alice', 'mAlice', { inviteActive: false, inviteCode: 'CODE9' }));
await check('12.5 after it, the code lets nobody in', 'deny', {}, async () => {
  await leaveWith('alice', 'mAlice', { inviteActive: false });
  return setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: 1 });
});

// 13. Invites that end
const past = { invite: { expiresAt: Timestamp.fromMillis(Date.now() - 60_000) } };
const future = { invite: { expiresAt: Timestamp.fromMillis(Date.now() + 86_400_000) } };
await check('13.1 creator makes an invite with an end date, as the app does', 'allow', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ expiresAt: Timestamp.fromMillis(Date.now() + 90 * 86_400_000) })));
await check('13.2 invite with an end date that is not a time', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ expiresAt: 'never' })));
await check('13.3 join document on an invite past its end date', 'deny', past, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: 1 }));
await check('13.4 join document on an invite before its end date', 'allow', future, () => setDoc(doc(as('eve'), 'groups/g1/joins/eve'), { code: 'CODE1', at: 1 }));
await check('13.5 someone who joined before the end date reads the names after it', 'deny', past, () => getDocs(collection(as('mallory'), 'groups/g1/members')));
await check('13.6 joiner adds themselves on an invite past its end date', 'deny', past, () => updateDoc(doc(as('mallory'), 'groups/g1'), { memberUids: arrayUnion('mallory') }));

// 14. M1: a member cannot store the code a removal rotates to
const rotate = async () => {
  const db = as('alice');
  await updateDoc(doc(db, 'invites/CODE1'), { active: false });
  await setDoc(doc(db, 'invites/CODE2'), { groupId: 'g1', groupName: 'Trip', active: true, creatorUid: 'alice' });
  await updateDoc(doc(db, 'groups/g1'), { inviteCode: 'CODE2', inviteActive: true });
};
await check('14.1 member stores the new code before the removal commits', 'deny', {}, async () => {
  await rotate();
  return setDoc(doc(as('bob'), 'groups/g1/joins/bob'), { code: 'CODE2', at: 2 });
});
await check('14.2 the race: the member tries the new code, the removal still goes through', 'allow', {}, async () => {
  await rotate();
  await setDoc(doc(as('bob'), 'groups/g1/joins/bob'), { code: 'CODE2', at: 2 }).catch(() => {});
  return removal('alice', { rotate: false });
});
await check('14.3 after it, the removed member cannot come back', 'deny', {}, async () => {
  await rotate();
  await setDoc(doc(as('bob'), 'groups/g1/joins/bob'), { code: 'CODE2', at: 2 }).catch(() => {});
  await removal('alice', { rotate: false });
  return updateDoc(doc(as('bob'), 'groups/g1'), { memberUids: arrayUnion('bob') });
});
await check('14.4 older build: a member opens their link again and rewrites the code they came with', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/joins/bob'), { code: 'CODE1', at: 2 }));
await check('14.5 creator, with no join document, writes one', 'deny', {}, () => setDoc(doc(as('alice'), 'groups/g1/joins/alice'), { code: 'CODE1', at: 2 }));
await check('14.6 member with no join document writes one', 'deny', three, () => setDoc(doc(as('carol'), 'groups/g1/joins/carol'), { code: 'CODE1', at: 2 }));
await check('14.7 someone not in the group moves their join to the new code', 'allow', {}, async () => {
  await rotate();
  return setDoc(doc(as('mallory'), 'groups/g1/joins/mallory'), { code: 'CODE2', at: 2 });
});

// 15. L4: the names on the invite, and the join written in the pick commit
// Member ids as the app makes them: lowercase UUIDs.
const uuid = (i) => `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`;
// What the app writes: one "id<TAB>name<LF>" line per name nobody holds.
const line = (id, name) => `${id}\t${name}\n`;
const names = (n, name = (i) => `N${i}`) => Array.from({ length: n }, (_, i) => line(uuid(i), name(i))).join('');
const pickCommit = (uid, code, claim) => {
  const db = as(uid);
  const batch = writeBatch(db);
  batch.set(doc(db, `groups/g1/joins/${uid}`), { code }, { mergeFields: ['code'] });
  claim(batch, db);
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayUnion(uid) });
  return batch.commit();
};
await check('15.1 creator makes an invite with its names', 'allow', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ names: names(2) })));
await check('15.2 invite whose names pass 25,000 characters', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ names: names(300, () => 'x'.repeat(60)) })));
await check('15.3 invite whose names are a list, not text', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ names: ['x'] })));
await check('15.4 a member keeps the live invite\'s names current', 'allow', {}, () => updateDoc(doc(as('bob'), 'invites/CODE1'), { names: names(1) }));
await check('15.5 a member writes names on an invite switched off', 'deny', { invite: { active: false } }, () => updateDoc(doc(as('bob'), 'invites/CODE1'), { names: names(1) }));
await check('15.6 a member writes names on a code the group moved on from', 'deny', { group: { inviteCode: 'CODE2' } }, () => updateDoc(doc(as('bob'), 'invites/CODE1'), { names: names(1) }));
await check('15.7 someone with the live code, not in the group, writes the names', 'deny', {}, () => updateDoc(doc(as('mallory'), 'invites/CODE1'), { names: names(1) }));
await check('15.8 a member writes names past 25,000 characters', 'deny', {}, () => updateDoc(doc(as('bob'), 'invites/CODE1'), { names: names(300, () => 'x'.repeat(60)) }));
await check('15.9 creator switches the invite off and empties its names', 'allow', {}, () => updateDoc(doc(as('alice'), 'invites/CODE1'), { active: false, names: '' }));
await check('15.10 pick commit: join, claim and come in together, nothing written before', 'allow', {}, () => pickCommit('eve', 'CODE1', claimName('eve', 'mCara')));
await check('15.11 pick commit, "I\'m not on the list"', 'allow', {}, () => pickCommit('eve', 'CODE1', addOwnName('eve')));
await check('15.12 pick commit with a code that is not the group\'s', 'deny', {}, () => pickCommit('eve', 'CODE9', claimName('eve', 'mCara')));
await check('15.13 pick commit with a code switched off', 'deny', { group: { inviteActive: false } }, () => pickCommit('eve', 'CODE1', claimName('eve', 'mCara')));
await check('15.14 pick commit sent by someone already in', 'deny', three, () => pickCommit('carol', 'CODE1', claimName('carol', 'mCara')));
await check('15.15 opening the link reads the invite and nothing else', 'deny', {}, async () => {
  await getDoc(doc(as('eve'), 'invites/CODE1'));
  return getDocs(collection(as('eve'), 'groups/g1/members'));
});

// 16. M5: PromptPay IDs only members read
const payDoc = (over = {}) => ({ promptPay: '0812345678', claimedByUid: 'bob', ...over });
await check('16.1 holder stores their PromptPay ID', 'allow', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc()));
await check('16.2 holder changes it', 'allow', {}, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc({ promptPay: '1234567890123' })));
await check('16.3 a member stores one on someone else\'s name', 'deny', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mAlice'), payDoc()));
await check('16.4 a member stores one on a name nobody holds', 'deny', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mCara'), payDoc()));
await check('16.5 one that names another account as its holder', 'deny', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc({ claimedByUid: 'alice' })));
await check('16.6 one in the wrong form', 'deny', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc({ promptPay: '12345' })));
await check('16.7 one with a field the app never sends', 'deny', { pay: {} }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc({ blob: 'x' })));
await check('16.8 someone with the live code stores one', 'deny', { pay: {} }, () => setDoc(doc(as('mallory'), 'groups/g1/pay/mCara'), payDoc({ claimedByUid: 'mallory' })));
await check('16.9 deleted group: holder stores one', 'deny', { pay: {}, group: { deleted: true } }, () => setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc()));
await check('16.10 a member reads every PromptPay ID', 'allow', {}, () => getDocs(collection(as('bob'), 'groups/g1/pay')));
await check('16.11 someone with the live code reads them', 'deny', {}, () => getDocs(collection(as('mallory'), 'groups/g1/pay')));
await check('16.12 someone with the live code reads one', 'deny', {}, () => getDoc(doc(as('mallory'), 'groups/g1/pay/mBob')));
await check('16.13 a stranger reads them', 'deny', {}, () => getDocs(collection(as('eve'), 'groups/g1/pay')));
await check('16.14 deleted group: a member reads them', 'deny', deleted, () => getDocs(collection(as('bob'), 'groups/g1/pay')));
await check('16.15 holder takes theirs away', 'allow', {}, () => deleteDoc(doc(as('bob'), 'groups/g1/pay/mBob')));
await check('16.16 another member takes it away', 'deny', three, () => deleteDoc(doc(as('carol'), 'groups/g1/pay/mBob')));
await check('16.17 creator takes it away while its holder is still in', 'deny', {}, () => deleteDoc(doc(as('alice'), 'groups/g1/pay/mBob')));
await check('16.18 new app removal: the commit also takes the PromptPay ID away', 'allow', {}, async () => {
  await rotate();
  const db = as('alice');
  const batch = writeBatch(db);
  batch.update(doc(db, 'groups/g1/members/mBob'), { archived: true, claimedByUid: null, promptPay: null });
  batch.delete(doc(db, 'groups/g1/pay/mBob'));
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayRemove('bob') });
  return batch.commit();
});
const leaveDeleting = (pay) => () => {
  const db = as('bob');
  const batch = writeBatch(db);
  batch.update(doc(db, 'groups/g1/members/mBob'), { claimedByUid: null, promptPay: null });
  batch.delete(doc(db, 'groups/g1/pay/mBob'));
  batch.update(doc(db, 'groups/g1'), { memberUids: arrayRemove('bob') });
  return batch.commit();
};
await check('16.19 new app leave commit with a PromptPay ID', 'allow', {}, leaveDeleting());
await check('16.20 new app leave commit with none stored', 'allow', { pay: {} }, leaveDeleting());
await check('16.21 new app delete-for-everyone also takes every PromptPay ID away', 'allow', {}, () => {
  const db = as('alice');
  const batch = writeBatch(db);
  batch.update(doc(db, 'invites/CODE1'), { active: false, names: '' });
  batch.update(doc(db, 'groups/g1'), { deleted: true, inviteActive: false, deletedAt: serverTimestamp() });
  batch.update(doc(db, 'groups/g1/members/mBob'), { promptPay: null });
  batch.delete(doc(db, 'groups/g1/pay/mBob'));
  return batch.commit();
});
await check('16.22 older build: holder still sets the ID on their name', 'allow', {}, () => updateDoc(doc(as('bob'), 'groups/g1/members/mBob'), { promptPay: '0899999999' }));
await check('16.23 new app moves its ID: stored in pay, cleared from the name', 'allow', { pay: {} }, async () => {
  await setDoc(doc(as('bob'), 'groups/g1/pay/mBob'), payDoc());
  return updateDoc(doc(as('bob'), 'groups/g1/members/mBob'), { promptPay: null });
});

// 17. Review of L4: names die with the invite, and every entry has the app's shape
const listed = { invite: { names: names(1) } };
const pastListed = { invite: { names: names(1), expiresAt: Timestamp.fromMillis(Date.now() - 60_000) } };
const futureListed = { invite: { names: names(1), expiresAt: Timestamp.fromMillis(Date.now() + 86_400_000) } };
const setNames = (value) => () => updateDoc(doc(as('bob'), 'invites/CODE1'), { names: value });
await check('17.1 someone with the code reads an invite past its end date', 'deny', pastListed, () => getDoc(doc(as('eve'), 'invites/CODE1')));
await check('17.2 a member reads an invite past its end date', 'deny', pastListed, () => getDoc(doc(as('bob'), 'invites/CODE1')));
await check('17.3 someone with the code reads an invite before its end date', 'allow', futureListed, () => getDoc(doc(as('eve'), 'invites/CODE1')));
await check('17.4 a code that matches nothing reads as missing', 'allow', {}, () => getDoc(doc(as('eve'), 'invites/NOPE')));
await check('17.5 a member puts names on an invite past its end date', 'deny', pastListed, setNames(names(2)));
await check('17.6 a member puts names on an invite before its end date', 'allow', futureListed, setNames(names(2)));
await check('17.7 a name under an id that is not the app\'s', 'deny', listed, setNames(line('mBob', 'Aye')));
await check('17.8 a name under an id in capitals', 'deny', listed, setNames(line('ABCDEF00-0000-4000-8000-000000000001', 'Aye')));
await check('17.9 a name of 61 characters', 'deny', listed, setNames(line(uuid(1), 'x'.repeat(61))));
await check('17.10 a name of 60 characters', 'allow', listed, setNames(line(uuid(1), 'x'.repeat(60))));
await check('17.11 an empty name', 'deny', listed, setNames(line(uuid(1), '')));
await check('17.12 names that are not text', 'deny', listed, setNames(7));
await check('17.13 names as a map of entries', 'deny', listed, setNames({ [uuid(1)]: 'Aye' }));
await check('17.14 a line with a third part', 'deny', listed, setNames(line(uuid(1), `Aye\t${uuid(2)}`)));
await check('17.15 one bad line among good ones', 'deny', listed, setNames(names(3) + line('mBob', 'Aye')));
await check('17.16 names as the earlier list of entries', 'deny', listed, setNames([{ id: uuid(1), name: 'Aye' }]));
await check('17.17 creator makes an invite with a bad line', 'deny', {}, () => setDoc(doc(as('alice'), 'invites/CODE2'), invite({ names: line('mBob', 'Aye') })));
await check('17.18 the names emptied', 'allow', listed, setNames(''));
await check('17.19 a last line with no line break', 'deny', listed, setNames(line(uuid(1), 'Aye').trimEnd()));
await check('17.20 names in Burmese', 'allow', listed, setNames(line(uuid(1), 'မောင်မောင်')));
// What SharedSync.join does with a name off the invite: register, read the name as the group holds it, then claim.
await check('17.21 the pick: join registered, the name read back, then claimed', 'allow', {}, async () => {
  const db = as('eve');
  await setDoc(doc(db, 'groups/g1/joins/eve'), { code: 'CODE1' });
  const cara = await getDoc(doc(db, 'groups/g1/members/mCara'));
  if (cara.data().name !== 'Cara' || cara.data().claimedByUid !== null) throw new Error('name not as listed');
  return joinCommit('eve', claimName('eve', 'mCara'));
});
await check('17.22 before the join is registered, the name cannot be read', 'deny', {}, () => getDoc(doc(as('eve'), 'groups/g1/members/mCara')));

await env.cleanup();
for (const [status, expect, name, why] of results) console.log(`${status}  ${expect.padEnd(5)}  ${name}${why ? `  -> ${why}` : ''}`);
const failed = results.filter(([status]) => status === 'FAIL').length;
console.log(`\n${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
