# Thai review: shared groups

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in. The choices in `th-shell.md` apply here too.

Choices to check in this area:

- A shared group is กลุ่ม; Burmese keeps "Shared group" in English, Thai does not.
- Balances: คุณจะได้รับ (you are owed), คุณต้องจ่าย (you owe), ต้องจ่ายคุณ (owes you), เคลียร์แล้ว (settled up).
- Push notices reach other members' lock screens. `{actor}` is always a member's own name, never คุณ.
- A repeat is รายการประจำ, as in `th-slips-setup.md`.
- Delete is ลบ and Remove is นำ…ออก: a removed expense or payment can come back, a deleted group cannot.
- Settle up is เคลียร์ยอด. Is that too casual for the app, or is ชำระยอด better?

## The Shared tab: shared words

| English | Thai | Where |
|---|---|---|
| Shared | กลุ่ม | SharedCopy.kt `title` |
| Someone | ใครบางคน | SharedCopy.kt `someone` |
| Group | กลุ่ม | SharedCopy.kt `group` |
| People | คน | SharedCopy.kt `people` |
| Activity | กิจกรรม | SharedCopy.kt `activity` |
| Add person | เพิ่มคน | SharedCopy.kt `addPerson` |
| New group | กลุ่มใหม่ | SharedCopy.kt `newGroup` |
| Group name | ชื่อกลุ่ม | SharedCopy.kt `groupName` |
| Paid by | จ่ายโดย | SharedCopy.kt `paidBy` |
| Chosen | เลือกแล้ว | SharedCopy.kt `chosen` |
| Sign in | ลงชื่อเข้าใช้ | SharedCopy.kt `signIn` |
| Who pays whom | ใครจ่ายให้ใคร | SharedCopy.kt `whoPaysWhom` |
| Invite people | ชวนคน | SharedCopy.kt `invitePeople` |
| This group is gone | กลุ่มนี้ไม่อยู่แล้ว | SharedCopy.kt `thisGroupIsGone` |
| all yours | ของคุณทั้งหมด | SharedCopy.kt `allYours` |
| you are owed | คุณจะได้รับ | SharedCopy.kt `youAreOwed` |
| you owe | คุณต้องจ่าย | SharedCopy.kt `youOwe` |
| owes you | ต้องจ่ายคุณ | SharedCopy.kt `owesYou` |
| settled up | เคลียร์แล้ว | SharedCopy.kt `settledUp` |
| Trip | ทริป | SharedCopy.kt `typeTrip` |
| Flat | ห้องเช่า | SharedCopy.kt `typeFlat` |
| Couple | คู่รัก | SharedCopy.kt `typeCouple` |
| Bank transfer | โอนผ่านธนาคาร | SharedCopy.kt `methodBankTransfer` |
| Cash | เงินสด | SharedCopy.kt `methodCash` |
| Other app | แอปอื่น | SharedCopy.kt `methodOtherApp` |
| Equally | หารเท่ากัน | SharedCopy.kt `modeEqually` |
| Exact | ระบุจำนวน | SharedCopy.kt `modeExact` |
| Percent | เปอร์เซ็นต์ | SharedCopy.kt `modePercent` |
| Shares | ตามสัดส่วน | SharedCopy.kt `modeShares` |
| {n} changes waiting to sync | มีการเปลี่ยนแปลง {n} รายการรอซิงก์ | SharedCopy.kt `changesWaiting` |
| Offline · {n} changes waiting | ออฟไลน์ · รอส่ง {n} รายการ | SharedCopy.kt `offlineWaiting` |

## The Shared tab: balances, rows and the + sheets

| English | Thai | Where |
|---|---|---|
| Overall, you are owed | รวมทั้งหมด คุณจะได้รับ | SharedHomeCopy.kt `overallOwed` |
| Overall, you owe | รวมทั้งหมด คุณต้องจ่าย | SharedHomeCopy.kt `overallOwe` |
| All settled up | เคลียร์ครบแล้ว | SharedHomeCopy.kt `allSettledUp` |
| {n} changes were not accepted | การเปลี่ยนแปลง {n} รายการไม่ได้รับการยอมรับ | SharedHomeCopy.kt `refused` |
| You & {name} | คุณและ {name} | SharedHomeCopy.kt `youAnd` |
| {n} people | {n} คน | SharedHomeCopy.kt `peopleCount` |
| {name} added {n} | {name} เพิ่ม {n} รายการ | SharedHomeCopy.kt `addedSince` |
| {n} new | ใหม่ {n} รายการ | SharedHomeCopy.kt `newSince` |
| {name} repeats {15th} | {name} ทุก{วันที่ 15} ของเดือน | SharedHomeCopy.kt `repeatsMonthly` |
| {name} repeats weekly | {name} ทุกสัปดาห์ | SharedHomeCopy.kt `repeatsWeekly` |
| {name} repeats every 2 weeks | {name} ทุก 2 สัปดาห์ | SharedHomeCopy.kt `repeatsTwoWeeks` |
| {name} repeats yearly | {name} ทุกปี | SharedHomeCopy.kt `repeatsYearly` |
| Search groups and people | ค้นหากลุ่มและคน | SharedHomeCopy.kt `search` |
| New group or person | กลุ่มใหม่หรือคนใหม่ | SharedHomeCopy.kt `newGroupOrPerson` |
| You are owed | คุณจะได้รับ | SharedHomeCopy.kt `totalOwed` |
| You owe | คุณต้องจ่าย | SharedHomeCopy.kt `totalOwe` |
| Nothing shared yet. | ยังไม่มีอะไรที่แชร์ | SharedHomeCopy.kt `emptyTitle` |
| Add a person to split one bill, or make a group for a trip or a flat. They join from a link, and your own spending stays on this phone. | เพิ่มคนเพื่อหารบิลเดียว หรือสร้างกลุ่มสำหรับทริปหรือห้องเช่า เขาเข้าร่วมจากลิงก์ และรายจ่ายของคุณเองยังอยู่ในโทรศัพท์เครื่องนี้ | SharedHomeCopy.kt `emptyBody` |
| I have an invite code | ฉันมีโค้ดเชิญ | SharedHomeCopy.kt `haveInviteCode` |
| Add | เพิ่ม | SharedHomeCopy.kt `add` |
| New person | คนใหม่ | SharedHomeCopy.kt `newPerson` |
| Join with a code | เข้าร่วมด้วยโค้ด | SharedHomeCopy.kt `joinWithCode` |
| Invite code | โค้ดเชิญ | SharedHomeCopy.kt `inviteCode` |
| Or paste the whole link. | หรือวางลิงก์ทั้งหมด | SharedHomeCopy.kt `orPasteLink` |
| Join | เข้าร่วม | SharedHomeCopy.kt `join` |
| Add a name | เพิ่มชื่อ | SharedHomeCopy.kt `addAName` |
| Create | สร้าง | SharedHomeCopy.kt `create` |
| Their name | ชื่อของเขา | SharedHomeCopy.kt `theirName` |
| They join from a link and see only the bills you share with them. | เขาเข้าร่วมจากลิงก์ และเห็นเฉพาะบิลที่คุณแชร์กับเขา | SharedHomeCopy.kt `newPersonNote` |

