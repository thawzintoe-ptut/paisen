import { HttpError } from './http';

/**
 * M6: what a member's phone may say happened. The phone that receives it words the sentence from
 * its own copy of the group, so nothing a sender types reaches another member's lock screen.
 */
export const NOTICE_KINDS = [
  'expenseAdded',
  'expenseEdited',
  'expenseRemoved',
  'expensePutBack',
  'repeatAdded',
  'paid',
  'paymentRemoved',
  'paymentPutBack',
  'repeatSetUp',
  'repeatChanged',
  'repeatStopped',
  'personAdded',
  'personRemoved',
  'nameChanged',
  'renamed',
  'joined',
  'left',
  'deleted',
] as const;

export type NoticeKind = (typeof NOTICE_KINDS)[number];

export interface EventNotice {
  shape: 'event';
  groupId: string;
  kind: NoticeKind;
  /** The expense, payment, repeat or member the change is about. */
  about?: string;
  from?: string;
  to?: string;
  /** Whole minor units of [currency]. */
  amount?: number;
  currency?: string;
}

/** What builds up to v0.4.0-rc.1 send: the phone's own sentences. */
export interface LegacyNotice {
  shape: 'legacy';
  groupId: string;
  title: string;
  body: { en: string; my: string; th?: string };
}

export type Notice = EventNotice | LegacyNotice;

export type Language = 'en' | 'my' | 'th';

const MAX_GROUP_ID_LENGTH = 1500;
const MAX_TITLE_LENGTH = 200;
const MAX_LEGACY_BODY_LENGTH = 1000;
// Room ids are UUIDs; this also keeps them safe in a data payload.
const ID = /^[A-Za-z0-9_-]{1,128}$/;
const CURRENCY = /^[A-Z]{3}$/;
// The rules' own money cap (M3).
const MAX_AMOUNT = 10_000_000_000_000;
// What reaches a lock screen from an old build's sentence.
const LEGACY_SENT_LENGTH = 160;
const KINDS: ReadonlySet<string> = new Set(NOTICE_KINDS);

/**
 * A body with `kind` is the structured notice. One with `body` sentences is the old shape, taken
 * only while [acceptLegacy] holds.
 */
export function parseNotice(body: unknown, acceptLegacy: boolean): Notice {
  const fields = asRecord(body);
  const groupId = text(fields?.groupId, MAX_GROUP_ID_LENGTH);
  if (!groupId || groupId.includes('/')) throw badBody();
  if (fields?.kind !== undefined) return parseEvent(fields, groupId);
  if (!acceptLegacy) throw new HttpError(400, 'old notice shape');
  return parseLegacy(fields, groupId);
}

function parseEvent(fields: Record<string, unknown>, groupId: string): EventNotice {
  const kind = fields.kind;
  if (typeof kind !== 'string' || !KINDS.has(kind)) throw badBody();
  const about = optionalId(fields.about);
  const from = optionalId(fields.from);
  const to = optionalId(fields.to);
  const amount = fields.amount;
  const amountOk =
    amount === undefined || (typeof amount === 'number' && Number.isSafeInteger(amount) && amount >= 0 && amount <= MAX_AMOUNT);
  const currency = fields.currency;
  const currencyOk = currency === undefined || (typeof currency === 'string' && CURRENCY.test(currency));
  if (about === null || from === null || to === null || !amountOk || !currencyOk) throw badBody();
  return {
    shape: 'event',
    groupId,
    kind: kind as NoticeKind,
    about,
    from,
    to,
    amount: amount as number | undefined,
    currency: currency as string | undefined,
  };
}

function parseLegacy(fields: Record<string, unknown> | undefined, groupId: string): LegacyNotice {
  const sentences = asRecord(fields?.body);
  const title = text(fields?.title, MAX_TITLE_LENGTH);
  const en = text(sentences?.en, MAX_LEGACY_BODY_LENGTH);
  const my = text(sentences?.my, MAX_LEGACY_BODY_LENGTH);
  // Optional, since apps built before Thai never send it. When sent, it is checked like the others.
  const th = text(sentences?.th, MAX_LEGACY_BODY_LENGTH);
  const badTh = sentences?.th !== undefined && !th;
  if (!title || !en || !my || badTh) throw badBody();
  return {
    shape: 'legacy',
    groupId,
    title: sanitize(title),
    body: { en: sanitize(en), my: sanitize(my), th: th === undefined ? undefined : sanitize(th) },
  };
}

// Scheme links, www. hosts and bare domains such as bit.ly/x: a notice never carries somewhere to go.
const LINK = /(?:[a-z][a-z0-9+.-]*:\/\/|www\.)\S*|\b(?:[a-z0-9-]+\.)+[a-z]{2,24}\b(?:\/\S*)?/gi;
// Control and bidi-override characters, which can reorder or hide what a banner shows.
const HIDDEN = /[\u0000-\u001f\u007f-\u009f​-‏‪-‮⁦-⁩﻿]/g;

/** An old build's free text, without links or hidden characters, on one line, cut to what a banner shows. */
export function sanitize(sentence: string): string {
  const cleaned = sentence.replace(HIDDEN, ' ').replace(LINK, '…').replace(/\s+/g, ' ').trim();
  const characters = Array.from(cleaned);
  return characters.length <= LEGACY_SENT_LENGTH ? cleaned : `${characters.slice(0, LEGACY_SENT_LENGTH - 1).join('')}…`;
}

/**
 * The sentence a build up to v0.4.0-rc.1 shows for a structured notice, since it cannot word one:
 * SharedHomeCopy.newSince(1), the line iOS already posts for new rows.
 */
export const OLD_BUILD_SENTENCE: Record<Language, string> = {
  en: '1 new',
  my: 'အသစ် ၁ ခု',
  th: 'ใหม่ 1 รายการ',
};

/** FCM data values for one phone: strings only, the event's fields only when set. */
export function noticeData(notice: Notice, senderUid: string, language: Language): Record<string, string> {
  if (notice.shape === 'legacy') {
    return { body: notice.body[language] ?? notice.body.en };
  }
  const data: Record<string, string> = { body: OLD_BUILD_SENTENCE[language], kind: notice.kind, by: senderUid };
  if (notice.about !== undefined) data.about = notice.about;
  if (notice.from !== undefined) data.from = notice.from;
  if (notice.to !== undefined) data.to = notice.to;
  if (notice.amount !== undefined) data.amount = String(notice.amount);
  if (notice.currency !== undefined) data.currency = notice.currency;
  return data;
}

function badBody(): HttpError {
  return new HttpError(400, 'bad body');
}

/** Undefined when absent, null when present but not an id. */
function optionalId(value: unknown): string | undefined | null {
  if (value === undefined) return undefined;
  return typeof value === 'string' && ID.test(value) ? value : null;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown, maxLength: number): string | undefined {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength ? value : undefined;
}
