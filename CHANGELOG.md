# Changelog

All notable changes to PaiSeN are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

Each release's notes are made from its `feat:` and `fix:` commit titles (`scripts/release/notes.sh`)
and shown on its GitHub release; this file is no longer a step in releasing.

## [Unreleased]

The first public version.

### Added

- Play, in *Settings › Play*: a tray-clear run with freezes, stamps and a stamp book, *Guess the
  week*, a 30-second daily question, one PaiSeN line on Today, week challenges, goal jars, month
  stories, closing a month and a money score. Your own play is worked out on the phone. In shared
  groups: a settle plan, a who-pays wheel, an *All square* stamp, reactions on payments, monthly
  awards and a trip recap. Each part stays in debug builds until it has been checked on a phone,
  and Remote Config's `play_on` turns all of it off.
- Reading K PLUS payment slips from your photos on the phone, with ML Kit on Android and Apple
  Vision on iOS.
- On Android 14 and later, *Select photos* works: the photos you pick are read, and Today and
  *Photo folders* say that new slips stay hidden until you pick them or allow all photos.
- A *To check* queue for slips the app cannot read, with the slip's picture.
- Shop rules: a shop's category is remembered, and the record shows which rule filed it.
- Today, week and month totals, top categories, and a comparison with a usual day.
- Reports for 3 months, 6 months and a year, by category and sub-category.
- History with search, and adding or editing a transaction with its date, time, category,
  sub-category and note. The add sheet asks Expense or Income first, colours the amount to match,
  shows each category's icon, puts the note and the time on one line, and keeps *Save* above the
  keyboard with the amount on it. A tap on *Save* too early says what is missing.
- Delete with undo.
- Quick add by typing or speaking in English, Thai or Burmese. Speech is recognised on the phone
  where it has an offline pack. On Android, other languages such as Burmese are recognised online
  by Google, and the first time for each language asks before your voice is sent. The voice
  language sits beside the mic. Siri and app icon shortcuts on iOS.
- Shared groups: split costs, see balances, record payments, settle with a PromptPay QR, invite by
  link or QR code, and get told about changes. An open group checks for changes every 15 seconds,
  or at once when you pull it down.
- Adding a shared expense: one tap picks who paid and how it splits. Split equally, or one person
  is owed the full amount. Exact, percent and shares are under *More options*. A blank
  description saves as the category's name.
- Asking to be paid: when someone owes you, *Record a payment* has a button that shares the amount
  and your PromptPay number to a chat.
- Export and import of the whole ledger as a file.
- Send feedback in Settings: opens your email app with the app version and phone model filled in.
- English, Burmese and Thai, in light and dark. Thai shows years in the Buddhist Era, and a phone
  set to Thai opens in Thai until you pick another language. Push notices reach each phone in its
  own language.
- An iPhone build (`.ipa`) on each GitHub release, to install with SideStore or AltStore and your
  own Apple ID. It has no Sign in with Apple or push notifications, and an invite link opens in
  Safari, where **Open in PaiSen** takes you to the app.

### Security

- Android cloud backup, Android phone-to-phone transfer and iCloud backup are off for the app's
  data.
- Firestore security rules with 64 tests run against the emulator.
- Push notifications start only after you sign in to share a group. A phone that never
  signs in sends nothing to Firebase.

[Unreleased]: https://github.com/thawzintoe-ptut/PaiSen/commits/main
