# Thai review: Today, Add and the day receipt

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in. The choices in `th-shell.md` apply here too.

Please check these against a Thai phone:

- The Settings steps in `allowAllAndroidSteps` and `allowAllIosSteps` must match the labels a Thai phone
  shows: สิทธิ์ → รูปภาพและวิดีโอ → อนุญาตทั้งหมดเสมอ on Android, รูปภาพ → การเข้าถึงทั้งหมด on iPhone.
- The receipt can show any day, not only today. Does ไม่มีรายจ่ายในวันนี้ read right for a past day?
- `{since}` is a span from `th-shell.md` ("5 นาที", "ไม่ถึงนาที"), so "นำเข้าเมื่อ 5 นาทีที่แล้ว".

## Today

| English | Thai | Where |
|---|---|---|
| Spent today | ใช้ไปวันนี้ | HomeCopy.kt `spentToday` |
| today | วันนี้ | HomeCopy.kt `spanToday` |
| Imported {since} ago · {n} filed {span} | นำเข้าเมื่อ {since}ที่แล้ว · {span}บันทึก {n} รายการ | HomeCopy.kt `imported` |
| No slips imported yet | ยังไม่ได้นำเข้าสลิป | HomeCopy.kt `noSlipsYet` |
| No slips seen for {since} | ไม่พบสลิปมา {since}แล้ว | HomeCopy.kt `noSlipsFor` |
| New slips can't be read | อ่านสลิปใหม่ไม่ได้ | HomeCopy.kt `scansFailing` |
| Only photos you picked are read | อ่านเฉพาะรูปที่คุณเลือก | HomeCopy.kt `onlyPickedRead` |
| New slips stay hidden until you pick them or allow all photos. | สลิปใหม่จะยังไม่แสดงจนกว่าคุณจะเลือกรูป หรืออนุญาตให้เข้าถึงรูปทั้งหมด | HomeCopy.kt `onlyPickedBody` |
| Pick photos | เลือกรูป | HomeCopy.kt `pickPhotos` |
| Allow all photos | อนุญาตให้เข้าถึงรูปทั้งหมด | HomeCopy.kt `allowAllTitle` |
| PaiSeN can't see new photos, so new slips stay hidden. | PaiSeN มองไม่เห็นรูปใหม่ สลิปใหม่จึงยังไม่แสดง | HomeCopy.kt `allowAllBody` |
| On the next screen tap Permissions → Photos and videos → Always allow all. | ในหน้าถัดไป แตะ สิทธิ์ → รูปภาพและวิดีโอ → อนุญาตทั้งหมดเสมอ | HomeCopy.kt `allowAllAndroidSteps` |
| On the next screen tap Photos → Full Access. | ในหน้าถัดไป แตะ รูปภาพ → การเข้าถึงทั้งหมด | HomeCopy.kt `allowAllIosSteps` |
| Open settings | เปิดการตั้งค่า | HomeCopy.kt `openSettings` |
| Not now | ไว้ทีหลัง | HomeCopy.kt `notNow` |
| Totals below may be incomplete | ยอดรวมด้านล่างอาจยังไม่ครบ | HomeCopy.kt `totalsIncomplete` |
| Fix | แก้ไข | HomeCopy.kt `fix` |
| Net | สุทธิ | HomeCopy.kt `net` |
| A quiet day so far. | วันนี้ยังไม่ได้ใช้จ่ายอะไร | HomeCopy.kt `quietDay` |
| New slips in your chosen folders show up here on their own. Or add one by hand. | สลิปใหม่ในโฟลเดอร์ที่คุณเลือกจะแสดงที่นี่เอง หรือจะเพิ่มเองก็ได้ | HomeCopy.kt `emptyBody` |
| Add a cash expense | เพิ่มรายจ่ายเงินสด | HomeCopy.kt `addCash` |
| Search all purchases in History | ค้นหารายการทั้งหมดในประวัติ | HomeCopy.kt `searchHistory` |
| Purchases | รายการใช้จ่าย | HomeCopy.kt `purchases` |
| Saved today | บันทึกวันนี้ | HomeCopy.kt `savedToday` |
| Each one counts on the day printed on its slip, not the day you saved it. | แต่ละรายการนับตามวันที่บนสลิป ไม่ใช่วันที่คุณบันทึก | HomeCopy.kt `savedTodayNote` |
| {n} more in History | อีก {n} รายการในประวัติ | HomeCopy.kt `moreInHistory` |
| no run yet | ยังไม่ได้ตรวจ | HomeCopy.kt `noRunYet` |
| checked {since} ago | ตรวจเมื่อ {since}ที่แล้ว | HomeCopy.kt `checkedAgo` |
| No folder watched · pick one in Settings | ไม่ได้ติดตามโฟลเดอร์ใด · เลือกได้ในตั้งค่า | HomeCopy.kt `noFolderWatched` |
| Watching {folder} · {checked} | ติดตาม {folder} · {checked} | HomeCopy.kt `watchingOne` |
| Watching {n} folders · {checked} | ติดตาม {n} โฟลเดอร์ · {checked} | HomeCopy.kt `watchingMany` |
| {n} slips to check | สลิปที่ต้องตรวจ {n} ใบ | HomeCopy.kt `slipsToCheck` |
| {n} new shops | ร้านใหม่ {n} ร้าน | HomeCopy.kt `newShops` |
| {n} unreadable amounts | จำนวนเงินที่อ่านไม่ได้ {n} รายการ | HomeCopy.kt `unreadableAmounts` |
| {n} unread slips | สลิปที่ยังไม่ได้อ่าน {n} ใบ | HomeCopy.kt `unreadSlips` |
| Good morning | อรุณสวัสดิ์ | HomeCopy.kt `goodMorning` |
| Yesterday came to {spent} — {comparison}. | เมื่อวานใช้ไป {spent} — {comparison} | HomeCopy.kt `yesterdayCameTo` |
| Your day, wrapped | สรุปวันนี้ | HomeCopy.kt `dayWrapped` |
| {n} purchases, {total}. Tap for the receipt. | ใช้จ่าย {n} รายการ รวม {total} แตะเพื่อดูใบเสร็จ | HomeCopy.kt `wrapLine` |
| Your week | สัปดาห์ของคุณ | HomeCopy.kt `weekLead` |
| Last week came to {spent}{comparison}. | สัปดาห์ที่แล้วใช้ไป {spent}{comparison} | HomeCopy.kt `weekCameTo` |
| , {amount} more than a usual week | มากกว่าสัปดาห์ปกติ {amount} | HomeCopy.kt `weekMore` |
| , {amount} less than a usual week | น้อยกว่าสัปดาห์ปกติ {amount} | HomeCopy.kt `weekLess` |
| , the same as a usual week | เท่ากับสัปดาห์ปกติ | HomeCopy.kt `weekSame` |
| {category} rose most, {amount} more, mostly at {shop} and {shop}. | หมวด {category} เพิ่มขึ้นมากที่สุด {amount} ส่วนใหญ่ที่ {shop} และ {shop} | HomeCopy.kt `weekRise` |

