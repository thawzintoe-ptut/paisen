# Slip transcripts

Recorded K PLUS slips, as text. The images never live here — `.githooks/pre-commit` rejects them —
so this folder is what lets the test suite run the parser without anyone's bank details in the repo.

Issue #41 is the collection task. Until it lands, the confirm-slip panel (#14) is a placeholder and
the parser cases (#12) are guesses.

## Before you capture

**Set K PLUS to English.** C-3, and the on-device reader ships no Thai model, so a Thai slip is not
a harder case — it is an unreadable one, and it will not tell you anything useful about the parser.

## What to capture

Fifteen slips of the same transfer proves one layout. The value is in the spread, so cover:

| Shape | Why it differs |
|---|---|
| Transfer out | The common case, and the one the ledger is mostly made of |
| Bill payment | Carries a biller name where a transfer carries a person |
| PromptPay | Identifies by phone or national ID, not by account |
| Top-up | Small amounts, and often a different header |
| One the reader will get wrong | Glare, a fold, a dark screenshot. `Unreadable` has to be a real case, not a theory |

## Redact at capture, not later

NFR-5: an account number is read off a slip and discarded. It must never reach this folder, in any
form, including the masked one. A mask that keeps the last four digits still carries four real
digits, and four digits beside a merchant and a timestamp is enough to identify an account.

Two guards enforce this, so a slip that keeps its digits will not commit:

- `.githooks/pre-commit` — fires at the moment you commit
- `./gradlew :composeApp:verifyNoAccountNumbers` — wired into `check`, so `--no-verify` does not get past CI

Both reject three shapes: a Thai account number in its three-one-five-one grouping, masked or not;
a card number in groups of four; and any bare run of ten or more digits. Replace the whole run with `[redacted]`. Do not
shorten it, and do not leave the last four.

**Keep the reference number.** `0142K9` is not an account number, it is half of the F3 duplicate key,
and the importer needs it.

## The format

One file per slip. The reader returns raw text, so that is what `lines` holds — an array only
because a human has to type these, and that is easier to edit than one string full of `\n`.

```json
{
  "id": "qr-lawson-02",
  "kind": "qr_merchant",
  "lines": ["Payment Completed", "16 Aug 26  10:44 AM", "..."],
  "expected": {
    "occurredAt": "2026-08-16T10:44",
    "amount": "151.00",
    "counterparty": "LAWSON108_P3793 THE LIVIN",
    "reference": "0162000000011BQR00011"
  },
  "transactionIdFormat": "alphanumeric",
  "uncertain": ["counterparty"],
  "notes": "Repeat merchant, different day and amount."
}
```

`expected` is what the parser should come out with, so a fixture is a test case rather than just a
sample.

**`expected` is the parser contract, not a transcription of the paper slip** (#60). Where a slip
prints a Thai trade name above an English company name, `counterparty` is the English line, because
the reader ships no Thai model (C-3) and never passes the Thai line on. A `null` counterparty means
the recipient block has no Latin line at all, so the parser reads the whole slip as `Unreadable`:
`qr-thai-merchant` is that case. The Thai names those three slips print are in their `lines`.
On a real Android phone ML Kit's Latin model does pass a Thai line on, as noise such as
`msus:quus:óiÜangönms` (16 Oct 26). It stays the shop rule's key; `isReaderNoise` keeps it off screen. `uncertain` is the list #14 needs: the fields the reader got wrong or nearly wrong on this
slip. That list decides where the "read, low confidence" marks sit on the confirm screen, and it is
why a made-up slip was never good enough.

## What was redacted, and what that costs

The layout, the labels, the amounts, the dates and the merchant names are exactly as printed. Every
identifier is synthetic and shape-preserving:

| On the slip | In the fixture | Why |
|---|---|---|
| Account, last four showing | Every position masked | NFR-5. The app discards it while reading, so a fixture holding it would be the only place it survives |
| PromptPay ID, phone or national-ID shaped | Every position masked | Same, and it identifies someone who is not the account holder |
| Transaction ID | Synthetic, same length | A live handle to a real transaction through the slip-verify QR |
| Biller and QR references | Synthetic, same length, date prefix kept | On a bill payment the first reference is often a customer number |
| A person's name | Pseudonym | A transfer names someone who never agreed to be in this repo |

**One cost worth knowing.** Five of the nineteen slips print an all-digit transaction ID. A synthetic
one of the same shape would be a bare digit run, which the guard refuses and should. Those fixtures
carry a letter in the ID instead and record `"transactionIdFormat": "numeric"`, so #12 knows the
all-digit form exists and can build that case in code rather than in a committed file.

The same applies to the account. No fixture holds a mask with digits in it, so #12 cannot use one to
prove the parser drops them. That assertion has to build its input in the test.

## Done

- [x] 19 slips, six layouts, every identifier synthetic
- [x] One transcript here per slip
- [x] Thai merchant names captured — the reader has no Thai model, so those are the `Unreadable` cases
- [ ] The `uncertain` fields written into #14, the parser cases into #12

This brief holds no account number of its own, on purpose. It is scanned by the same guard as every
other file here, and an example worth writing down is an example worth rejecting.
