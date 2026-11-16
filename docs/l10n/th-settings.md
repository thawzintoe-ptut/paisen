# Thai review: Settings and categories

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in. The choices in `th-shell.md` apply here too.

Choices to check in this area:

- The installed categories show as อาหาร, ของชำ, ค่าเดินทาง, บิล, ช้อปปิ้ง, อื่น ๆ and รายรับ until the
  reader renames them. They are saved under their English names, so a shared group never sees them in Thai.
- `englishSlipsText` asks a Thai reader to set K PLUS to English, because the slip reader reads Latin only.
- `licencesValue` and `coversZawgyi` name Burmese text, which the app ships for Burmese readers.

## Settings

| English | Thai | Where |
|---|---|---|
| Settings | ตั้งค่า | SettingsCopy.kt `title` |
| Your spending stays on this phone. | การใช้จ่ายของคุณอยู่ในโทรศัพท์เครื่องนี้ | SettingsCopy.kt `privacyTitle` |
| Slips, totals and reports never leave it, and account numbers on slips are never stored. Only shared groups sync, with the people in them. | สลิป ยอดรวม และรายงานไม่ออกจากโทรศัพท์ และไม่เคยเก็บเลขบัญชีบนสลิป มีเพียงกลุ่มที่ซิงก์กับคนในกลุ่ม | SettingsCopy.kt `privacyText` |
| Needs your attention | รอคุณจัดการ | SettingsCopy.kt `needsAttention` |
| Photo folders | โฟลเดอร์รูป | SettingsCopy.kt `photoFolders` |
| Background import | นำเข้าเบื้องหลัง | SettingsCopy.kt `backgroundImport` |
| Keep unread slips | เก็บสลิปที่อ่านไม่ได้ | SettingsCopy.kt `keepUnreadSlips` |
| {retention}, then removed from PaiSeN | {retention} แล้วลบออกจาก PaiSeN | SettingsCopy.kt `thenDeleted` |
| Activity | กิจกรรม | SettingsCopy.kt `activity` |
| Last import {elapsed} ago | นำเข้าล่าสุดเมื่อ {elapsed}ที่แล้ว | SettingsCopy.kt `lastImport` |
| No import yet | ยังไม่ได้นำเข้า | SettingsCopy.kt `noImportYet` |
| {n} categories · {n} sub-categories | {n} หมวดหมู่ · {n} หมวดหมู่ย่อย | SettingsCopy.kt `categoryCounts` |
| Mood by time of day | การ์ดตามช่วงเวลาของวัน | SettingsCopy.kt `moodByTimeOfDay` |
| Morning recap, evening receipt | สรุปตอนเช้า ใบเสร็จตอนเย็น | SettingsCopy.kt `moodValue` |
| Weekly check-in | สรุปรายสัปดาห์ | SettingsCopy.kt `weeklyCheckIn` |
| Mondays, last week beside a usual one | วันจันทร์ เทียบสัปดาห์ที่แล้วกับสัปดาห์ปกติ | SettingsCopy.kt `weeklyCheckInValue` |
| Transparency (the see-through nav bar) | ความโปร่งแสง | SettingsCopy.kt `transparency` |
| Frosted nav bar | แถบนำทางแบบฝ้า | SettingsCopy.kt `transparencyValue` |
| App lock | ล็อกแอป | SettingsCopy.kt `appLock` |
| Fingerprint, face or phone PIN | ลายนิ้วมือ ใบหน้า หรือ PIN ของโทรศัพท์ | SettingsCopy.kt `appLockValue` |
| PaiSeN is locked | PaiSeN ล็อกอยู่ | SettingsCopy.kt `lockedTitle` |
| Unlock to see your spending. | ปลดล็อกเพื่อดูการใช้จ่ายของคุณ | SettingsCopy.kt `lockedText` |
| Unlock | ปลดล็อก | SettingsCopy.kt `unlock` |
| Unlock PaiSeN (the phone's prompt) | ปลดล็อก PaiSeN | SettingsCopy.kt `unlockReason` |
| Timezone | เขตเวลา | SettingsCopy.kt `timezone` |
| Asia/Bangkok · day ends midnight | Asia/Bangkok · วันสิ้นสุดตอนเที่ยงคืน | SettingsCopy.kt `timezoneValue` |
| Export | ส่งออก | SettingsCopy.kt `exportLedger` |
| Backup file | ไฟล์สำรอง | SettingsCopy.kt `exportValue` |
| Import | นำเข้า | SettingsCopy.kt `importLedger` |
| Restore from a backup file | กู้คืนจากไฟล์สำรอง | SettingsCopy.kt `importValue` |
| On-device assistant | ผู้ช่วยในเครื่อง | SettingsCopy.kt `assistant` |
| Licences | สัญญาอนุญาต | SettingsCopy.kt `licences` |
| Fonts, icons and Burmese text | ฟอนต์ ไอคอน และข้อความภาษาพม่า | SettingsCopy.kt `licencesValue` |
| Send feedback | ส่งความคิดเห็น | SettingsCopy.kt `feedback` |
| PaiSeN feedback ({version}) | ความคิดเห็นเกี่ยวกับ PaiSeN ({version}) | SettingsCopy.kt `feedbackSubject` |
| What happened? (email body) | เกิดอะไรขึ้น | SettingsCopy.kt `feedbackBody` |
| No email app on this phone. Write to {address}. | โทรศัพท์เครื่องนี้ไม่มีแอปอีเมล เขียนถึง {address} | SettingsCopy.kt `noEmailApp` |
| Off · scans when you open the app | ปิด · สแกนเมื่อคุณเปิดแอป | SettingsCopy.kt `importOff` |
| last run {elapsed} ago | ทำงานล่าสุด {elapsed}ที่แล้ว | SettingsCopy.kt `lastRunInRow` |
| no run yet | ยังไม่เคยทำงาน | SettingsCopy.kt `noRunYet` |
| No folder watched | ไม่ได้ติดตามโฟลเดอร์ใด | SettingsCopy.kt `noFolderWatched` |
| On | เปิด | SettingsCopy.kt `importOn` |
| The app looks in your watched folders about every 15 minutes, even when it is closed. Android decides the exact moment, so a slip can take a little longer to appear on a phone that is saving battery. Opening the app always scans straight away. | แอปจะดูโฟลเดอร์ที่ติดตามประมาณทุก 15 นาที แม้ปิดแอปอยู่ Android เป็นผู้กำหนดเวลาที่แน่นอน สลิปจึงอาจแสดงช้าขึ้นเล็กน้อยในโทรศัพท์ที่ประหยัดแบตเตอรี่ การเปิดแอปจะสแกนทันทีเสมอ | SettingsCopy.kt `androidImportExplanation` |
| No album watched | ไม่ได้ติดตามอัลบั้มใด | SettingsCopy.kt `noAlbumWatched` |
| On each app open | ทุกครั้งที่เปิดแอป | SettingsCopy.kt `onEachAppOpen` |
| iOS gives an app no reliable window to work in while it is closed, so there is no schedule to switch here. Your watched albums are read every time you open the app, and whatever K PLUS saved while you were away is picked up then. | iOS ไม่ให้เวลาที่แน่นอนแก่แอปในการทำงานขณะปิดอยู่ จึงไม่มีตารางเวลาให้เปิดปิดที่นี่ แอปจะอ่านอัลบั้มที่ติดตามทุกครั้งที่คุณเปิดแอป และจะรับสลิปที่ K PLUS บันทึกไว้ระหว่างนั้น | SettingsCopy.kt `iosImportExplanation` |
| Scan in the background | สแกนเบื้องหลัง | SettingsCopy.kt `scanInBackground` |
| Off | ปิด | SettingsCopy.kt `off` |
| There is no schedule to switch on this phone, so nothing here can be turned off. | โทรศัพท์เครื่องนี้ไม่มีตารางเวลาให้เปิด จึงไม่มีอะไรให้ปิดที่นี่ | SettingsCopy.kt `noSchedule` |
| Looking… | กำลังดู… | SettingsCopy.kt `looking` |
| Check for slips now | ตรวจหาสลิปตอนนี้ | SettingsCopy.kt `checkNow` |
| Turning this off does not stop the app importing. It stops the app looking while it is closed — opening it still reads whatever is waiting. | การปิดตัวเลือกนี้ไม่ได้หยุดการนำเข้า แต่หยุดการดูขณะที่แอปปิดอยู่ เมื่อเปิดแอป แอปยังอ่านสลิปที่รออยู่ | SettingsCopy.kt `turningOffNote` |
| Last run {elapsed} ago | ทำงานล่าสุด {elapsed}ที่แล้ว | SettingsCopy.kt `lastRun` |
| Nothing has been imported yet | ยังไม่ได้นำเข้าอะไร | SettingsCopy.kt `nothingImportedYet` |
| The scan could not run. Check that PaiSeN can see your photos, then try again. | สแกนไม่ได้ ตรวจสอบว่า PaiSeN เห็นรูปของคุณ แล้วลองอีกครั้ง | SettingsCopy.kt `scanCouldNotRun` |
| The scan could not finish — Activity says why | สแกนไม่เสร็จ ดูสาเหตุได้ในกิจกรรม | SettingsCopy.kt `scanCouldNotFinish` |
| No new slips in your folders | ไม่มีสลิปใหม่ในโฟลเดอร์ของคุณ | SettingsCopy.kt `noNewSlips` |
| Found {n} · filed {n} · {n} waiting for you | พบ {n} · บันทึก {n} · รอคุณ {n} | SettingsCopy.kt `scanFound` |
| A slip the app could not read keeps its picture so you can look at it and fill in what is missing. After that PaiSeN removes its copy. Your gallery photo stays. | สลิปที่แอปอ่านไม่ได้จะเก็บรูปไว้ให้คุณดูและกรอกส่วนที่ขาด หลังจากนั้น PaiSeN จะลบสำเนาของตัวเอง รูปในแกลเลอรีของคุณยังอยู่ | SettingsCopy.kt `keepIntro` |
| No slips are waiting — nothing is being kept | ไม่มีสลิปที่รออยู่ จึงไม่ได้เก็บอะไรไว้ | SettingsCopy.kt `keptNone` |
| {n} slips are waiting for you | มีสลิป {n} ใบรอคุณอยู่ | SettingsCopy.kt `keptWaiting` |
| A slip that files cleanly is removed from PaiSeN straight away. | สลิปที่บันทึกเรียบร้อยจะลบออกจาก PaiSeN ทันที | SettingsCopy.kt `cleanSlipDeleted` |
| Slips read best in English. | สลิปภาษาอังกฤษอ่านได้ดีที่สุด | SettingsCopy.kt `englishSlipsTitle` |
| The reader is set up for English, so a slip printed in Thai comes back part-read or not at all. Set K PLUS to English and fewer slips end up here. | ตัวอ่านสลิปตั้งไว้สำหรับภาษาอังกฤษ สลิปที่พิมพ์เป็นภาษาไทยจึงอ่านได้ไม่ครบหรืออ่านไม่ได้เลย ตั้ง K PLUS เป็นภาษาอังกฤษ แล้วสลิปที่ต้องมาอยู่ตรงนี้จะน้อยลง | SettingsCopy.kt `englishSlipsText` |
| Keep them for | เก็บไว้นาน | SettingsCopy.kt `keepThemFor` |
| There is no option to keep them for ever. A slip picture carries the account number this app promises never to store, so it always has an end. | ไม่มีตัวเลือกให้เก็บไว้ตลอดไป รูปสลิปมีเลขบัญชีที่แอปนี้สัญญาว่าจะไม่เก็บ จึงต้องมีวันหมดอายุเสมอ | SettingsCopy.kt `noForever` |
| The oldest loses its image today | สลิปที่เก่าที่สุดจะลบรูปวันนี้ | SettingsCopy.kt `oldestLosesImageToday` |
| The oldest has {n} days left | สลิปที่เก่าที่สุดเหลืออีก {n} วัน | SettingsCopy.kt `oldestDaysLeft` |
| Your transactions and their notes, your categories, sub-categories and shop rules, and the groups kept only on this phone, in one backup file you choose the home for. Groups you share online come back when you sign in. It is plain JSON, so it opens on any computer and does not need this app to be readable. | รายการและโน้ต หมวดหมู่ หมวดหมู่ย่อย กฎของร้าน และกลุ่มที่อยู่ในโทรศัพท์เครื่องนี้เท่านั้น รวมอยู่ในไฟล์สำรองไฟล์เดียวที่คุณเลือกที่เก็บเอง กลุ่มที่แชร์ออนไลน์จะกลับมาเมื่อคุณลงชื่อเข้าใช้ ไฟล์เป็น JSON ธรรมดา เปิดได้บนคอมพิวเตอร์ทุกเครื่องโดยไม่ต้องใช้แอปนี้ | SettingsCopy.kt `exportIntro` |
| Saved as {file} | บันทึกเป็น {file} | SettingsCopy.kt `savedAs` |
| There will be a file to keep as soon as your history has something in it. | จะมีไฟล์ให้เก็บทันทีที่ประวัติของคุณมีรายการ | SettingsCopy.kt `exportEmpty` |
| Save a copy | บันทึกสำเนา | SettingsCopy.kt `saveACopy` |
| Nothing is uploaded. The file goes where you put it and nowhere else. | ไม่มีการอัปโหลด ไฟล์จะอยู่ที่ที่คุณเลือกเท่านั้น | SettingsCopy.kt `exportFootnote` |
| Pick a backup file this app exported. You see what it holds first, then what is new is added to what you already have. | เลือกไฟล์สำรองที่ส่งออกจากแอปนี้ คุณจะเห็นสิ่งที่อยู่ในไฟล์ก่อน แล้วรายการใหม่จะถูกเพิ่มเข้ากับสิ่งที่คุณมีอยู่ | SettingsCopy.kt `importIntro` |
| Nothing is deleted. | ไม่มีอะไรถูกลบ | SettingsCopy.kt `nothingDeleted` |
| This adds rows, it never replaces them. A transaction the file and your history both hold is kept once, not twice, and a category this phone already has stays as it is. | การนำเข้าจะเพิ่มรายการ ไม่แทนที่ รายการที่มีทั้งในไฟล์และในประวัติจะเก็บไว้ครั้งเดียว และหมวดหมู่ที่โทรศัพท์เครื่องนี้มีอยู่แล้วจะคงเดิม | SettingsCopy.kt `importCardText` |
| In this file | ในไฟล์นี้ | SettingsCopy.kt `inThisFile` |
| No transactions | ไม่มีรายการ | SettingsCopy.kt `fileHoldsNoTransactions` |
| {n} shop rules | กฎของร้าน {n} ข้อ | SettingsCopy.kt `shopRuleCount` |
| Choose a file | เลือกไฟล์ | SettingsCopy.kt `chooseAFile` |
| A file written by a newer version of the app is refused rather than half read. | ไฟล์ที่เขียนโดยแอปเวอร์ชันใหม่กว่าจะถูกปฏิเสธ แทนที่จะอ่านเพียงครึ่งเดียว | SettingsCopy.kt `importFootnote` |
| Saved to {place} | บันทึกไปที่ {place} แล้ว | SettingsCopy.kt `savedTo` |
| That did not work — nothing was changed | ไม่สำเร็จ ไม่มีอะไรเปลี่ยนแปลง | SettingsCopy.kt `didNotWork` |
| Counting what you have… | กำลังนับสิ่งที่คุณมี… | SettingsCopy.kt `counting` |
| Nothing to export yet | ยังไม่มีอะไรให้ส่งออก | SettingsCopy.kt `nothingToExport` |
| {n} transactions, back to {oldest} | {n} รายการ ย้อนไปถึง {oldest} | SettingsCopy.kt `holding` |
| A day in this app runs midnight to midnight in Bangkok. It does not follow your phone, so travelling never moves a payment onto another day. | วันในแอปนี้เริ่มและจบตอนเที่ยงคืนตามเวลากรุงเทพฯ ไม่ได้ตามเวลาของโทรศัพท์ การเดินทางจึงไม่ย้ายรายการไปอยู่วันอื่น | SettingsCopy.kt `timezoneIntro` |
| Fixed. Changing it would move history between days, and a total that quietly changes is the one thing this app must not do. | ตั้งตายตัว การเปลี่ยนจะย้ายประวัติข้ามวัน และยอดรวมที่เปลี่ยนเองโดยไม่บอกคือสิ่งเดียวที่แอปนี้ต้องไม่ทำ | SettingsCopy.kt `timezoneFixed` |
| The day you are in | วันที่คุณอยู่ตอนนี้ | SettingsCopy.kt `dayYouAreIn` |
| Reports count into the same days, so a week here is seven Bangkok days however many timezones you crossed inside it. | รายงานนับตามวันเดียวกัน หนึ่งสัปดาห์ที่นี่จึงเป็นเจ็ดวันตามเวลากรุงเทพฯ ไม่ว่าคุณจะข้ามเขตเวลากี่เขต | SettingsCopy.kt `timezoneFootnote` |
| Type a sentence in Burmese, Thai or English, and the Add sheet fills itself. You still check it and tap Save. | พิมพ์ประโยคเป็นภาษาพม่า ไทย หรืออังกฤษ แล้วหน้าเพิ่มรายการจะกรอกให้เอง คุณยังต้องตรวจและแตะบันทึก | SettingsCopy.kt `assistantIntro` |
| It runs on this phone. What you type never leaves it, and the model comes from the app store only on Wi-Fi, only when you ask. | ผู้ช่วยทำงานในโทรศัพท์เครื่องนี้ สิ่งที่คุณพิมพ์ไม่ออกไปไหน และโมเดลจะดาวน์โหลดจากสโตร์แอปผ่าน Wi-Fi เมื่อคุณขอเท่านั้น | SettingsCopy.kt `assistantPrivacy` |
| Download on Wi-Fi | ดาวน์โหลดผ่าน Wi-Fi | SettingsCopy.kt `downloadOnWifi` |
| Remove from this phone | ลบออกจากโทรศัพท์เครื่องนี้ | SettingsCopy.kt `removeFromPhone` |
| Short commands like "coffee 60" work without the model. | คำสั่งสั้น ๆ อย่าง "กาแฟ 60" ใช้ได้โดยไม่ต้องมีโมเดล | SettingsCopy.kt `shortCommands` |
| Not in this build yet | ยังไม่มีในเวอร์ชันนี้ | SettingsCopy.kt `modelNotOffered` |
| Not on this phone · {size} | ยังไม่มีในโทรศัพท์ · {size} | SettingsCopy.kt `modelNotInstalled` |
| Downloading · {done} of {size} | กำลังดาวน์โหลด · {done} จาก {size} | SettingsCopy.kt `modelDownloading` |
| On this phone · {size} | อยู่ในโทรศัพท์ · {size} | SettingsCopy.kt `modelReady` |
| What ships inside the app, and the terms it ships under. | สิ่งที่มากับแอป และเงื่อนไขที่ใช้ | SettingsCopy.kt `licencesIntro` |
| The typeface | แบบอักษร | SettingsCopy.kt `coversTypeface` |
| The figures | ตัวเลข | SettingsCopy.kt `coversFigures` |
| The icons | ไอคอน | SettingsCopy.kt `coversIcons` |
| Reading Burmese typed in Zawgyi | การอ่านภาษาพม่าที่พิมพ์ด้วย Zawgyi | SettingsCopy.kt `coversZawgyi` |
| The on-device assistant's model | โมเดลของผู้ช่วยในเครื่อง | SettingsCopy.kt `coversModel` |

## Categories and the category editor

| English | Thai | Where |
|---|---|---|
| Categories | หมวดหมู่ | CategoriesCopy.kt `title` |
| New category | หมวดหมู่ใหม่ | CategoriesCopy.kt `newCategory` |
| Tap to edit. Sub-categories roll up into their category in every total. | แตะเพื่อแก้ไข หมวดหมู่ย่อยจะรวมเข้ากับหมวดหมู่หลักในทุกยอดรวม | CategoriesCopy.kt `intro` |
| Reorder | จัดลำดับ | CategoriesCopy.kt `reorder` |
| No sub-categories | ไม่มีหมวดหมู่ย่อย | CategoriesCopy.kt `noSubCategories` |
| Move {name} up | ย้าย {name} ขึ้น | CategoriesCopy.kt `moveUp` |
| Move {name} down | ย้าย {name} ลง | CategoriesCopy.kt `moveDown` |
| No sub-categories yet | ยังไม่มีหมวดหมู่ย่อย | CategoriesCopy.kt `subCount` |
| {n} sub-categories | หมวดหมู่ย่อย {n} หมวด | CategoriesCopy.kt `subCount` |
| {subs} · {spent} this month | {subs} · เดือนนี้ {spent} | CategoriesCopy.kt `meta` |
| Edit category | แก้ไขหมวดหมู่ | CategoriesCopy.kt `editTitle` |
| Close without saving | ปิดโดยไม่บันทึก | CategoriesCopy.kt `closeWithoutSaving` |
| Change the icon | เปลี่ยนไอคอน | CategoriesCopy.kt `changeIcon` |
| Money out | เงินออก | CategoriesCopy.kt `moneyOut` |
| Money in | เงินเข้า | CategoriesCopy.kt `moneyIn` |
| Decides which sheets offer this category. What is already filed here stays exactly as it was recorded. | กำหนดว่าหน้าไหนจะแสดงหมวดหมู่นี้ รายการที่บันทึกไว้แล้วยังคงเหมือนเดิม | CategoriesCopy.kt `directionHint` |
| Sub-categories | หมวดหมู่ย่อย | CategoriesCopy.kt `subCategories` |
| Removing a sub-category keeps its spending in {name}. Nothing is deleted, it just stops being counted separately. | การลบหมวดหมู่ย่อยจะเก็บรายจ่ายไว้ใน {name} ไม่มีอะไรถูกลบ แค่ไม่นับแยกอีกต่อไป | CategoriesCopy.kt `removingKeepsSpending` |
| Delete category | ลบหมวดหมู่ | CategoriesCopy.kt `deleteCategory` |
| Category name | ชื่อหมวดหมู่ | CategoriesCopy.kt `categoryName` |
| More icons | ไอคอนเพิ่มเติม | CategoriesCopy.kt `moreIcons` |
| Colour | สี | CategoriesCopy.kt `colour` |
| Rename {name} | เปลี่ยนชื่อ {name} | CategoriesCopy.kt `renameSub` |
| Remove {name} | ลบ {name} | CategoriesCopy.kt `removeSub` |
| Add sub-category | เพิ่มหมวดหมู่ย่อย | CategoriesCopy.kt `addSubCategory` |
| Name the sub-category | ตั้งชื่อหมวดหมู่ย่อย | CategoriesCopy.kt `nameTheSub` |
| Add this sub-category | เพิ่มหมวดหมู่ย่อยนี้ | CategoriesCopy.kt `addThisSub` |
| Choose a colour | เลือกสี | CategoriesCopy.kt `chooseColour` |
| Keep this one | ใช้อันนี้ | CategoriesCopy.kt `keepThisOne` |
| Choose an icon | เลือกไอคอน | CategoriesCopy.kt `chooseIcon` |
| Where should these go? | จะย้ายรายการเหล่านี้ไปที่ไหน | CategoriesCopy.kt `whereShouldTheseGo` |
| Keep the category | เก็บหมวดหมู่ไว้ | CategoriesCopy.kt `keepTheCategory` |
| Nothing is filed here, so nothing moves. | ไม่มีรายการในหมวดนี้ จึงไม่มีอะไรต้องย้าย | CategoriesCopy.kt `nothingMoves` |
| You will be asked where its {n} transactions should go. | ระบบจะถามว่าจะย้าย {n} รายการของหมวดนี้ไปที่ไหน | CategoriesCopy.kt `askWhereTheyGo` |
| You will be asked where its {n} shop rules should go. | ระบบจะถามว่าจะย้ายกฎของร้าน {n} ข้อของหมวดนี้ไปที่ไหน | CategoriesCopy.kt `askWhereRulesGo` |
| Its {n} shop rules go there too. | กฎของร้าน {n} ข้อจะย้ายไปด้วย | CategoriesCopy.kt `rulesGoToo` |
| {n} purchases | {n} รายการ | CategoriesCopy.kt `purchases` |

### Colour names

| English | Thai | Where |
|---|---|---|
| Theme colour | สีธีม | CategoriesCopy.kt `colourNames` `default` |
| Teal | เขียวอมฟ้า | CategoriesCopy.kt `colourNames` `teal` |
| Blue | น้ำเงิน | CategoriesCopy.kt `colourNames` `blue` |
| Violet | ม่วง | CategoriesCopy.kt `colourNames` `violet` |
| Rose | ชมพู | CategoriesCopy.kt `colourNames` `rose` |
| Moss | เขียวมอส | CategoriesCopy.kt `colourNames` `moss` |
| Slate | เทาอมฟ้า | CategoriesCopy.kt `colourNames` `slate` |

### Installed category names

| English | Thai | Where |
|---|---|---|
| Food & drink | อาหาร | CategoriesCopy.kt `seedNames` `food` |
| Groceries | ของชำ | CategoriesCopy.kt `seedNames` `groceries` |
| Transport | ค่าเดินทาง | CategoriesCopy.kt `seedNames` `transport` |
| Bills | บิล | CategoriesCopy.kt `seedNames` `bills` |
| Shopping | ช้อปปิ้ง | CategoriesCopy.kt `seedNames` `shopping` |
| Other | อื่น ๆ | CategoriesCopy.kt `seedNames` `other` |
| Income | รายรับ | CategoriesCopy.kt `seedNames` `income` |

## Money in from slips (#26, new, 16 Nov)

| English | Thai | Where |
| --- | --- | --- |
| Money in from | รับเงินจาก | IncomeSlipCopy.kt `moneyInFrom` |
| Your name on slips | ชื่อของคุณบนสลิป | IncomeSlipCopy.kt `ownerRow` |
| Not learned yet | ยังไม่รู้จัก | IncomeSlipCopy.kt `ownerUnknown` |
| As K PLUS prints you as the sender, like MR. THAW Z. A slip that names you as the recipient is filed as money in. Leave it empty and PaiSeN learns it again from your own slips. | ตามที่ K PLUS พิมพ์ชื่อคุณเป็นผู้โอน เช่น MR. THAW Z สลิปที่ระบุคุณเป็นผู้รับจะบันทึกเป็นเงินเข้า เว้นว่างไว้ PaiSeN จะเรียนรู้ชื่อใหม่จากสลิปของคุณเอง | IncomeSlipCopy.kt `ownerHint` |
| Save | บันทึก | IncomeSlipCopy.kt `save` |
| Cancel | ยกเลิก | IncomeSlipCopy.kt `cancel` |

## Home currency and home time zone (16 Nov, not yet checked)

| English | Thai | Where |
|---|---|---|
| Your money | สกุลเงินของคุณ | `CurrencyCopy` yourMoney |
| Change | เปลี่ยน | `CurrencyCopy` change |
| Home currency | สกุลเงินหลัก | `CurrencyCopy` homeTitle |
| Every total counts in this currency. Days start at midnight, {zone} time. | ยอดรวมทั้งหมดนับเป็นสกุลนี้ วันเริ่มตอนเที่ยงคืนตามเวลา {zone} | `CurrencyCopy` homeIntro |
| Your ledger already has entries, so changing this converts every amount. | มีรายการอยู่แล้ว การเปลี่ยนจะแปลงทุกจำนวนเงิน | `CurrencyCopy` homeHasRows |
| Change to {code}? | เปลี่ยนเป็น {code} ไหม | `CurrencyCopy` convertTitle |
| Every amount converts at this rate. Each entry keeps its old amount and the rate beside it. | ทุกจำนวนเงินจะแปลงตามอัตรานี้ แต่ละรายการเก็บจำนวนเดิมและอัตราไว้ข้างๆ | `CurrencyCopy` convertBody |
| {from} becomes {to} | {from} เป็น {to} | `CurrencyCopy` convertExample |
| Export a backup first | สำรองข้อมูลก่อน | `CurrencyCopy` backupFirst |
| Nothing changed. The entries could not be converted. | ไม่มีอะไรเปลี่ยน แปลงรายการไม่ได้ | `CurrencyCopy` convertFailed |
| This file counts in {file}. This phone counts in {here}. | ไฟล์นี้นับเป็น {file} แต่โทรศัพท์นี้นับเป็น {here} | `PhrasesCopy` backupOtherHome |
| Timezone screen: "Bangkok" is now the home zone's place | กรุงเทพฯ / ย่างกุ้ง | `SettingsCopy` zonePlace |
| A {code} slip needs a rate | สลิป {code} ต้องใส่อัตราแลกเปลี่ยน | `CurrencyCopy` rateNeeded |
| Rate needed | ต้องใส่อัตรา | `CurrencyCopy` reasonRate |
| one needs a rate / {n} need a rate | ต้องใส่อัตรา {n} ใบ | `CurrencyCopy` slipsNeedRate |