## Weekly target (#21)

Plain amounts only: the target never says "over budget" and is never red.

| English | Thai | Where |
|---|---|---|
| Weekly target | เป้าหมายรายสัปดาห์ | TargetCopy.kt `fieldLabel` |
| Spend less than this in a week. Leave it empty for no target. | ใช้จ่ายให้น้อยกว่านี้ในหนึ่งสัปดาห์ เว้นว่างไว้ถ้าไม่ต้องการเป้าหมาย | TargetCopy.kt `fieldHint` |
| A usual week here: {amount} | สัปดาห์ปกติของหมวดนี้: {amount} | TargetCopy.kt `usualWeek` |
| Saving replaces the {amount} target on {category}. There is one target at a time. | การบันทึกจะแทนที่เป้าหมาย {amount} ของ {category} ตั้งเป้าหมายได้ครั้งละหนึ่งรายการ | TargetCopy.kt `replaces` |
| {category}: {spent} of {target} this week | {category}: สัปดาห์นี้ {spent} จาก {target} | TargetCopy.kt `weekLine` |
| {category} stayed under its {target} target. | {category} อยู่ต่ำกว่าเป้าหมาย {target} | TargetCopy.kt `stayedUnder` |
| {category} went {over} past its {target} target. | {category} เกินเป้าหมาย {target} ไป {over} | TargetCopy.kt `wentPast` |
| of {target} | จากเป้าหมาย {target} | TargetCopy.kt `ofTarget` |

## Add and Quick add

