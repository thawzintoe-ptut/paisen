# Thai review: shared words, the shell, dates and the Language screen

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in.

Choices to check first:

- Tone: neutral polite, no ครับ or ค่ะ, so the app has no gender. The reader is คุณ, or the subject is left out.
- Save is บันทึก and a note is โน้ต, because บันทึก means both.
- The Shared tab and a shared group are กลุ่ม.
- Years are Buddhist Era (2569 for 2026). Digits are 0–9. Amounts keep ฿.
- `{language}` is the language's own name ("English", "မြန်မာ", "ไทย"), so it has a space on each side.

## Shared words

| English | Thai | Where |
|---|---|---|
| Save | บันทึก | CommonCopy.kt `save` |
| Done | เสร็จ | CommonCopy.kt `done` |
| Cancel | ยกเลิก | CommonCopy.kt `cancel` |
| Delete | ลบ | CommonCopy.kt `delete` |
| Keep it | เก็บไว้ | CommonCopy.kt `keepIt` |
| Undo | เลิกทำ | CommonCopy.kt `undo` |
| Edit | แก้ไข | CommonCopy.kt `edit` |
| Back | กลับ | CommonCopy.kt `back` |
| Close | ปิด | CommonCopy.kt `close` |
| Try again | ลองอีกครั้ง | CommonCopy.kt `tryAgain` |
| Expense | รายจ่าย | CommonCopy.kt `expense` |
| Income | รายรับ | CommonCopy.kt `income` |
| Amount | จำนวนเงิน | CommonCopy.kt `amount` |
| Category | หมวดหมู่ | CommonCopy.kt `category` |
| Sub-category | หมวดหมู่ย่อย | CommonCopy.kt `subCategory` |
| Note | โน้ต | CommonCopy.kt `note` |
| Time | เวลา | CommonCopy.kt `time` |
| Total | รวม | CommonCopy.kt `total` |
| Today | วันนี้ | CommonCopy.kt `today` |
| Yesterday | เมื่อวาน | CommonCopy.kt `yesterday` |
| This week | สัปดาห์นี้ | CommonCopy.kt `thisWeek` |
| This month | เดือนนี้ | CommonCopy.kt `thisMonth` |
| You | คุณ | CommonCopy.kt `you` |
| Discard changes? | ทิ้งสิ่งที่แก้ไขไหม | CommonCopy.kt `discardTitle` |
| What you changed here will be lost. | สิ่งที่คุณแก้ไขตรงนี้จะหายไป | CommonCopy.kt `discardBody` |
| Keep editing | แก้ไขต่อ | CommonCopy.kt `keepEditing` |
| Discard | ทิ้ง | CommonCopy.kt `discard` |

## The shell and shared components

| English | Thai | Where |
|---|---|---|
| Today | วันนี้ | ShellCopy.kt `tabToday` |
| Reports | รายงาน | ShellCopy.kt `tabReports` |
| Shared | กลุ่ม | ShellCopy.kt `tabShared` |
| Settings | ตั้งค่า | ShellCopy.kt `tabSettings` |
| Add | เพิ่ม | ShellCopy.kt `add` |
| Add an expense by hand | เพิ่มรายจ่ายเอง | ShellCopy.kt `addByHand` |
| Add a note | เพิ่มโน้ต | ShellCopy.kt `addANote` |
| What this was for | จ่ายไปกับอะไร | ShellCopy.kt `notePlaceholder` |
| Keep this note | เก็บโน้ตนี้ | ShellCopy.kt `keepNote` |
| Date & time | วันที่และเวลา | ShellCopy.kt `dateAndTime` |
| Now | ตอนนี้ | ShellCopy.kt `now` |
| Next | ถัดไป | ShellCopy.kt `next` |
| Picture gone | ไม่มีรูปแล้ว | ShellCopy.kt `pictureGone` |
| Open the slip picture | เปิดรูปสลิป | ShellCopy.kt `openSlipPicture` |
| Slip picture | รูปสลิป | ShellCopy.kt `slipPicture` |
| Close the slip picture | ปิดรูปสลิป | ShellCopy.kt `closeSlipPicture` |
| Delete the last digit | ลบตัวเลขตัวสุดท้าย | ShellCopy.kt `deleteLastDigit` |
| {language} isn't available on this phone right now. Type it instead. | ตอนนี้โทรศัพท์เครื่องนี้ฟังเสียงพูด {language} ไม่ได้ พิมพ์แทนได้เลย | ShellCopy.kt `voiceUnavailable` |
| {language} voice needs the internet. Check your connection, or type it. | เสียงพูด {language} ต้องใช้อินเทอร์เน็ต ตรวจสอบการเชื่อมต่อ หรือพิมพ์แทน | ShellCopy.kt `voiceNeedsInternet` |
| The phone's speech service is busy. Try again. | ระบบฟังเสียงของโทรศัพท์ไม่ว่าง ลองอีกครั้ง | ShellCopy.kt `speechBusy` |
| Could not hear that. Type it instead. | ไม่ได้ยิน พิมพ์แทนได้เลย | ShellCopy.kt `couldNotHear` |
| This phone has no microphone it can use right now. Type it instead. | ตอนนี้โทรศัพท์เครื่องนี้ไม่มีไมโครโฟนที่ใช้ได้ พิมพ์แทนได้เลย | ShellCopy.kt `noMicrophone` |