## People

| English | Thai | Where |
|---|---|---|
| Search people | ค้นหาคน | SharedHomeCopy.kt `SharedPersonCopy.search` |
| Bring {name} back, and you can split new costs with them again. | นำ {name} กลับมา แล้วคุณจะหารค่าใช้จ่ายใหม่กับเขาได้อีก | SharedHomeCopy.kt `bringBackBody` |
| {name} keeps every past split and balance, but can't be added to new ones. You can bring them back. | {name} ยังมีการหารและยอดคงค้างที่ผ่านมาครบ แต่จะเพิ่มในรายการใหม่ไม่ได้ คุณนำกลับมาได้ | SharedHomeCopy.kt `archiveBody` |
| Bring back | นำกลับมา | SharedHomeCopy.kt `bringBack` |
| Archive {name} | เก็บ {name} เข้าคลัง | SharedHomeCopy.kt `archive` |
| Archived · past splits keep the name | เก็บเข้าคลังแล้ว · การหารที่ผ่านมายังใช้ชื่อเดิม | SharedHomeCopy.kt `archived` |
| In no group yet | ยังไม่อยู่ในกลุ่มใด | SharedHomeCopy.kt `inNoGroup` |

## Joining from an invite

| English | Thai | Where |
|---|---|---|
| Join a group | เข้าร่วมกลุ่ม | SharedHomeCopy.kt `SharedJoinCopy.title` |
| Sharing is not set up in this copy of PaiSen, so this invite cannot open here. | PaiSeN ชุดนี้ไม่ได้เปิดการแชร์ไว้ จึงเปิดคำเชิญนี้ที่นี่ไม่ได้ | SharedHomeCopy.kt `unavailable` |
| Sign in to see the group and pick your name. Your own spending stays on this phone. | ลงชื่อเข้าใช้เพื่อดูกลุ่มและเลือกชื่อของคุณ รายจ่ายของคุณเองยังอยู่ในโทรศัพท์เครื่องนี้ | SharedHomeCopy.kt `signInFirst` |
| Opening the invite… | กำลังเปิดคำเชิญ… | SharedHomeCopy.kt `opening` |
| Which one are you? | คุณคือคนไหน | SharedHomeCopy.kt `whichOne` |
| {group} already splits costs with these names. Pick yours, and what is already split to it becomes yours. | {group} หารค่าใช้จ่ายกับชื่อเหล่านี้อยู่แล้ว เลือกชื่อของคุณ แล้วสิ่งที่หารให้ชื่อนั้นจะเป็นของคุณ | SharedHomeCopy.kt `pickYours` |
| I'm not on the list | ไม่มีชื่อฉันในรายการ | SharedHomeCopy.kt `notOnList` |
| You're already in {group} | คุณอยู่ใน {group} แล้ว | SharedHomeCopy.kt `alreadyIn` |
| You joined as {name}, so what is split to that name is already yours. | คุณเข้าร่วมในชื่อ {name} สิ่งที่หารให้ชื่อนั้นจึงเป็นของคุณแล้ว | SharedHomeCopy.kt `joinedAs` |
| Open group | เปิดกลุ่ม | SharedHomeCopy.kt `openGroup` |
| So you see the group's expenses and they see yours. | เพื่อให้คุณเห็นค่าใช้จ่ายของกลุ่ม และคนอื่นเห็นของคุณ | SharedHomeCopy.kt `signInReason` |
| Join as {name}? | เข้าร่วมในชื่อ {name} ไหม | SharedHomeCopy.kt `joinAsAsk` |
| What is already split to {name} becomes yours. Pick only your own name. | สิ่งที่หารให้ {name} อยู่แล้วจะเป็นของคุณ เลือกเฉพาะชื่อของคุณเอง | SharedHomeCopy.kt `joinAsBody` |
| Join as {name} | เข้าร่วมในชื่อ {name} | SharedHomeCopy.kt `joinAs` |
| Pick again | เลือกใหม่ | SharedHomeCopy.kt `pickAgain` |

## Sign-in, invites and the account