| English | Thai | Where |
|---|---|---|
| Pick a category to save this | เลือกหมวดหมู่เพื่อบันทึก | AddCopy.kt `pickCategoryHint` |
| Type an amount to save this | ใส่จำนวนเงินเพื่อบันทึก | AddCopy.kt `typeAmountHint` |
| Type it: coffee 60 | พิมพ์เลย: กาแฟ 60 | AddCopy.kt `askPlaceholder` |
| Listening… | กำลังฟัง… | AddCopy.kt `listening` |
| Say it | พูด | AddCopy.kt `sayIt` |
| Stop listening | หยุดฟัง | AddCopy.kt `stopListening` |
| Voice language | ภาษาที่พูด | AddCopy.kt `voiceLanguageTitle` |
| Voice language: {language} | ภาษาที่พูด: {language} | AddCopy.kt `voiceLanguageLabel` |
| On this phone. Works offline. | อยู่ในโทรศัพท์ ใช้ได้แม้ไม่มีอินเทอร์เน็ต | AddCopy.kt `voiceOnPhone` |
| {language} is heard online | เสียงพูด {language} ฟังผ่านออนไลน์ | AddCopy.kt `onlineAskTitle` |
| Listen | ฟัง | AddCopy.kt `listenOnline` |
| Type instead | พิมพ์แทน | AddCopy.kt `typeInstead` |
| Save {amount} | บันทึก {amount} | AddCopy.kt `saveAmount` |
| {language} voice isn't available on this phone. Type it instead. | โทรศัพท์เครื่องนี้ใช้เสียงพูด {language} ไม่ได้ พิมพ์แทนได้เลย | AddCopy.kt `voiceUnavailable` |
| {language} is recognised online by Google, so your voice is sent to Google. | Google ฟังเสียงพูด {language} ทางออนไลน์ เสียงของคุณจึงถูกส่งไปที่ Google | AddCopy.kt `voiceOnline` |
| {language} voice isn't on this phone yet. Download it once and it works offline. | โทรศัพท์เครื่องนี้ยังไม่มีเสียงพูด {language} ดาวน์โหลดครั้งเดียว แล้วใช้ได้แม้ไม่มีอินเทอร์เน็ต | AddCopy.kt `voiceDownloadable` |
| Download {language} | ดาวน์โหลด {language} | AddCopy.kt `downloadVoice` |
| Downloading {language}. The mic appears when it's ready. | กำลังดาวน์โหลด {language} ไมค์จะแสดงเมื่อพร้อม | AddCopy.kt `voiceDownloading` |
| The phone couldn't download {language}. Try again on Wi-Fi, or type it. | โทรศัพท์ดาวน์โหลด {language} ไม่ได้ ลองอีกครั้งผ่าน Wi-Fi หรือพิมพ์แทน | AddCopy.kt `voiceDownloadFailed` |
| Could not read that. Fill it in below. | อ่านไม่ออก กรอกด้านล่างได้เลย | AddCopy.kt `couldNotRead` |
| Could not read the amount or the category. Fill them in below. | อ่านจำนวนเงินและหมวดหมู่ไม่ออก กรอกด้านล่างได้เลย | AddCopy.kt `notRead` |
| Could not read the amount. Type it below. | อ่านจำนวนเงินไม่ออก พิมพ์ด้านล่างได้เลย | AddCopy.kt `notRead` |
| Could not read the category. Pick one below. | อ่านหมวดหมู่ไม่ออก เลือกด้านล่างได้เลย | AddCopy.kt `notRead` |
| Didn't catch that. Try again or type it. | ฟังไม่ทัน ลองอีกครั้งหรือพิมพ์แทน | AddCopy.kt `didNotCatch` |
| Send | ส่ง | AddCopy.kt `send` |
| Allow the microphone in Settings to say it. Typing still works. | อนุญาตไมโครโฟนในการตั้งค่าเพื่อพูด ยังพิมพ์ได้ตามปกติ | AddCopy.kt `allowMicrophone` |
| Saved {amount} · {category} | บันทึก {amount} แล้ว · {category} | AddCopy.kt `saved` |
| Spent {amount} today | วันนี้ใช้ไป {amount} | AddCopy.kt `spentToday` |
| Spent {amount} this week | สัปดาห์นี้ใช้ไป {amount} | AddCopy.kt `spentThisWeek` |
| Spent {amount} this month | เดือนนี้ใช้ไป {amount} | AddCopy.kt `spentThisMonth` |
| {n} rows match | ตรงกัน {n} รายการ | AddCopy.kt `rowsMatch` |

