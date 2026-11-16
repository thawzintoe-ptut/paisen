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