| English | Thai | Where |
|---|---|---|
| Sign in to share | ลงชื่อเข้าใช้เพื่อแชร์ | SharedAccountCopy.kt `SharedSignInCopy.title` |
| What leaves this phone | อะไรบ้างที่ออกจากโทรศัพท์เครื่องนี้ | SharedAccountCopy.kt `leavesTitle` |
| The group's name, the members' names, the shared expenses and payments with who added them, a PromptPay ID if you add one, and a push token for this phone, so it can be told when someone changes a group. They are kept with Google Firebase in {region}. Your own spending, slips and reports never leave this phone. Delete your account in Settings to remove what is stored. | ชื่อกลุ่ม ชื่อสมาชิก ค่าใช้จ่ายและการจ่ายเงินที่แชร์พร้อมชื่อคนที่เพิ่ม PromptPay ID ถ้าคุณใส่ไว้ และโทเค็นการแจ้งเตือนของโทรศัพท์เครื่องนี้ เพื่อแจ้งเมื่อมีคนเปลี่ยนแปลงกลุ่ม ข้อมูลเหล่านี้เก็บไว้กับ Google Firebase ใน {region} รายจ่ายของคุณเอง สลิป และรายงานไม่ออกจากโทรศัพท์เครื่องนี้ ลบบัญชีในตั้งค่าเพื่อลบสิ่งที่เก็บไว้ | SharedAccountCopy.kt `leavesBody` |
| Signing in… | กำลังลงชื่อเข้าใช้… | SharedAccountCopy.kt `signingIn` |
| Continue with Google | ดำเนินการต่อด้วย Google | SharedAccountCopy.kt `google` |
| Sign-in did not finish. Try again. | ลงชื่อเข้าใช้ไม่สำเร็จ ลองอีกครั้ง | SharedAccountCopy.kt `didNotFinish` |
| Make a link so people can join {group}. The group's name, its members and its expenses then sync through Google Firebase in {region}. Your own spending stays on this phone. | สร้างลิงก์เพื่อให้คนเข้าร่วม {group} จากนั้นชื่อกลุ่ม สมาชิก และค่าใช้จ่ายจะซิงก์ผ่าน Google Firebase ใน {region} รายจ่ายของคุณเองยังอยู่ในโทรศัพท์เครื่องนี้ | SharedAccountCopy.kt `makeLinkBody` |
| Make an invite link | สร้างลิงก์เชิญ | SharedAccountCopy.kt `makeLink` |
| Not now | ไว้ทีหลัง | SharedAccountCopy.kt `notNow` |
| Making a link… | กำลังสร้างลิงก์… | SharedAccountCopy.kt `making` |
| This link is switched off. Nobody new can join with it. | ลิงก์นี้ปิดแล้ว ไม่มีใครเข้าร่วมใหม่ด้วยลิงก์นี้ได้ | SharedAccountCopy.kt `switchedOff` |
| Make a new link | สร้างลิงก์ใหม่ | SharedAccountCopy.kt `newLink` |
| Each person opens the link, signs in and picks their own name in {group}. | แต่ละคนเปิดลิงก์ ลงชื่อเข้าใช้ และเลือกชื่อของตัวเองใน {group} | SharedAccountCopy.kt `howToJoin` |
| QR code for the invite link | QR โค้ดสำหรับลิงก์เชิญ | SharedAccountCopy.kt `qr` |
| Copy | คัดลอก | SharedAccountCopy.kt `copyLink` |
| Share link | แชร์ลิงก์ | SharedAccountCopy.kt `shareLink` |
| Join {group} on PaiSen: {link} (sent to LINE) | เข้าร่วม {group} บน PaiSeN: {link} | SharedAccountCopy.kt `message` |
| Switch this link off | ปิดลิงก์นี้ | SharedAccountCopy.kt `switchOff` |
| Shared groups account | บัญชีสำหรับกลุ่ม | SharedAccountCopy.kt `SharedAccountCopy.title` |
| Signed in as {who} | ลงชื่อเข้าใช้ในชื่อ {who} | SharedAccountCopy.kt `signedInAs` |
| Sign out | ออกจากระบบ | SharedAccountCopy.kt `signOut` |
| Delete account | ลบบัญชี | SharedAccountCopy.kt `deleteAccount` |
| Not signed in. You sign in when you share a group or open an invite. | ยังไม่ได้ลงชื่อเข้าใช้ คุณจะลงชื่อเข้าใช้เมื่อแชร์กลุ่มหรือเปิดคำเชิญ | SharedAccountCopy.kt `notSignedIn` |
| Sign in once more to delete your account. It keeps someone else from deleting it. | ลงชื่อเข้าใช้อีกครั้งเพื่อลบบัญชี เพื่อไม่ให้คนอื่นลบบัญชีของคุณได้ | SharedAccountCopy.kt `freshSignIn` |
| So the people in your groups see the same expenses in their own app. | เพื่อให้คนในกลุ่มของคุณเห็นค่าใช้จ่ายเดียวกันในแอปของเขา | SharedAccountCopy.kt `signInReason` |
| Delete your account? | ลบบัญชีของคุณไหม | SharedAccountCopy.kt `deleteAsk` |
| You leave every shared group, and your name in them is free for someone else to pick. What you added stays in each group's history. Shared groups leave this phone; your own spending stays. | คุณจะออกจากทุกกลุ่ม และชื่อของคุณในกลุ่มจะว่างให้คนอื่นเลือก สิ่งที่คุณเพิ่มไว้ยังอยู่ในประวัติของแต่ละกลุ่ม กลุ่มจะหายไปจากโทรศัพท์เครื่องนี้ แต่รายจ่ายของคุณเองยังอยู่ | SharedAccountCopy.kt `deleteBody` |
| {n} changes have not reached your groups yet. Connect to the internet, then try again. | การเปลี่ยนแปลง {n} รายการยังไปไม่ถึงกลุ่มของคุณ เชื่อมต่ออินเทอร์เน็ต แล้วลองอีกครั้ง | SharedAccountCopy.kt `notSentYet` |
| Sign out? | ออกจากระบบไหม | SharedAccountCopy.kt `signOutAsk` |
| Your shared groups leave this phone and come back when you sign in again. Your own spending stays. | กลุ่มของคุณจะหายไปจากโทรศัพท์เครื่องนี้ และกลับมาเมื่อคุณลงชื่อเข้าใช้อีกครั้ง รายจ่ายของคุณเองยังอยู่ | SharedAccountCopy.kt `signOutBody` |
| Signed out on this phone | ออกจากระบบในโทรศัพท์เครื่องนี้แล้ว | SharedAccountCopy.kt `endedTitle` |
| Your account signed in on another phone. Sign in again in Settings to bring your shared groups back here. Your own spending is still on this phone. | บัญชีของคุณลงชื่อเข้าใช้ในโทรศัพท์อีกเครื่อง ลงชื่อเข้าใช้อีกครั้งในตั้งค่าเพื่อนำกลุ่มกลับมาที่นี่ รายจ่ายของคุณเองยังอยู่ในโทรศัพท์เครื่องนี้ | SharedAccountCopy.kt `endedBody` |

## Sync messages

| English | Thai | Where |
|---|---|---|
| No connection. Try again when you're online. | ไม่มีการเชื่อมต่อ ลองอีกครั้งเมื่อออนไลน์ | SharedSyncCopy.kt `offline` |
| The group didn't accept this. | กลุ่มไม่ยอมรับการเปลี่ยนแปลงนี้ | SharedSyncCopy.kt `refused` |
| Sign in again to keep sharing. | ลงชื่อเข้าใช้อีกครั้งเพื่อแชร์ต่อ | SharedSyncCopy.kt `sessionEnded` |
| This invite has expired. Ask for a new one. | คำเชิญนี้หมดอายุแล้ว ขอคำเชิญใหม่ | SharedSyncCopy.kt `inviteExpired` |
| Sign in to share groups | ลงชื่อเข้าใช้เพื่อแชร์กลุ่ม | SharedSyncCopy.kt `signInToShare` |
| A group | กลุ่มหนึ่ง | SharedSyncCopy.kt `aGroup` |
| No group has this code. Check it, or ask for the link again. | ไม่มีกลุ่มที่ใช้โค้ดนี้ ตรวจสอบโค้ด หรือขอลิงก์อีกครั้ง | SharedSyncCopy.kt `noSuchCode` |
| Someone just picked this name. Choose another one. | มีคนเพิ่งเลือกชื่อนี้ไป เลือกชื่ออื่น | SharedSyncCopy.kt `nameTaken` |
| Signed out. Sign in to keep this group in sync. | ออกจากระบบแล้ว ลงชื่อเข้าใช้เพื่อซิงก์กลุ่มนี้ต่อ | SharedSyncCopy.kt `signedOutGroup` |
| Signed out · {n} changes not sent | ออกจากระบบแล้ว · ยังไม่ได้ส่ง {n} รายการ | SharedSyncCopy.kt `signedOutGroup` |
| Signed out. Sign in to keep your shared groups in sync. | ออกจากระบบแล้ว ลงชื่อเข้าใช้เพื่อซิงก์กลุ่มของคุณต่อ | SharedSyncCopy.kt `signedOutHome` |
| Sign in with the same account, and your shared groups sync again. | ลงชื่อเข้าใช้ด้วยบัญชีเดิม แล้วกลุ่มของคุณจะซิงก์อีกครั้ง | SharedSyncCopy.kt `signInAgainReason` |

## Push notices (other members' lock screens)

