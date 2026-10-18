# PaiSeN privacy policy

Last updated: 18 October 2026

PaiSeN is an expense tracker made by Thaw Zin Toe. This policy explains what the app does with
your data. The short version: your own spending stays on your phone. Only shared groups use the
internet, and only after you sign in to share.

## What stays on your phone

These are stored in the app's private storage and are never sent anywhere:

- your transactions, categories, sub-categories, notes and shop rules
- the bank slips the app reads, and the text it reads from them
- your reports and settings

The app has no ads, no analytics and no crash reporting. It does not sell or share your data.

The app also turns off Android cloud backup, Android phone-to-phone transfer and iCloud backup for
its data. To move your ledger to a new phone, use **Settings › Export** and **Import**. The export
file goes only where you choose to put it.

## Permissions

| Permission | Why the app asks |
|---|---|
| Photos | To read payment slips from the folders or album you choose. Slips are read on the phone. |
| Microphone and speech recognition (iOS) | To turn what you say in Quick add into an expense. Recognition runs on the iPhone, and nothing is recorded or uploaded. On Android the app uses the phone's on-device recognizer. |
| Notifications | To tell you when someone changes a shared group. |
| Internet | For shared groups only. |

On Android, the slip reader is Google's ML Kit with its model built into the app. The app removes
ML Kit's usage-statistics uploader, so ML Kit sends nothing to Google.

## Shared groups

Shared groups are optional. If you never sign in, nothing leaves your phone.

When you sign in to share, the app uses **Sign in with Google** through Google Firebase
Authentication. Firebase keeps your Google account's name and email address so it can recognise
you next time.

For each shared group, these are stored with Google Firebase (Cloud Firestore) in Singapore:

- the group's name and the members' names
- the shared expenses and payments, and who added each one
- a PromptPay ID, if you add one
- a push token for each phone, so it can be told when someone changes the group
- which phone your account is signed in on (one phone at a time)

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
- Shared-group data stays while the group exists.
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
balances stay correct. Your own spending on the phone is not touched; uninstalling the app
removes it.

**Without the app:** email developer.ptut@gmail.com from the Google account you signed in with,
with the subject "Delete my PaiSeN account". Your account and shared-group data are deleted
within 30 days, and you get a reply when it is done.

## Children

PaiSeN is not made for children under 13 and does not knowingly collect their data.

## Changes to this policy

If this policy changes, the new version will be published in this file, with a new date at the
top. You can see every past version in the repository's history.

## Contact

Questions about your data, or a request to delete it: developer.ptut@gmail.com
