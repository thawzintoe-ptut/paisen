# Thai review: slips, photo folders and first run

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in. The choices in `th-shell.md` apply here too.

Choices to check in this area:

- The To check screen is รอตรวจ, and slips count with ใบ.
- A shop rule is กฎของร้าน. Repeats are รายการประจำ, as the shared-group slices will call them.
- The first-run headline keeps three short lines: รายจ่าย / ง่ายขึ้น / เสียที.
- `shopReadAsNoise` tells a Thai reader the app cannot read Thai shop names yet. Does it read as plain fact?

## Review, Check slip, shop rules, Activity and shared slips

| English | Thai | Where |
|---|---|---|
| To check | รอตรวจ | SlipsCopy.kt `reviewTitle` |
| Nothing needs you | ไม่มีอะไรต้องตรวจ | SlipsCopy.kt `reviewEmptyHeadline` |
| Every slip read so far was filed with confidence. Anything unclear will wait here instead of being guessed. | สลิปทุกใบที่อ่านมาบันทึกได้อย่างมั่นใจ ถ้ามีอะไรไม่ชัด จะรอไว้ที่นี่แทนการเดา | SlipsCopy.kt `reviewEmptyBody` |
| Last {n} slips filed automatically | บันทึกอัตโนมัติ {n} ใบล่าสุด | SlipsCopy.kt `reviewEmptyStatus` |
| {n} slips to check | สลิปที่ต้องตรวจ {n} ใบ | SlipsCopy.kt `reviewHeadline` |
| Not counted in any total until you confirm them. | ยังไม่นับรวมในยอดใดจนกว่าคุณจะยืนยัน | SlipsCopy.kt `reviewNotCounted` |
| Slips we could not read are kept {n} days, then removed from PaiSeN. Slips that filed cleanly are removed straight away. Your gallery photo stays. | สลิปที่อ่านไม่ได้จะเก็บไว้ {n} วัน แล้วลบออกจาก PaiSeN สลิปที่บันทึกเรียบร้อยแล้วจะลบออกทันที รูปในแกลเลอรีของคุณยังอยู่ | SlipsCopy.kt `retentionNote` |
| The oldest here loses its image today. | สลิปที่เก่าที่สุดตรงนี้จะลบรูปวันนี้ | SlipsCopy.kt `retentionNote` |
| The oldest here has {n} days left. | สลิปที่เก่าที่สุดตรงนี้เหลืออีก {n} วัน | SlipsCopy.kt `retentionNote` |
| Not counted | ยังไม่นับ | SlipsCopy.kt `notCountedPill`, `notCounted` |
| Amount unclear | ยอดเงินไม่ชัด | SlipsCopy.kt `amountUnclearPill` |
| Check slip | ตรวจสลิป | SlipsCopy.kt `checkSlip` |
| Discard this slip | ทิ้งสลิปนี้ | SlipsCopy.kt `discardSlip` |
| New shop | ร้านใหม่ | SlipsCopy.kt `reasonNewShop` |
| Amount hard to read | อ่านยอดเงินยาก | SlipsCopy.kt `reasonAmountHard` |
| Could not read this slip | อ่านสลิปนี้ไม่ได้ | SlipsCopy.kt `reasonUnreadable` |
| Unknown shop | ร้านที่ไม่รู้จัก | SlipsCopy.kt `unknownShop` |
| Close without saving | ปิดโดยไม่บันทึก | SlipsCopy.kt `closeWithoutSaving` |
| Amount not read | อ่านยอดเงินไม่ได้ | SlipsCopy.kt `amountNotRead` |
| Read from slip | อ่านจากสลิป | SlipsCopy.kt `readFromSlip` |
| Paid to | จ่ายให้ | SlipsCopy.kt `paidTo` |
| Reference | เลขอ้างอิง | SlipsCopy.kt `reference` |
| {label} · check it | {label} · โปรดตรวจ | SlipsCopy.kt `checkIt` |
| Not mine | ไม่ใช่ของฉัน | SlipsCopy.kt `notMine` |
| Is this your {amount} payment to {name} in {group}? | นี่คือเงิน {amount} ที่คุณจ่ายให้ {name} ในกลุ่ม {group} ใช่ไหม | SlipsCopy.kt `debtQuestion` |
| {name} · {group} | {name} · {group} | SlipsCopy.kt `debtChoice` |
| Yes, group payment only | ใช่ เป็นการจ่ายในกลุ่มเท่านั้น | SlipsCopy.kt `debtOnly` |
| Yes, and keep it in my spending | ใช่ และนับเป็นรายจ่ายของฉันด้วย | SlipsCopy.kt `debtKeep` |
| Not this | ไม่ใช่รายการนี้ | SlipsCopy.kt `debtNotThis` |
| Recorded {amount} to {name} in {group} | บันทึกการจ่าย {amount} ให้ {name} ในกลุ่ม {group} แล้ว | SlipsCopy.kt `debtRecorded` |
| Recorded as paid to {name}. File it below to keep it in your spending. | บันทึกว่าจ่ายให้ {name} แล้ว เลือกหมวดหมู่ด้านล่างเพื่อนับเป็นรายจ่ายของคุณด้วย | SlipsCopy.kt `debtKeepNote` |
| Confirm | ยืนยัน | SlipsCopy.kt `confirm` |
| Pick a category to save this slip | เลือกหมวดหมู่เพื่อบันทึกสลิปนี้ | SlipsCopy.kt `pickCategory` |
| Removed from PaiSeN. Your gallery photo stays. | ลบออกจาก PaiSeN แล้ว รูปในแกลเลอรีของคุณยังอยู่ | SlipsCopy.kt `slipRemoved` |
| This slip is no longer here | สลิปนี้ไม่อยู่แล้ว | SlipsCopy.kt `slipGoneHeadline` |
| It was confirmed or removed, or its days ran out. A confirmed slip is already in your totals. | สลิปนี้ถูกยืนยันหรือลบไปแล้ว หรือหมดเวลาเก็บ สลิปที่ยืนยันแล้วนับรวมในยอดของคุณแล้ว | SlipsCopy.kt `slipGoneBody` |
| Nothing left to check here | ไม่มีอะไรต้องตรวจที่นี่แล้ว | SlipsCopy.kt `slipGoneStatus` |
| today | วันนี้ | SlipsCopy.kt `savedToday` |
| Saved {amount} to {day} · {category} | บันทึก {amount} · {day} · {category} | SlipsCopy.kt `saved` |
| … · {category} remembered for {shop} | … · จำ {shop} ไว้เป็น {category} | SlipsCopy.kt `saved` |
| {day}, {time} | {day} {time} | SlipsCopy.kt `slipWhen` |
| When | วันเวลา | SlipsCopy.kt `whenLabel` |
| When · photo time | วันเวลา · เวลาถ่ายรูป | SlipsCopy.kt `whenPhotoTime` |
| Not read | อ่านไม่ได้ | SlipsCopy.kt `confidenceNotRead` |
| Read with low confidence | อ่านได้แต่ไม่มั่นใจ | SlipsCopy.kt `confidenceLow` |
| Amount read clearly | อ่านยอดเงินได้ชัดเจน | SlipsCopy.kt `confidenceClear` |
| MERCHANT | ร้านค้า | SlipsCopy.kt `merchantLabel` |
| REF | เลขอ้างอิง | SlipsCopy.kt `referenceLabel` |
| {label} · not read | {label} · อ่านไม่ได้ | SlipsCopy.kt `labelNotRead` |
| {label} · read, low confidence | {label} · อ่านได้ ไม่มั่นใจ | SlipsCopy.kt `labelLowConfidence` |
| Every later slip from {shop} will file itself here. Past transactions are left alone. | สลิปต่อไปทุกใบจาก {shop} จะเข้าหมวดหมู่นี้เอง รายการที่ผ่านมาไม่เปลี่ยน | SlipsCopy.kt `ruleNote` |
| No shop could be read, so this is saved by hand and nothing is remembered for next time. | อ่านชื่อร้านไม่ได้ จึงบันทึกเองครั้งนี้ และไม่จำอะไรไว้สำหรับครั้งหน้า | SlipsCopy.kt `ruleNoteNoShop` |
| Shop rule | กฎของร้าน | SlipsCopy.kt `shopRuleTitle` |
| This shop is not remembered, so nothing files itself. The next slip from it will ask once. | ไม่ได้จำร้านนี้ไว้ จึงไม่มีอะไรเข้าหมวดหมู่เอง สลิปถัดไปจากร้านนี้จะถามหนึ่งครั้ง | SlipsCopy.kt `shopNotRemembered` |
| Later slips from this shop file here. What is already recorded stays as it is. | สลิปต่อไปจากร้านนี้จะเข้าที่นี่ รายการที่บันทึกแล้วยังคงเดิม | SlipsCopy.kt `shopFilesHere` |
| The slip prints this shop's name in Thai, which PaiSeN cannot read. Name it once, and every slip from it shows your name. | สลิปพิมพ์ชื่อร้านนี้เป็นภาษาไทย ซึ่ง PaiSeN ยังอ่านไม่ได้ ตั้งชื่อครั้งเดียว แล้วทุกสลิปจากร้านนี้จะแสดงชื่อที่คุณตั้ง | SlipsCopy.kt `shopReadAsNoise` |
| Shop name | ชื่อร้าน | SlipsCopy.kt `shopName` |
| For example, Electricity | เช่น ค่าไฟ | SlipsCopy.kt `shopNameExample` |
| What is this shop? | ร้านนี้คือร้านอะไร | SlipsCopy.kt `whatIsThisShop` |
| Forget this shop | ลืมร้านนี้ | SlipsCopy.kt `forgetShop` |
| Forgetting keeps every slip already filed. The next one asks once, like a shop the app has never seen. | การลืมร้านไม่ลบสลิปที่บันทึกแล้ว สลิปถัดไปจะถามหนึ่งครั้ง เหมือนร้านที่แอปไม่เคยเห็น | SlipsCopy.kt `forgetNote` |
| Nothing filed by this rule yet. | กฎนี้ยังไม่ได้บันทึกอะไร | SlipsCopy.kt `filedSoFar` |
| {n} slips filed here without asking. | บันทึกสลิป {n} ใบที่นี่โดยไม่ถาม | SlipsCopy.kt `filedSoFar` |
| Activity | กิจกรรม | SlipsCopy.kt `activityTitle` |
| Nothing to report | ไม่มีอะไรต้องแจ้ง | SlipsCopy.kt `activityEmptyHeadline` |
| Imports, failures and new shops all land here. A quiet list means the importer has had nothing to say. | การนำเข้า ข้อผิดพลาด และร้านใหม่จะแสดงที่นี่ทั้งหมด รายการว่างแปลว่ายังไม่มีอะไรต้องแจ้ง | SlipsCopy.kt `activityEmptyBody` |
| No import has run yet | ยังไม่เคยนำเข้า | SlipsCopy.kt `activityEmptyStatus` |
| needs you | รอคุณ | SlipsCopy.kt `needsYou` |
| Could not read the folder | อ่านโฟลเดอร์ไม่ได้ | SlipsCopy.kt `folderUnread` |
| The importer stopped before it could scan. | การนำเข้าหยุดก่อนจะสแกนได้ | SlipsCopy.kt `importerStopped` |
| Import in progress | กำลังนำเข้า | SlipsCopy.kt `importRunning` |
| Started, and has not reported back yet. | เริ่มแล้ว และยังไม่ได้รายงานกลับ | SlipsCopy.kt `importRunningBody` |
| Checked, nothing new | ตรวจแล้ว ไม่มีอะไรใหม่ | SlipsCopy.kt `nothingNew` |
| The watched folders held no slip the app had not already read. | โฟลเดอร์ที่ติดตามไม่มีสลิปที่แอปยังไม่ได้อ่าน | SlipsCopy.kt `nothingNewBody` |
| Shared slips | สลิปที่แชร์มา | SlipsCopy.kt `sharedSlips` |
| Imported {n} slips | นำเข้าสลิป {n} ใบ | SlipsCopy.kt `imported` |
| all sorted automatically | บันทึกอัตโนมัติทั้งหมด | SlipsCopy.kt `allSorted` |
| {n} to check | รอตรวจ {n} ใบ | SlipsCopy.kt `toCheck` |
| Your week, {from} to {to} | สัปดาห์ของคุณ {from} ถึง {to} | SlipsCopy.kt `weekTitle` |
| You spent {spent} Mon–Sun, {comparison}. | คุณใช้ไป {spent} วันจันทร์ถึงวันอาทิตย์ {comparison} | SlipsCopy.kt `weekBody` |
| last week | สัปดาห์ที่แล้ว | SlipsCopy.kt `lastWeek` |
| A slip needs a category | สลิป 1 ใบต้องการหมวดหมู่ | SlipsCopy.kt `needsCategory` |
| A slip needs an amount | สลิป 1 ใบต้องการยอดเงิน | SlipsCopy.kt `needsAmount` |
| Could not read 1 image | อ่านรูป 1 รูปไม่ได้ | SlipsCopy.kt `imageUnread` |
| {shop} · {amount} · added to To check | {shop} · {amount} · เพิ่มในรอตรวจแล้ว | SlipsCopy.kt `addedToCheck` |
| {shop} · the amount could not be read with confidence | {shop} · อ่านยอดเงินได้ไม่มั่นใจ | SlipsCopy.kt `amountNotConfident` |
| Added to To check · fill it in by hand | เพิ่มในรอตรวจแล้ว · กรอกเอง | SlipsCopy.kt `fillByHand` |
| now | ตอนนี้ | SlipsCopy.kt `justNow` |
| {n} min | {n} นาที | SlipsCopy.kt `minutesAgo` |
| The slip import could not run. Open PaiSeN to try again. | นำเข้าสลิปไม่ได้ เปิด PaiSeN เพื่อลองอีกครั้ง | SlipsCopy.kt `importCouldNotRun` |
| That image could not be imported. Share it again from K PLUS. | นำเข้ารูปนั้นไม่ได้ แชร์อีกครั้งจาก K PLUS | SlipsCopy.kt `shareFailed` |
| That image could not be opened, so it was not imported. Share it again from K PLUS. | เปิดรูปนั้นไม่ได้ จึงไม่ได้นำเข้า แชร์อีกครั้งจาก K PLUS | SlipsCopy.kt `shareNotOpened` |
| This app was not given access to that image | แอปนี้ไม่ได้รับสิทธิ์เข้าถึงรูปนั้น | SlipsCopy.kt `shareRefused` |
| That image was empty, so there was nothing to read | รูปนั้นว่างเปล่า จึงไม่มีอะไรให้อ่าน | SlipsCopy.kt `shareEmpty` |
| That image could not be filed. Share it again from K PLUS. | บันทึกรูปนั้นไม่ได้ แชร์อีกครั้งจาก K PLUS | SlipsCopy.kt `shareNotFiled` |
| That image could not be saved for import. Free some space on the phone, then share it again. | เก็บรูปนั้นเพื่อนำเข้าไม่ได้ ล้างพื้นที่ในโทรศัพท์ แล้วแชร์อีกครั้ง | SlipsCopy.kt `shareNotSaved` |
| Slip received | ได้รับสลิปแล้ว | SlipsCopy.kt `shareReceived` |
| Slip received · {where} | ได้รับสลิปแล้ว · {where} | SlipsCopy.kt `shareLanded` |
| Filed to {day} | บันทึกใน {day} | SlipsCopy.kt `shareFiledTo` |
| Already recorded | บันทึกไว้แล้ว | SlipsCopy.kt `shareAlreadyRecorded` |
| Waiting in To check | รออยู่ในรอตรวจ | SlipsCopy.kt `shareWaiting` |
| No slip found in that picture | ไม่พบสลิปในรูปนั้น | SlipsCopy.kt `shareNotASlip` |
| Not read yet, tried again on the next scan | ยังอ่านไม่ได้ จะลองอีกครั้งในการสแกนครั้งถัดไป | SlipsCopy.kt `shareNotReadYet` |
| The picture was gone before it was read | รูปหายไปก่อนจะอ่านได้ | SlipsCopy.kt `shareGone` |
| It will show in Today or To check | จะแสดงในวันนี้หรือรอตรวจ | SlipsCopy.kt `shareLookIn` |