| English | Thai | Where |
|---|---|---|
| an expense | ค่าใช้จ่ายรายการหนึ่ง | SharedPushCopy.kt `anExpense` |
| {actor} added {what} · {amount} | {actor} เพิ่ม {what} · {amount} | SharedPushCopy.kt `added` |
| {actor} changed {what} | {actor} แก้ไข {what} | SharedPushCopy.kt `edited` |
| {actor} removed {what} | {actor} นำ {what} ออก | SharedPushCopy.kt `removed` |
| {actor} put back {what} | {actor} นำ {what} กลับมา | SharedPushCopy.kt `putBack` |
| {what} was added · {amount} | เพิ่ม {what} อัตโนมัติแล้ว · {amount} | SharedPushCopy.kt `repeatAdded` |
| {from} paid {to} {amount} | {from} จ่ายให้ {to} {amount} | SharedPushCopy.kt `paid` |
| {actor} removed a payment | {actor} นำการจ่ายเงินรายการหนึ่งออก | SharedPushCopy.kt `paymentRemoved` |
| {actor} put back a payment | {actor} นำการจ่ายเงินกลับมา | SharedPushCopy.kt `paymentPutBack` |
| {name} joined | {name} เข้าร่วมกลุ่มแล้ว | SharedPushCopy.kt `joined` |
| {name} left | {name} ออกจากกลุ่มแล้ว | SharedPushCopy.kt `left` |
| {actor} added {name} | {actor} เพิ่ม {name} | SharedPushCopy.kt `addedPerson` |
| {actor} removed {name} | {actor} ลบ {name} ออก | SharedPushCopy.kt `removedPerson` |
| {actor} changed a name | {actor} เปลี่ยนชื่อหนึ่งชื่อ | SharedPushCopy.kt `changedName` |
| {actor} renamed the group to {name} | {actor} เปลี่ยนชื่อกลุ่มเป็น {name} | SharedPushCopy.kt `renamed` |
| {actor} deleted the group | {actor} ลบกลุ่มนี้ | SharedPushCopy.kt `deleted` |
| {actor} set up a repeat: {what} | {actor} ตั้งรายการประจำ: {what} | SharedPushCopy.kt `repeatSetUp` |
| {actor} changed the repeat {what} | {actor} แก้ไขรายการประจำ {what} | SharedPushCopy.kt `repeatChanged` |
| {actor} stopped the repeat {what} | {actor} หยุดรายการประจำ {what} | SharedPushCopy.kt `repeatStopped` |

## One group: hero, feed, menu, PromptPay and leave or delete

| English | Thai | Where |
|---|---|---|
| It is no longer on this phone, so there is nothing to show here. | กลุ่มนี้ไม่อยู่ในโทรศัพท์เครื่องนี้แล้ว จึงไม่มีอะไรให้แสดง | SharedGroupCopy.kt `SharedDetailCopy.goneBody` |
| Nothing here to settle | ไม่มีอะไรให้เคลียร์ | SharedGroupCopy.kt `goneStatus` |
| More for this group | เพิ่มเติมสำหรับกลุ่มนี้ | SharedGroupCopy.kt `more` |
| New (spoken for a new row) | ใหม่ | SharedGroupCopy.kt `newRow` |
| {name}, not joined yet | {name} ยังไม่ได้เข้าร่วม | SharedGroupCopy.kt `notJoined` |
| Settle up | เคลียร์ยอด | SharedGroupCopy.kt `settleUp` |
| Balances | ยอดคงค้าง | SharedGroupCopy.kt `balances` |
| Repeats | รายการประจำ | SharedGroupCopy.kt `repeats` |
| Expenses | ค่าใช้จ่าย | SharedGroupCopy.kt `expenses` |
| No expenses yet. Add the first one with the + button. | ยังไม่มีค่าใช้จ่าย เพิ่มรายการแรกด้วยปุ่ม + | SharedGroupCopy.kt `noExpenses` |
| Edit group | แก้ไขกลุ่ม | SharedGroupCopy.kt `editGroup` |
| My PromptPay | PromptPay ของฉัน | SharedGroupCopy.kt `myPromptPay` |
| Repeat an expense | ตั้งรายการประจำ | SharedGroupCopy.kt `repeatAnExpense` |
| Delete group | ลบกลุ่ม | SharedGroupCopy.kt `deleteGroup` |
| Delete for everyone | ลบสำหรับทุกคน | SharedGroupCopy.kt `deleteForEveryone` |
| Leave group | ออกจากกลุ่ม | SharedGroupCopy.kt `leaveGroup` |
| So the people in {group} see the same expenses in their own app. | เพื่อให้คนใน {group} เห็นค่าใช้จ่ายเดียวกันในแอปของเขา | SharedGroupCopy.kt `signInReason` |
| Add the phone number or ID your PromptPay uses. People in this group see a QR for it when they pay you. | ใส่เบอร์โทรหรือเลขประจำตัวที่ PromptPay ของคุณใช้ คนในกลุ่มนี้จะเห็น QR ของคุณเมื่อจ่ายเงินให้คุณ | SharedGroupCopy.kt `promptPayAdd` |
| Now {current}. Type a new one, or leave it empty to take it off. | ตอนนี้คือ {current} พิมพ์ใหม่ หรือเว้นว่างเพื่อลบออก | SharedGroupCopy.kt `promptPayNow` |
| PromptPay phone number or ID | เบอร์โทรหรือเลขประจำตัว PromptPay | SharedGroupCopy.kt `promptPayField` |
| Use a 10-digit phone number, or a 13- or 15-digit ID. | ใช้เบอร์โทร 10 หลัก หรือเลขประจำตัว 13 หรือ 15 หลัก | SharedGroupCopy.kt `promptPayWrong` |
| Take it off | ลบออก | SharedGroupCopy.kt `takeItOff` |
| Leave {name}? | ออกจาก {name} ไหม | SharedGroupCopy.kt `leaveAsk` |
| Delete {name}? | ลบ {name} ไหม | SharedGroupCopy.kt `deleteAsk` |
| Its expenses, payments and repeats go from this phone. Your own transactions stay in History. | ค่าใช้จ่าย การจ่ายเงิน และรายการประจำของกลุ่มจะหายไปจากโทรศัพท์เครื่องนี้ รายการของคุณเองยังอยู่ในประวัติ | SharedGroupCopy.kt `deleteHereBody` |
| It goes from everyone's phone, with its expenses, payments and repeats, and the invite link stops working. Your own transactions stay in History. | กลุ่มจะหายไปจากโทรศัพท์ของทุกคน พร้อมค่าใช้จ่าย การจ่ายเงิน และรายการประจำ และลิงก์เชิญจะใช้ไม่ได้อีก รายการของคุณเองยังอยู่ในประวัติ | SharedGroupCopy.kt `deleteForEveryoneBody` |
| It goes from your phone, and your name is free for someone else. The others keep the group and everything in it. Your own transactions stay in History. | กลุ่มจะหายไปจากโทรศัพท์ของคุณ และชื่อของคุณจะว่างให้คนอื่น คนอื่นยังมีกลุ่มและทุกอย่างในกลุ่ม รายการของคุณเองยังอยู่ในประวัติ | SharedGroupCopy.kt `leaveBody` |
| Some balances are not settled up. | ยังมียอดที่ยังไม่เคลียร์ | SharedGroupCopy.kt `notSettled` |
| Leaving… | กำลังออก… | SharedGroupCopy.kt `leaving` |
| Deleting… | กำลังลบ… | SharedGroupCopy.kt `deleting` |
| Leave | ออก | SharedGroupCopy.kt `leave` |
| Add an expense | เพิ่มค่าใช้จ่าย | SharedGroupCopy.kt `addAnExpense` |
| {n} changes were not accepted by the group | กลุ่มไม่ยอมรับการเปลี่ยนแปลง {n} รายการ | SharedGroupCopy.kt `refused` |
| Up to date | เป็นปัจจุบันแล้ว | SharedGroupCopy.kt `upToDate` |
| {from} paid {to} | {from} จ่ายให้ {to} | SharedGroupCopy.kt `paid` |
| {method} · settlement | {method} · เคลียร์ยอด | SharedGroupCopy.kt `settlement` |
| In this group you are owed | ในกลุ่มนี้ คุณจะได้รับ | SharedGroupCopy.kt `owedHere` |
| In this group you owe | ในกลุ่มนี้ คุณต้องจ่าย | SharedGroupCopy.kt `oweHere` |
| In this group you are settled up | ในกลุ่มนี้ คุณเคลียร์แล้ว | SharedGroupCopy.kt `settledHere` |
| Total trip spend / Total spend {amount} · shown in THB | ค่าใช้จ่ายทั้งทริป / ค่าใช้จ่ายทั้งหมด {amount} · แสดงเป็น THB | SharedGroupCopy.kt `spendLine` |
| {payer} paid {amount} | {payer} จ่าย {amount} | SharedGroupCopy.kt `payerPaid` |
| not involved | ไม่เกี่ยวข้อง | SharedGroupCopy.kt `notInvolved` |
| you lent | คุณออกให้ | SharedGroupCopy.kt `youLent` |
| your share | ส่วนของคุณ | SharedGroupCopy.kt `yourShare` |
| {name} hasn't joined yet | {name} ยังไม่ได้เข้าร่วม | SharedGroupCopy.kt `hasNotJoined` |
| {a} and {b} haven't joined yet | {a} และ {b} ยังไม่ได้เข้าร่วม | SharedGroupCopy.kt `twoHaveNotJoined` |
| {n} people haven't joined yet | อีก {n} คนยังไม่ได้เข้าร่วม | SharedGroupCopy.kt `manyHaveNotJoined` |
| Add people | เพิ่มคน | SharedGroupCopy.kt `addPeople` |
| New names join from the invite link and pick their own name. | ชื่อใหม่เข้าร่วมจากลิงก์เชิญ และเลือกชื่อของตัวเอง | SharedGroupCopy.kt `addPeopleNote` |
| Remove someone | นำคนออก | SharedGroupCopy.kt `removeSomeone` |
| Remove {name} from this group? | นำ {name} ออกจากกลุ่มนี้ไหม | SharedGroupCopy.kt `removeAsk` |
| {name} keeps every past split and balance, but can't be added to new expenses. | {name} ยังมีการหารและยอดคงค้างที่ผ่านมาครบ แต่จะเพิ่มในค่าใช้จ่ายใหม่ไม่ได้ | SharedGroupCopy.kt `removeBody` |
| {name} still has a balance here. It stays until it is settled. | {name} ยังมียอดคงค้างในกลุ่มนี้ ยอดจะอยู่จนกว่าจะเคลียร์ | SharedGroupCopy.kt `removeUnsettled` |
| {n} changes have not been sent yet. If you leave now, the others never see them. | การเปลี่ยนแปลง {n} รายการยังไม่ได้ส่ง ถ้าออกตอนนี้ คนอื่นจะไม่เห็นเลย | SharedGroupCopy.kt `notSent` |
| Leave anyway | ออกเลย | SharedGroupCopy.kt `leaveAnyway` |