## The day receipt

| English | Thai | Where |
|---|---|---|
| BANGKOK | กรุงเทพฯ | ReceiptCopy.kt `city` |
| Other | อื่น ๆ | ReceiptCopy.kt `other` |
| Nothing was spent on this day. | ไม่มีรายจ่ายในวันนี้ | ReceiptCopy.kt `nothingSpent` |
| TOTAL | รวม | ReceiptCopy.kt `total` |
| Kept on this phone. Nothing was sent anywhere. | เก็บไว้ในโทรศัพท์เครื่องนี้ ไม่ได้ส่งไปที่ไหนเลย | ReceiptCopy.kt `keptOnPhone` |

## Weekly note and Planned (#22, new, 16 Nov; Playful lines need the closest read)

| English | Thai | Where |
| --- | --- | --- |
| {category} rose {amount}, mostly at {shop}. | {category}เพิ่มขึ้น {amount} ส่วนใหญ่ที่ {shop} | NoteCopy.kt `rose` (Plain) |
| {category} rose {amount} after the planned {planned}. | {category}เพิ่ม {amount} ไม่นับ {planned} ที่วางแผนไว้ | `roseAfterPlanned` (Plain) |
| {amount} under a usual week. | ต่ำกว่าสัปดาห์ปกติ {amount} | `under` (Plain) |
| Right on a usual week. | เท่ากับสัปดาห์ปกติ | `same` (Plain) |
| {shop} had a good week: {category} ran {amount} above usual. | สัปดาห์นี้ {shop} ยิ้มเลย {category}เกินปกติไป {amount} | `rose` (Playful) |
| {planned} of {category} was planned. Only {amount} sneaked in. | {planned} วางแผนไว้แล้ว แอบเพิ่มมาแค่ {amount} | `roseAfterPlanned` (Playful) |
| {amount} under a usual week. Your wallet noticed. | ต่ำกว่าปกติ {amount} กระเป๋าตังค์ยิ้มแล้ว | `under` (Playful) |
| Right on a usual week. Steady. | เท่าเดิมเป๊ะ สม่ำเสมอดี | `same` (Playful) |
| Planned / Left out of weekly notes | วางแผนไว้ / ไม่นับในบันทึกรายสัปดาห์ | `planned`, `plannedHint` |
| That's not right / What's off? | ไม่ถูกต้อง / อะไรไม่ถูก | `notRight`, `whatsOff` |
| Some of it was planned / A category is wrong / Just hide this week | บางส่วนวางแผนไว้แล้ว / หมวดหมู่ไม่ถูก / ซ่อนสัปดาห์นี้ | sheet choices |
| Note tone / Plain / Playful | น้ำเสียงของบันทึก / เรียบ ๆ / สนุก ๆ | `toneTitle`, `tonePlain`, `tonePlayful` |

## Design v2 (#68, #69), not reviewed yet

| English | Thai | Where |
|---|---|---|
| Photo access is off, so new slips wait until you allow it. | ปิดการเข้าถึงรูปอยู่ สลิปใหม่จะรอจนกว่าคุณจะอนุญาต | `HomeCopy` stopAccessOff |
| The photo library didn't answer. The next scan tries again. | คลังรูปไม่ตอบสนอง การสแกนครั้งถัดไปจะลองใหม่ | `HomeCopy` stopNoAnswer |
| Purchases already here still count. | รายการที่มีอยู่แล้วยังนับตามเดิม | `HomeCopy` stillCounts |
| Reading new slips | กำลังอ่านสลิปใหม่ | `HomeCopy` readingSlips |

## Today has one job (Hick's law pass, 22 Nov), not reviewed yet

| English | Thai | Where |
|---|---|---|
| Today by category: {parts}. Open Reports. | วันนี้ตามหมวด: {parts} เปิดรายงาน | `HomeCopy` byCategory (spoken only) |

Gone from `HomeCopy`: the Week and Month tabs and their "Spent this week / month", "this week / month" and "A quiet week / month so far." lines, and the "Compared" card title. Reports owns the week and the month now.
