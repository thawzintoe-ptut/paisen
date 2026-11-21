# PaiSeN privacy policy

Last updated: 19 November 2026

PaiSeN is an expense tracker made by Thaw Zin Toe. This policy explains what the app does with
your data. The short version: your own spending stays on your phone. Shared groups use the
internet after you sign in to share, and the app checks with Google which of its features are on,
sending nothing about your spending. The one exception you choose: on Android, saying an
expense in a language your phone cannot recognise offline sends your voice to Google to turn it
into text.

## What stays on your phone

These are stored in the app's private storage and are never sent anywhere:

- your transactions, categories, sub-categories, notes and shop rules
- the bank slips the app reads, and the text it reads from them
- your reports and settings

On iPhone, if you ask Siri or a Shortcut for your spending, the app gives the total to Siri or
that Shortcut on your phone, after the phone is unlocked.

The app has no ads, no analytics and no crash reporting. It does not sell or share your data.
If the app crashes, it writes a short report that stays on your phone unless you choose Share.

The app also turns off Android cloud backup, Android phone-to-phone transfer and iCloud backup for
its ledger. To move your ledger to a new phone, use **Settings › Export** and **Import**. The export
file goes only where you choose to put it. On iPhone, the account name the app learns from your own
slips stays out of backups too. The app's other settings are still part of an iCloud or computer
backup of the phone if you have one turned on.

If you save a "Pay me" picture, it goes to your phone's Pictures folder, where your other apps with
photo access can see it.

## Permissions

| Permission | Why the app asks |
|---|---|
| Photos | To read payment slips from the folders or album you choose. Slips are read on the phone. |
| Microphone and speech recognition | To turn what you say in Quick add into an expense. On iPhone, recognition runs on the phone and nothing is recorded or uploaded. On Android, a language with an offline pack is recognised on the phone. A language without one, such as Burmese, goes to the phone's speech service, usually Google, which sends your voice to its servers and returns the words. Quick add says so before you speak, and PaiSeN keeps no audio. If you tap Download for a voice language, Android fetches it from Google. |
| Notifications | To tell you when someone changes a shared group, and the day before a repeating shared expense adds itself. |
| Internet | For shared groups, and to check which app features are on (see Feature switches). Online voice is sent by Android's speech service, not by PaiSeN. |

On Android, the slip reader is Google's ML Kit with its model built into the app. The app removes
ML Kit's usage-statistics uploader, so ML Kit sends nothing to Google.

## Feature switches

Some features can be turned on or off without a new version of the app, for example the support for
other currencies. To know which are on, the app asks Google Firebase Remote Config at most twice a
day, after it has opened. It sends:

- the app's version and the language you use it in
- the app's Firebase installation ID and its sign-in token, which Firebase makes for this install of
  PaiSeN; they are not tied to your name, your Google account or your spending

To make that installation ID, the app starts Google Firebase on the phone the first time it checks,
even if you never sign in.

It sends nothing about your transactions, slips, reports, categories or groups. The answer is a short
list of features and whether each is on. If the check fails or you are offline, the app uses the
answer it last saved, and otherwise its own defaults.

## Shared groups

Shared groups are optional. If you never sign in, nothing about your spending or your groups leaves
your phone; only the feature check below is sent.

When you sign in to share, the app uses **Sign in with Google**, or **Sign in with Apple** on
iPhone, through Google Firebase Authentication. Firebase keeps the name and email address that
account gives it so it can recognise you next time.

For each shared group, these are stored with Google Firebase (Cloud Firestore) in Singapore:

- the group's name and the members' names
- the shared expenses and payments, and who added each one
- a PromptPay ID, if you add one
- a push token for each phone, so it can be told when someone changes the group
- which phone your account is signed in on (one phone at a time)
- with Play on: the day a trip was marked ended, the word a member left on a payment (Thanks, Well
  played or Cheers) and who left it, and whether you turned *Stamps and awards* off for groups. A
  word you leave stores the id of the payment it sits on, and goes when you leave the group or
  delete your account.

Your own play (runs, stamps, guesses, challenges, goal jars and month seals) is worked out on your
phone, is kept in your backup file, and is never part of a group.

Every member of a group can see that group's data. Your own spending, slips and reports are not
part of a shared group and never leave your phone.

### Notifications

When you change a shared group, your phone sends a short notice (the group, its title and a line of
text such as who added what) to a small server run on Cloudflare Workers. The server checks that
you are a member, then asks Firebase Cloud Messaging (Google), and Apple's push service for
iPhones, to deliver the notice to the other members' phones. The server does not store the notice.
Cloudflare keeps error logs for a few days.

## How long data is kept

- Data on your phone stays until you delete it or uninstall the app.
- Shared-group data stays while the group exists. When a group's creator deletes it for everyone,
  the group is closed and leaves every member's phone, but its data is not yet erased from Firebase:
  people who were members can still be given it by the server. To have a group's data erased, email
  the address below.
- When you **delete your account** in Settings, the app deletes your Firebase account and removes
  you from every group. The expenses and payments you added stay in each group's history, so the
  other members' balances stay correct.
- When you **sign out**, shared groups leave your phone. Your own spending stays.

## Delete your account

**In the app:** Settings → Shared groups account → Delete account. The app may ask you to sign
in once more. It then:
- removes you from every shared group, and frees your name in them
- removes this phone's push registration
- deletes your Firebase account, with its name, email address and user ID
- removes the shared groups from this phone

The expenses and payments you added stay in each group's history, so the other members'
balances stay correct. Those entries, and the record of the invite you joined with, keep your
Firebase user ID, a random code that no longer leads to a name or an email address once the
account is gone. Your own spending on the phone is not touched; uninstalling the app
removes it.

**Without the app:** email developer.ptut@gmail.com from the account you signed in with,
with the subject "Delete my PaiSeN account". Your account and shared-group data are deleted
within 30 days, and you get a reply when it is done.

## Children

PaiSeN is not made for children under 13 and does not knowingly collect their data.

## Changes to this policy

If this policy changes, the new version will be published in this file, with a new date at the
top. You can see every past version in the repository's history.

## Contact

Questions about your data, or a request to delete it: developer.ptut@gmail.com

**Settings › Send feedback** opens your own email app with the app version and your phone model
filled in. Nothing is sent unless you send that email.