## Who pays whom and the Pay me card

| English | Thai | Where |
|---|---|---|
| To settle {group} | เพื่อเคลียร์ยอด {group} | SharedGroupCopy.kt `SharedSettleCopy.toSettle` |
| {n} between everyone | {n} รายการระหว่างทุกคน | SharedGroupCopy.kt `between` |
| then | แล้ว | SharedGroupCopy.kt `then` |
| {n} after simplifying | {n} รายการหลังลดขั้นตอน | SharedGroupCopy.kt `afterSimplifying` |
| Simplify debts | ลดจำนวนการโอน | SharedGroupCopy.kt `simplify` |
| Settle a payment | บันทึกการเคลียร์ยอด | SharedGroupCopy.kt `settleAPayment` |
| {from} pays you | {from} จ่ายให้คุณ | SharedGroupCopy.kt `paysYou` |
| You pay {to} | คุณจ่ายให้ {to} | SharedGroupCopy.kt `youPay` |
| {from} pays {to} | {from} จ่ายให้ {to} | SharedGroupCopy.kt `pays` |
| All settled | เคลียร์ครบแล้ว | SharedGroupCopy.kt `allSettled` |
| {n} payments | โอน {n} ครั้ง | SharedGroupCopy.kt `payments` |
| Ask {name} to pay | ขอให้ {name} จ่าย | SharedGroupCopy.kt `askToPay` |
| For {group} | สำหรับ {group} | SharedGroupCopy.kt `payMeFor` |
| {payer} pays | {payer} จ่าย | SharedGroupCopy.kt `payMeFrom` |
| Scan with any Thai bank app · PromptPay {id} | สแกนด้วยแอปธนาคารไทยใดก็ได้ · PromptPay {id} | SharedGroupCopy.kt `payMeScan` |
| {payer}, your part in {group} is {amount}. Scan the QR in the picture with your bank app to pay me. | {payer} ส่วนของคุณใน {group} คือ {amount} สแกน QR ในรูปด้วยแอปธนาคารเพื่อจ่ายให้ฉัน | SharedGroupCopy.kt `payMeText` |
| Send the card | ส่งการ์ด | SharedGroupCopy.kt `sendCard` |
| Amounts in {codes} were converted to THB at the rate saved on each expense, so nobody's total moves with the market. | ยอดใน {codes} แปลงเป็น THB ด้วยอัตราที่บันทึกไว้ในแต่ละค่าใช้จ่าย ยอดของทุกคนจึงไม่เปลี่ยนตามตลาด | SharedGroupCopy.kt `converted` |

## A group's activity