### Category icon names (read by TalkBack and VoiceOver)

| Icon | Thai | Where |
|---|---|---|
| restaurant | อาหาร | ShellCopy.kt `iconNames` |
| local_grocery_store | ของชำ | ShellCopy.kt `iconNames` |
| shopping_bag | ช้อปปิ้ง | ShellCopy.kt `iconNames` |
| store | ร้านค้า | ShellCopy.kt `iconNames` |
| directions_bus | ค่าเดินทาง | ShellCopy.kt `iconNames` |
| place | สถานที่ | ShellCopy.kt `iconNames` |
| public | ท่องเที่ยว | ShellCopy.kt `iconNames` |
| local_activity | ตั๋ว | ShellCopy.kt `iconNames` |
| home | บ้าน | ShellCopy.kt `iconNames` |
| key | ค่าเช่าบ้าน | ShellCopy.kt `iconNames` |
| receipt_long | บิล | ShellCopy.kt `iconNames` |
| bolt | ค่าน้ำค่าไฟ | ShellCopy.kt `iconNames` |
| lightbulb | ค่าไฟ | ShellCopy.kt `iconNames` |
| local_fire_department | แก๊ส | ShellCopy.kt `iconNames` |
| wifi | อินเทอร์เน็ต | ShellCopy.kt `iconNames` |
| smartphone | โทรศัพท์ | ShellCopy.kt `iconNames` |
| handyman | ค่าซ่อม | ShellCopy.kt `iconNames` |
| monitor_heart | สุขภาพ | ShellCopy.kt `iconNames` |
| emoji_events | กีฬา | ShellCopy.kt `iconNames` |
| extension | งานอดิเรก | ShellCopy.kt `iconNames` |
| music_note | ดนตรี | ShellCopy.kt `iconNames` |
| movie | ภาพยนตร์ | ShellCopy.kt `iconNames` |
| menu_book | หนังสือ | ShellCopy.kt `iconNames` |
| school | การศึกษา | ShellCopy.kt `iconNames` |
| brush | งานฝีมือ | ShellCopy.kt `iconNames` |
| computer | คอมพิวเตอร์ | ShellCopy.kt `iconNames` |
| content_cut | ตัดผม | ShellCopy.kt `iconNames` |
| auto_awesome | ให้รางวัลตัวเอง | ShellCopy.kt `iconNames` |
| card_giftcard | ของขวัญ | ShellCopy.kt `iconNames` |
| group | ครอบครัว | ShellCopy.kt `iconNames` |
| work | งาน | ShellCopy.kt `iconNames` |
| apartment | ออฟฟิศ | ShellCopy.kt `iconNames` |
| payments | รายรับ | ShellCopy.kt `iconNames` |
| cash | เงินสด | ShellCopy.kt `iconNames` |
| account_balance_wallet | กระเป๋าเงิน | ShellCopy.kt `iconNames` |
| account_balance | ธนาคาร | ShellCopy.kt `iconNames` |
| verified_user | ประกัน | ShellCopy.kt `iconNames` |
| calendar_month | จ่ายรายเดือน | ShellCopy.kt `iconNames` |
| label | ป้ายทั่วไป | ShellCopy.kt `iconNames` |
| more_horiz | อื่น ๆ | ShellCopy.kt `iconNames` |

## Dates

| English | Thai | Where |
|---|---|---|
| Monday … Sunday | วันจันทร์, วันอังคาร, วันพุธ, วันพฤหัสบดี, วันศุกร์, วันเสาร์, วันอาทิตย์ | CalendarCopy.kt `weekdays` |
| Mon … Sun | จ., อ., พ., พฤ., ศ., ส., อา. | CalendarCopy.kt `weekdaysShort` |
| January … December | มกราคม, กุมภาพันธ์, มีนาคม, เมษายน, พฤษภาคม, มิถุนายน, กรกฎาคม, สิงหาคม, กันยายน, ตุลาคม, พฤศจิกายน, ธันวาคม | CalendarCopy.kt `months` |
| Jan … Dec | ม.ค., ก.พ., มี.ค., เม.ย., พ.ค., มิ.ย., ก.ค., ส.ค., ก.ย., ต.ค., พ.ย., ธ.ค. | CalendarCopy.kt `monthsShort` |
| Mon 16 Nov 2026 (receipt heading) | จ. 16 พ.ย. 2569 | DayLabel.kt `yearLabel` |

## The Language screen

