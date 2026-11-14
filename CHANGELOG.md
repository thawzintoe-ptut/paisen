# Changelog

All notable changes to PaiSeN are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

The first public version.

### Added

- Reading K PLUS payment slips from your photos on the phone, with ML Kit on Android and Apple
  Vision on iOS.
- A *To check* queue for slips the app cannot read, with the slip's picture.
- Shop rules: a shop's category is remembered, and the record shows which rule filed it.
- Today, week and month totals, top categories, and a comparison with a usual day.
- Reports for 3 months, 6 months and a year, by category and sub-category.
- History with search, and adding or editing a transaction with its date, time, category,
  sub-category and note.
- Delete with undo.
- Quick add by typing or speaking in English, Thai or Burmese. Speech is recognised on the phone
  where it has an offline pack. On Android, other languages such as Burmese are recognised online
  by Google, and the sheet says so. Siri and app icon shortcuts on iOS.
- Shared groups: split costs, see balances, record payments, settle with a PromptPay QR, invite by
  link or QR code, and get told about changes. An open group checks for changes every 15 seconds,
  or at once when you pull it down.
- Adding a shared expense: one tap picks who paid and how it splits. Split equally, or one person
  is owed the full amount. Exact, percent and shares are under *More options*. A blank
  description saves as the category's name.
- Export and import of the whole ledger as a file.
- Send feedback in Settings: opens your email app with the app version and phone model filled in.
- English and Burmese, in light and dark.
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