| English | Thai | Where |
|---|---|---|
| {group} · activity | {group} · กิจกรรม | SharedGroupCopy.kt `SharedLogCopy.groupTitle` |
| All | ทั้งหมด | SharedGroupCopy.kt `filterAll` |
| Expenses | ค่าใช้จ่าย | SharedGroupCopy.kt `filterExpenses` |
| Payments | การจ่ายเงิน | SharedGroupCopy.kt `filterPayments` |
| Edits | การแก้ไข | SharedGroupCopy.kt `filterEdits` |
| Nothing here yet. Expenses, payments and edits show up as they happen. | ยังไม่มีอะไร ค่าใช้จ่าย การจ่ายเงิน และการแก้ไขจะแสดงเมื่อเกิดขึ้น | SharedGroupCopy.kt `empty` |
| changed to | เปลี่ยนเป็น | SharedGroupCopy.kt `changedTo` |
| {what} added automatically in {group} | เพิ่ม {what} อัตโนมัติใน {group} | SharedGroupCopy.kt `addedAutomatically` |
| {author} added {what} in {group} | {author} เพิ่ม {what} ใน {group} | SharedGroupCopy.kt `added` |
| {author} edited {what} | {author} แก้ไข {what} | SharedGroupCopy.kt `edited` |
| {author} removed {what} | {author} นำ {what} ออก | SharedGroupCopy.kt `removed` |
| Split changed because the amount changed · {time} | การหารเปลี่ยนเพราะจำนวนเงินเปลี่ยน · {time} | SharedGroupCopy.kt `splitFollowed` |
| Changed in History · {time} | แก้ไขในประวัติ · {time} | SharedGroupCopy.kt `changedInHistory` |
| Details changed · {time} | รายละเอียดเปลี่ยน · {time} | SharedGroupCopy.kt `detailsChanged` |
| It was: {what} | เดิมคือ: {what} | SharedGroupCopy.kt `itWas` |
| {from} paid {to} {amount} {how} | {from} จ่ายให้ {to} {amount} {how} | SharedGroupCopy.kt `paid` |
| Recorded by {author} · {time} | บันทึกโดย {author} · {time} | SharedGroupCopy.kt `recordedBy` |
| {author} removed a payment | {author} นำการจ่ายเงินรายการหนึ่งออก | SharedGroupCopy.kt `paymentRemoved` |
| {author} put back a payment | {author} นำการจ่ายเงินกลับมา | SharedGroupCopy.kt `paymentPutBack` |
| {from} paid {to} {amount} · {time} | {from} จ่ายให้ {to} {amount} · {time} | SharedGroupCopy.kt `whichPayment` |
| You owe {payer} {amount} | คุณต้องจ่าย {payer} {amount} | SharedGroupCopy.kt `youOwePayer` |
| You lent {amount} | คุณออกให้ {amount} | SharedGroupCopy.kt `youLent` |
| Not involved | ไม่เกี่ยวข้อง | SharedGroupCopy.kt `SharedLogCopy.notInvolved` |
| by bank transfer | ผ่านการโอนธนาคาร | SharedGroupCopy.kt `byBankTransfer` |
| in cash | เป็นเงินสด | SharedGroupCopy.kt `inCash` |
| by PromptPay | ผ่าน PromptPay | SharedGroupCopy.kt `byPromptPay` |
| in another app | ผ่านแอปอื่น | SharedGroupCopy.kt `inAnotherApp` |

## Add or edit a shared expense

| English | Thai | Where |
|---|---|---|
| Add expense | เพิ่มค่าใช้จ่าย | SharedExpenseCopy.kt `addExpense` |
| Edit expense | แก้ไขค่าใช้จ่าย | SharedExpenseCopy.kt `editExpense` |
| There is nothing to add an expense to. | ไม่มีกลุ่มให้เพิ่มค่าใช้จ่าย | SharedExpenseCopy.kt `goneBody` |
| Nothing here to save | ไม่มีอะไรให้บันทึก | SharedExpenseCopy.kt `goneStatus` |
| Remove this expense | นำค่าใช้จ่ายนี้ออก | SharedExpenseCopy.kt `removeThis` |
| What was it for / What was it for? | ค่าอะไร | SharedExpenseCopy.kt `whatFor`, `whatForHint` |
| Pick a category | เลือกหมวดหมู่ | SharedExpenseCopy.kt `pickCategory` |
| Add a note | เพิ่มโน้ต | SharedExpenseCopy.kt `addNote` |
| Save expense | บันทึกค่าใช้จ่าย | SharedExpenseCopy.kt `saveExpense` |
| Currency | สกุลเงิน | SharedExpenseCopy.kt `currency` |
| Remove this expense? | นำค่าใช้จ่ายนี้ออกไหม | SharedExpenseCopy.kt `removeAsk` |
| Balances drop it and Activity keeps a line saying you removed it. Your own transaction stays in History. | ยอดคงค้างจะไม่นับรายการนี้ และกิจกรรมจะมีบรรทัดบอกว่าคุณนำออก รายการของคุณเองยังอยู่ในประวัติ | SharedExpenseCopy.kt `removeBody` |
| Remove | นำออก | SharedExpenseCopy.kt `remove` |
| Split | การหาร | SharedExpenseCopy.kt `split` |
| Percent checks against 100%. Shares need no total: 2 / 1 / 1 / 1 works out the amounts. | เปอร์เซ็นต์ต้องรวมได้ 100% ตามสัดส่วนไม่ต้องมียอดรวม: 2 / 1 / 1 / 1 จะคำนวณจำนวนเงินให้ | SharedExpenseCopy.kt `splitHelp` |
| {name}'s {mode} | {mode} ของ {name} | SharedExpenseCopy.kt `weightLabel` |
| Expense updated | อัปเดตค่าใช้จ่ายแล้ว | SharedExpenseCopy.kt `updated` |
| Added {what} | เพิ่ม {what} แล้ว | SharedExpenseCopy.kt `added` |
| Removed {what} / Removed the expense | นำ {what} ออกแล้ว / นำ ค่าใช้จ่าย ออกแล้ว | SharedExpenseCopy.kt `removed` |
| {group} · {n} people | {group} · {n} คน | SharedExpenseCopy.kt `groupLine` |
| Type the baht you got for {unit} {symbol} | ใส่จำนวนบาทที่ได้สำหรับ {unit} {symbol} | SharedExpenseCopy.kt `typeBaht` |
| ≈ {baht} at ฿{rate} per {per}, saved with the expense | ≈ {baht} ที่อัตรา ฿{rate} ต่อ {per} บันทึกพร้อมค่าใช้จ่าย | SharedExpenseCopy.kt `bahtAtRate` |
| ฿ per {unit} {code} | ฿ ต่อ {unit} {code} | SharedExpenseCopy.kt `ratePer` |
| {mode} · {n} people | {mode} · {n} คน | SharedExpenseCopy.kt `splitLabel` |
| From your History. Change the amount or date there. | มาจากประวัติของคุณ แก้จำนวนเงินหรือวันที่ได้ที่นั่น | SharedExpenseCopy.kt `locked` |
| Someone changed this expense on another phone. Save again to keep yours. | มีคนแก้ไขค่าใช้จ่ายนี้ในโทรศัพท์อีกเครื่อง บันทึกอีกครั้งเพื่อใช้ของคุณ | SharedExpenseCopy.kt `changedElsewhere` |
| Someone removed this expense. Save again to bring it back with your changes. | มีคนนำค่าใช้จ่ายนี้ออก บันทึกอีกครั้งเพื่อนำกลับมาพร้อมการแก้ไขของคุณ | SharedExpenseCopy.kt `removedElsewhere` |
| This expense was removed. Save to bring it back. | ค่าใช้จ่ายนี้ถูกนำออกแล้ว บันทึกเพื่อนำกลับมา | SharedExpenseCopy.kt `openedRemoved` |
| {amount} each | คนละ {amount} | SharedExpenseCopy.kt `each` |
| you are owed {amount} | คุณจะได้รับ {amount} | SharedExpenseCopy.kt `owedAmount` |
| you owe {name} {amount} | คุณต้องจ่าย {name} {amount} | SharedExpenseCopy.kt `oweTo` |
| you are not in this one | คุณไม่ได้อยู่ในรายการนี้ | SharedExpenseCopy.kt `notInThis` |
| How to split {amount} | หาร {amount} อย่างไร | SharedExpenseCopy.kt `splitTitle` |
| {left} left to assign | เหลือให้แบ่งอีก {left} | SharedExpenseCopy.kt `leftToAssign` |
| {over} too much | เกิน {over} | SharedExpenseCopy.kt `tooMuch` |
| {assigned} of {total} is split so far | แบ่งแล้ว {assigned} จาก {total} | SharedExpenseCopy.kt `splitSoFar` |
| Give to {NAME} | ให้ {name} | SharedExpenseCopy.kt `giveTo` |
| Type an amount to save | ใส่จำนวนเงินเพื่อบันทึก | SharedExpenseCopy.kt `hintAmount` |
| Type the baht rate to save | ใส่อัตราเงินบาทเพื่อบันทึก | SharedExpenseCopy.kt `hintRate` |
| Pick a category to save | เลือกหมวดหมู่เพื่อบันทึก | SharedExpenseCopy.kt `hintCategory` |
| The split doesn't add up yet. Open Split to fix it. | การหารยังไม่ลงตัว เปิดการหารเพื่อแก้ไข | SharedExpenseCopy.kt `hintSplit` |
| {typed} counts as {whole} | {typed} นับเป็น {whole} | SharedExpenseCopy.kt `countsAs` |
| Paid by and split | ใครจ่ายและการหาร | SharedExpenseCopy.kt `paidAndSplit` |
| You paid, split equally | คุณจ่าย หารเท่ากัน | SharedExpenseCopy.kt `quickYouEqual` |
| You are owed the full amount | คุณจะได้รับเต็มจำนวน | SharedExpenseCopy.kt `quickYouFull` |
| {name} paid, split equally | {name} จ่าย หารเท่ากัน | SharedExpenseCopy.kt `quickOtherEqual` |
| {name} is owed the full amount | {name} จะได้รับเต็มจำนวน | SharedExpenseCopy.kt `quickOtherFull` |
| Owed the full amount | ได้รับเต็มจำนวน | SharedExpenseCopy.kt `quickFull` |
| More options | ตัวเลือกเพิ่มเติม | SharedExpenseCopy.kt `moreOptions` |