## Photo folders and the platform's notices

| English | Thai | Where |
|---|---|---|
| Photo folders | โฟลเดอร์รูป | FoldersCopy.kt `title` |
| Where your slips are | สลิปของคุณอยู่ที่ไหน | FoldersCopy.kt `headline` |
| Pick every gallery folder a bank app saves slips to. Only these are read. | เลือกทุกโฟลเดอร์ในแกลเลอรีที่แอปธนาคารบันทึกสลิปไว้ แอปจะอ่านเฉพาะโฟลเดอร์เหล่านี้ | FoldersCopy.kt `intro` |
| Photo access is off, so no folder can be read. Allow access to all photos in your phone's settings for this app. | ปิดการเข้าถึงรูปอยู่ จึงอ่านโฟลเดอร์ไม่ได้ อนุญาตให้แอปนี้เข้าถึงรูปทั้งหมดในการตั้งค่าของโทรศัพท์ | FoldersCopy.kt `accessOff` |
| Only images that look like payment slips are read. Everything else in these folders is ignored and never leaves your phone. | แอปจะอ่านเฉพาะรูปที่ดูเหมือนสลิปโอนเงิน รูปอื่นในโฟลเดอร์เหล่านี้จะถูกข้ามและไม่ออกจากโทรศัพท์ของคุณ | FoldersCopy.kt `footnote` |
| Save · {n} folders | บันทึก · {n} โฟลเดอร์ | FoldersCopy.kt `save` |
| {n} slips read | อ่านสลิปแล้ว {n} ใบ | FoldersCopy.kt `slipsRead` |
| No slips found yet | ยังไม่พบสลิป | FoldersCopy.kt `noSlipsYet` |
| Not checked | ยังไม่ได้ตรวจ | FoldersCopy.kt `notChecked` |
| {n} images | {n} รูป | FoldersCopy.kt `images` |
| {n} folders watched · {n} slips read so far | ติดตาม {n} โฟลเดอร์ · อ่านสลิปแล้ว {n} ใบ | FoldersCopy.kt `summary` |
| Every folder on this phone holding a picture is listed above. | ทุกโฟลเดอร์ในโทรศัพท์ที่มีรูปแสดงอยู่ด้านบนแล้ว | FoldersCopy.kt `allFoldersListed` |
| Allow photo access | อนุญาตการเข้าถึงรูป | FoldersCopy.kt `allowAccess` |
| Android is not letting this app see your folders yet. | Android ยังไม่ให้แอปนี้เห็นโฟลเดอร์ของคุณ | FoldersCopy.kt `androidNotAllowed` |
| Pick more photos | เลือกรูปเพิ่ม | FoldersCopy.kt `pickMorePhotos` |
| Only folders holding photos you picked are listed. A new slip shows up once you pick it, or once you allow all photos. | แสดงเฉพาะโฟลเดอร์ที่มีรูปที่คุณเลือก สลิปใหม่จะแสดงเมื่อคุณเลือกรูปนั้น หรืออนุญาตให้เข้าถึงรูปทั้งหมด | FoldersCopy.kt `androidOnlyPicked` |
| Share another album | แชร์อัลบั้มอื่น | FoldersCopy.kt `shareAnotherAlbum` |
| Open Settings | เปิดการตั้งค่า | FoldersCopy.kt `openSettings` |
| Allow access, then choose the albums your bank app saves slips to. | อนุญาตการเข้าถึง แล้วเลือกอัลบั้มที่แอปธนาคารบันทึกสลิปไว้ | FoldersCopy.kt `iosNotAsked` |
| Photo access is off for this app. Turn it on in iOS Settings, then come back here. | ปิดการเข้าถึงรูปสำหรับแอปนี้อยู่ เปิดในการตั้งค่าของ iOS แล้วกลับมาที่นี่ | FoldersCopy.kt `iosDenied` |
| Albums you have not shared with this app stay invisible to it. | แอปนี้มองไม่เห็นอัลบั้มที่คุณไม่ได้แชร์ให้ | FoldersCopy.kt `albumsNotShared` |
| Album you made | อัลบั้มที่คุณสร้าง | FoldersCopy.kt `albumYouMade` |
| Album iOS keeps for you | อัลบั้มที่ iOS จัดให้ | FoldersCopy.kt `albumIosKeeps` |
| Photo access is off, so no folder can be read. Allow access to photos in Android's app settings. | ปิดการเข้าถึงรูปอยู่ จึงอ่านโฟลเดอร์ไม่ได้ อนุญาตการเข้าถึงรูปในการตั้งค่าแอปของ Android | FoldersCopy.kt `androidAccessDenied` |
| Photo access is off, so no album can be scanned. Allow photo access in iOS Settings. | ปิดการเข้าถึงรูปอยู่ จึงสแกนอัลบั้มไม่ได้ อนุญาตการเข้าถึงรูปในการตั้งค่าของ iOS | FoldersCopy.kt `iosAccessDenied` |
| The photo library did not answer, so no slip was scanned | คลังรูปไม่ตอบสนอง จึงไม่ได้สแกนสลิป | FoldersCopy.kt `libraryNoAnswer` |
| That file could not be opened for writing. Choose another place to save it. | เปิดไฟล์นั้นเพื่อเขียนไม่ได้ เลือกที่อื่นเพื่อบันทึก | FoldersCopy.kt `fileNotOpenedForWriting` |
| That file could not be opened for reading. Choose the backup file again. | เปิดไฟล์นั้นเพื่ออ่านไม่ได้ เลือกไฟล์สำรองอีกครั้ง | FoldersCopy.kt `fileNotOpenedForReading` |
| The file could not be written before saving. Free some space on the phone, then try again. | เขียนไฟล์ก่อนบันทึกไม่ได้ ล้างพื้นที่ในโทรศัพท์ แล้วลองอีกครั้ง | FoldersCopy.kt `fileNotStaged` |
| That file could not be read. Choose the backup file again. | อ่านไฟล์นั้นไม่ได้ เลือกไฟล์สำรองอีกครั้ง | FoldersCopy.kt `fileNotRead` |
| No screen to present the file picker on | ไม่มีหน้าจอสำหรับแสดงตัวเลือกไฟล์ | FoldersCopy.kt `noScreenForPicker` |
| Repeats (notification channel) | รายการประจำ | FoldersCopy.kt `repeatsChannel` |
| Shared groups (notification channel) | กลุ่ม | FoldersCopy.kt `sharedGroupsChannel` |