| English | Thai | Where |
|---|---|---|
| Language | ภาษา | LanguageCopy.kt `title` |
| The language PaiSeN is written in. Your own category names and notes stay as you typed them. | ภาษาที่ใช้แสดงผลใน PaiSeN ชื่อหมวดหมู่และโน้ตที่คุณตั้งเองจะยังคงเป็นแบบที่คุณพิมพ์ไว้ | LanguageCopy.kt `intro` |
| Show PaiSeN in | แสดง PaiSeN เป็นภาษา | LanguageCopy.kt `groupTitle` |
| (the language's own name) | ไทย | Language.kt `Thai` |

## Phrases: row names, times, comparisons, backups and repeats

| English | Thai | Where |
|---|---|---|
| Unknown shop | ร้านที่ไม่รู้จัก | PhrasesCopy.kt `unknownShop` |
| Uncategorised | ไม่มีหมวดหมู่ | PhrasesCopy.kt `uncategorised` |
| Unknown category | หมวดหมู่ที่ไม่รู้จัก | PhrasesCopy.kt `unknownCategory` |
| Unsorted | ไม่มีหมวดหมู่ย่อย | PhrasesCopy.kt `unsorted` |
| auto | อัตโนมัติ | PhrasesCopy.kt `auto` |
| added by hand | เพิ่มเอง | PhrasesCopy.kt `addedByHand` |
| {date}, {time} | {date} {time} | PhrasesCopy.kt `dateAndTime` |
| {a}, {b} and {c} | {a}, {b} และ {c} | PhrasesCopy.kt `listComma`, `listAnd` |
| under a minute | ไม่ถึงนาที | PhrasesCopy.kt `underAMinute` |
| {n} min | {n} นาที | PhrasesCopy.kt `minutes` |
| 1 hour / {n} hours | {n} ชั่วโมง | PhrasesCopy.kt `hours` |
| 1 day / {n} days | {n} วัน | PhrasesCopy.kt `days` |
| {amount} less than a usual {weekday} | น้อยกว่า{weekday}ปกติ {amount} | PhrasesCopy.kt `lessThanUsual` |
| {amount} more than a usual {weekday} | มากกว่า{weekday}ปกติ {amount} | PhrasesCopy.kt `moreThanUsual` |
| the same as a usual {weekday} | เท่ากับ{weekday}ปกติ | PhrasesCopy.kt `sameAsUsual` |
| {percent}% under a usual {weekday} | ต่ำกว่า{weekday}ปกติ {percent}% | PhrasesCopy.kt `percentUnderUsual` |
| {percent}% over a usual {weekday} | สูงกว่า{weekday}ปกติ {percent}% | PhrasesCopy.kt `percentOverUsual` |
| the same as {baseline} | เท่ากับ{baseline} | PhrasesCopy.kt `sameAsBaseline` |
| {percent}% more than {baseline} | มากกว่า{baseline} {percent}% | PhrasesCopy.kt `percentMoreThan` |
| {percent}% less than {baseline} | น้อยกว่า{baseline} {percent}% | PhrasesCopy.kt `percentLessThan` |
| {digits} baht (spoken) | {digits} บาท | PhrasesCopy.kt `spokenBaht` |
| That file held no transactions | ไฟล์นี้ไม่มีรายการเลย | PhrasesCopy.kt `restoreEmpty` |
| Nothing new — your history already held all {n} | ไม่มีรายการใหม่ ประวัติของคุณมีครบทั้ง {n} รายการแล้ว | PhrasesCopy.kt `restoreNothingNew` |
| Added {n} transactions | เพิ่ม {n} รายการแล้ว | PhrasesCopy.kt `restoreAdded` |
| {n} already here | มีอยู่แล้ว {n} รายการ | PhrasesCopy.kt `restoreAlreadyHere` |
| {n} shared groups | {n} กลุ่ม | PhrasesCopy.kt `restoreGroups` |
| Added {what} | เพิ่ม {what} แล้ว | PhrasesCopy.kt `restoreAddedOnly` |
| {n} lost their category | {n} รายการไม่มีหมวดหมู่แล้ว | PhrasesCopy.kt `restoreLostCategory` |
| This is not a file this app can read | แอปนี้อ่านไฟล์นี้ไม่ได้ | PhrasesCopy.kt `backupNotReadable` |
| This file was written by a different version of the app (format {n}) | ไฟล์นี้มาจากแอปเวอร์ชันอื่น (รูปแบบ {n}) | PhrasesCopy.kt `backupOtherVersion` |
| The file holds an unknown {field}: {value} | ในไฟล์มี {field} ที่ไม่รู้จัก: {value} | PhrasesCopy.kt `backupUnknownValue` |
| Every {weekday} | ทุก{weekday} | PhrasesCopy.kt `everyWeekday` |
| Every other {weekday} | ทุก{weekday} เว้นสัปดาห์ | PhrasesCopy.kt `everyOtherWeekday` |
| The {15th} of each month | ทุก{วันที่ 15} ของเดือน | PhrasesCopy.kt `monthlyOn` |
| {day} {month} each year | ทุกปี วันที่ {day} {month} | PhrasesCopy.kt `yearlyOn` |
| 15th | วันที่ 15 | PhrasesCopy.kt `ordinal` |