## Repeat an expense

| English | Thai | Where |
|---|---|---|
| Repeat expense | รายการประจำ | SharedExpenseCopy.kt `SharedRepeatCopy.title` |
| How often | บ่อยแค่ไหน | SharedExpenseCopy.kt `howOften` |
| On | เริ่มวันที่ | SharedExpenseCopy.kt `on` |
| Ends | สิ้นสุด | SharedExpenseCopy.kt `ends` |
| Remind me the day before | เตือนล่วงหน้า 1 วัน | SharedExpenseCopy.kt `remind` |
| NEXT THREE | 3 ครั้งถัดไป | SharedExpenseCopy.kt `nextThree` |
| Stop repeating | หยุดรายการประจำ | SharedExpenseCopy.kt `stop` |
| Never | ไม่มีกำหนด | SharedExpenseCopy.kt `never` |
| After {n} times | หลังจาก {n} ครั้ง | SharedExpenseCopy.kt `afterTimes` |
| On a date | ในวันที่กำหนด | SharedExpenseCopy.kt `onADate` |
| On {date} | ถึง {date} | SharedExpenseCopy.kt `onDate` |
| Pick a day | เลือกวัน | SharedExpenseCopy.kt `pickADay` |
| You paid | คุณจ่าย | SharedExpenseCopy.kt `youPaid` |
| {name} paid | {name} จ่าย | SharedExpenseCopy.kt `paid` |
| split 50 / 50 with {name} | หาร 50 / 50 กับ {name} | SharedExpenseCopy.kt `splitHalf` |
| split equally, {n} ways | หารเท่ากัน {n} คน | SharedExpenseCopy.kt `splitEqually` |
| Save · repeats {often} | บันทึก · {often} | SharedExpenseCopy.kt `save` |
| weekly | ทุกสัปดาห์ | SharedExpenseCopy.kt `weekly` |
| every 2 weeks | ทุก 2 สัปดาห์ | SharedExpenseCopy.kt `everyTwoWeeks` |
| monthly | ทุกเดือน | SharedExpenseCopy.kt `monthly` |
| yearly | ทุกปี | SharedExpenseCopy.kt `yearly` |
| Weekly | รายสัปดาห์ | SharedExpenseCopy.kt `freqWeekly` |
| 2 weeks | 2 สัปดาห์ | SharedExpenseCopy.kt `freqTwoWeeks` |
| Monthly | รายเดือน | SharedExpenseCopy.kt `freqMonthly` |
| Yearly | รายปี | SharedExpenseCopy.kt `freqYearly` |
| Rent | ค่าเช่า | SharedExpenseCopy.kt `kindRent` |
| Utilities | ค่าน้ำค่าไฟ | SharedExpenseCopy.kt `kindUtilities` |
| Internet | อินเทอร์เน็ต | SharedExpenseCopy.kt `kindInternet` |
| Subscription | ค่าสมาชิก | SharedExpenseCopy.kt `kindSubscription` |
| Repeat saved. No reminder: notifications are off for PaiSen in system settings. | บันทึกรายการประจำแล้ว ไม่มีการเตือน: การแจ้งเตือนของ PaiSeN ปิดอยู่ในการตั้งค่าระบบ | SharedExpenseCopy.kt `noReminder` |
| {name} owes you {amount} | {name} ต้องจ่ายคุณ {amount} | SharedExpenseCopy.kt `owesYou` |
| You are owed {amount} | คุณจะได้รับ {amount} | SharedExpenseCopy.kt `SharedRepeatCopy.youAreOwed` |
| You owe {name} {amount} | คุณต้องจ่าย {name} {amount} | SharedExpenseCopy.kt `SharedRepeatCopy.youOwe` |
| {what} repeats tomorrow (reminder) | {what} จะถึงกำหนดพรุ่งนี้ | SharedExpenseCopy.kt `reminderTitle` |
| {amount} goes into {group} tomorrow. | {amount} จะเข้า {group} พรุ่งนี้ | SharedExpenseCopy.kt `reminderBody` |
| Stop repeating {what}? | หยุดรายการประจำ {what} ไหม | SharedExpenseCopy.kt `stopAsk` |
| No more dates are added. What was already added stays in the group. | จะไม่เพิ่มวันใหม่อีก รายการที่เพิ่มไปแล้วยังอยู่ในกลุ่ม | SharedExpenseCopy.kt `stopBody` |

## Record a payment