## First run

| English | Thai | Where |
|---|---|---|
| Spending, / finally / simple. | รายจ่าย / ง่ายขึ้น / เสียที | OnboardingCopy.kt `headline` |
| Bank slips from your gallery become a tidy record of every baht. Your spending stays on this phone. | สลิปธนาคารในแกลเลอรีกลายเป็นบันทึกรายจ่ายที่เป็นระเบียบทุกบาท ข้อมูลการใช้จ่ายของคุณอยู่ในโทรศัพท์เครื่องนี้เท่านั้น | OnboardingCopy.kt `promise` |
| One setting to change. | มีการตั้งค่าเดียวที่ต้องเปลี่ยน | OnboardingCopy.kt `settingTitle` |
| Set K PLUS slip language to English so the reader can see the numbers. | ตั้งภาษาสลิปของ K PLUS เป็นภาษาอังกฤษ เพื่อให้แอปอ่านตัวเลขได้ | OnboardingCopy.kt `settingText` |
| Restore | กู้คืน | OnboardingCopy.kt `restore` |
| Allow photos | อนุญาตการเข้าถึงรูป | OnboardingCopy.kt `allowPhotos` |
| I'll add expenses by hand | ฉันจะเพิ่มรายจ่ายเอง | OnboardingCopy.kt `byHand` |
| That file could not be read. Nothing was changed. | อ่านไฟล์นั้นไม่ได้ ไม่มีอะไรเปลี่ยนแปลง | OnboardingCopy.kt `restoreFailed` |

## Design v2 (#72), not reviewed yet

| English | Thai | Where |
|---|---|---|
| Reading the slip… | กำลังอ่านสลิป… | `SlipsCopy` readingSlip |
| Reading… | กำลังอ่าน… | `SlipsCopy` reading |

## Fewer choices at each step (22 Nov, not yet checked)

| English | Thai | Where |
|---|---|---|
| Picked from the shop name. Change it if it's wrong. | เลือกจากชื่อร้าน ถ้าไม่ถูกต้องเปลี่ยนได้ | `SlipsCopy` pickedFromShop |