| English | Thai | Where |
|---|---|---|
| Record a payment | บันทึกการจ่ายเงิน | SharedPaymentCopy.kt `title` |
| PAID | จ่ายแล้ว | SharedPaymentCopy.kt `paidCaps` |
| Amount paid | จำนวนที่จ่าย | SharedPaymentCopy.kt `amountPaid` |
| How was it paid? | จ่ายด้วยวิธีไหน | SharedPaymentCopy.kt `howPaid` |
| Date | วันที่ | SharedPaymentCopy.kt `date` |
| After this | หลังจากนี้ | SharedPaymentCopy.kt `afterThis` |
| PromptPay QR for this payment | PromptPay QR สำหรับการจ่ายเงินนี้ | SharedPaymentCopy.kt `qrDescription` |
| Scan it in your bank app and check the name it shows before you pay. Then record it here. | สแกนในแอปธนาคารของคุณ และตรวจชื่อที่แสดงก่อนจ่าย แล้วค่อยบันทึกที่นี่ | SharedPaymentCopy.kt `scanHint` |
| This only records the payment. No money moves through PaiSen. | ที่นี่แค่บันทึกการจ่ายเงิน ไม่มีเงินผ่าน PaiSeN | SharedPaymentCopy.kt `onlyRecords` |
| Open my bank app | เปิดแอปธนาคารของฉัน | SharedPaymentCopy.kt `openMyBank` |
| QR saved to Photos. In your bank app, tap Scan, then choose it from your photos. | บันทึก QR ลงในรูปภาพแล้ว ในแอปธนาคาร แตะสแกน แล้วเลือกจากรูปภาพ | SharedPaymentCopy.kt `qrSaved` |
| Take a screenshot of this QR, then choose it from your photos in your bank app. | แคปหน้าจอ QR นี้ แล้วเลือกจากรูปภาพในแอปธนาคารของคุณ | SharedPaymentCopy.kt `takeScreenshot` |
| Open your bank app | เปิดแอปธนาคารของคุณ | SharedPaymentCopy.kt `openYourBank` |
| No Thai bank app found on this phone. Open yours and choose the QR from your photos. | ไม่พบแอปธนาคารไทยในโทรศัพท์เครื่องนี้ เปิดแอปของคุณแล้วเลือก QR จากรูปภาพ | SharedPaymentCopy.kt `noBankApp` |
| Full balance · tap to pay part of it | ยอดเต็ม · แตะเพื่อจ่ายบางส่วน | SharedPaymentCopy.kt `fullBalance` |
| Nothing owed between you two right now | ตอนนี้ไม่มียอดค้างระหว่างคุณสองคน | SharedPaymentCopy.kt `nothingOwed` |
| You are settled up | คุณเคลียร์แล้ว | SharedPaymentCopy.kt `youAreSettled` |
| {name} is settled up | {name} เคลียร์แล้ว | SharedPaymentCopy.kt `isSettled` |
| {debtor} still owes {creditor} {amount} | {debtor} ยังต้องจ่าย {creditor} {amount} | SharedPaymentCopy.kt `stillOwes` |
| {debtor} owes {creditor} {amount} | {debtor} ต้องจ่าย {creditor} {amount} | SharedPaymentCopy.kt `owes` |
| Record {amount} | บันทึก {amount} | SharedPaymentCopy.kt `record` |
| Payment recorded | บันทึกการจ่ายเงินแล้ว | SharedPaymentCopy.kt `recorded` |
| Pay {to} by PromptPay {id} | จ่ายให้ {to} ผ่าน PromptPay {id} | SharedPaymentCopy.kt `payBy` |
| Ask {name} to pay | ขอให้ {name} จ่าย | SharedPaymentCopy.kt `askToPay` |
| {group}: {name}, you owe me {amount}. (sent to the other person) | {group}: {name} คุณต้องจ่ายฉัน {amount} | SharedPaymentCopy.kt `askMessage` |
| My PromptPay: {id} | PromptPay ของฉัน: {id} | SharedPaymentCopy.kt `askPromptPay` |
| Payment | การจ่ายเงิน | SharedPaymentCopy.kt `detailTitle` |
| Remove payment | นำการจ่ายเงินออก | SharedPaymentCopy.kt `remove` |
| Remove this payment? | นำการจ่ายเงินนี้ออกไหม | SharedPaymentCopy.kt `removeAsk` |
| Balances drop it and Activity keeps a line saying you removed it. | ยอดคงค้างจะไม่นับรายการนี้ และกิจกรรมจะมีบรรทัดบอกว่าคุณนำออก | SharedPaymentCopy.kt `removeBodyShared` |
| Balances drop it. | ยอดคงค้างจะไม่นับรายการนี้ | SharedPaymentCopy.kt `removeBodyLocal` |
| Payment removed | นำการจ่ายเงินออกแล้ว | SharedPaymentCopy.kt `removed` |

## Group screen redesign (#61), not reviewed yet

| English | Thai | Where |
|---|---|---|
| Just you so far | ตอนนี้มีแค่คุณ | SharedGroupCopy.kt `justYou` |
| with {name} · {amount} | กับ {name} · {amount} | SharedGroupCopy.kt `settleWith` |
| All settled up | เคลียร์ครบแล้ว | SharedGroupCopy.kt `allSettledUp` |
| Nothing to settle yet | ยังไม่มีอะไรต้องเคลียร์ | SharedGroupCopy.kt `nothingToSettle` |
| Nothing spent yet · counts in {code} | ยังไม่มีค่าใช้จ่าย · นับเป็น {code} | SharedGroupCopy.kt `nothingSpent` |
| {n} new | ใหม่ {n} รายการ | SharedGroupCopy.kt `newActivity` |
| New since you last looked | ใหม่ตั้งแต่ที่คุณดูครั้งล่าสุด | SharedGroupCopy.kt `newSinceLooked` |
| Changes anyone makes in this group show here. | การเปลี่ยนแปลงของทุกคนในกลุ่มนี้จะแสดงที่นี่ | SharedGroupCopy.kt `activityEmpty` |
| Everyone's square | ทุกคนเคลียร์กันแล้ว | SharedGroupCopy.kt `everyoneSquare` |
| Who's in the group | ใครอยู่ในกลุ่มนี้ | SharedGroupCopy.kt `whoIsIn` |
| No expenses yet | ยังไม่มีค่าใช้จ่าย | SharedGroupCopy.kt `noExpensesTitle` |
| Add what someone paid for, and it's split between everyone here. | เพิ่มสิ่งที่ใครสักคนจ่ายไป แล้วจะหารกับทุกคนที่นี่ | SharedGroupCopy.kt `noExpensesBody` |
| Your own ledger is unchanged. | บัญชีของคุณเองไม่เปลี่ยน | SharedGroupCopy.kt `ledgerUnchanged` |
| Back to Shared | กลับไปที่กลุ่ม | SharedGroupCopy.kt `backToShared` |

## Record payment didn't save (#76), not reviewed yet

| English | Thai | Where |
|---|---|---|
| Couldn't record this payment | บันทึกการจ่ายเงินนี้ไม่ได้ | SharedPaymentCopy.kt `didntSave` |
| Nothing was saved. What you typed is still here, so try again. | ยังไม่ได้บันทึกอะไร สิ่งที่คุณพิมพ์ยังอยู่ ลองอีกครั้ง | SharedPaymentCopy.kt `keptTyped` |

## Design v2 (#75), not reviewed yet

| English | Thai | Where |
|---|---|---|
| +{n} | +{n} | `SharedDetailCopy` moreAvatars |
| {n} more people | อีก {n} คน | `SharedDetailCopy` morePeople |
