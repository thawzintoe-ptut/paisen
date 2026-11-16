# Thai review: History, Reports and a transaction's record

There is no Thai design, so every Thai string is here. A native reader should check each one. Paths are
under `core/locale/src/commonMain/kotlin/com/ptut/expense/locale/`. `{name}` marks a value the app puts
in. The choices in `th-shell.md` apply here too.

Choices to check in this area:

- The Reports screen title is การใช้จ่าย; its tab in the bar is รายงาน (`th-shell.md`).
- `{periods}` is a list such as "วันนี้, สัปดาห์นี้ และ เดือนนี้", built from the shared words.
- The baselines that start with a number begin with ช่วง, so a sentence reads "มากกว่าช่วง 3 เดือนก่อนหน้า 12%".
- Not translated, on purpose: the period tabs (W, M, 3M, 6M, Y) and the year chart's month letters
  (J F M A M J J A S O N D). Thai short forms do not fit those fixed widths. Should they change?

## History

| English | Thai | Where |
|---|---|---|
| History | ประวัติ | HistoryCopy.kt `title` |
| Search purchases | ค้นหารายการ | HistoryCopy.kt `search` |
| Filters | ตัวกรอง | HistoryCopy.kt `filters` |
| Show | แสดง | HistoryCopy.kt `show` |
| All | ทั้งหมด | HistoryCopy.kt `filterAll` |
| Auto-filed | บันทึกอัตโนมัติ | HistoryCopy.kt `filterAuto` |
| By hand | เพิ่มเอง | HistoryCopy.kt `filterByHand` |
| Edited | แก้ไขแล้ว | HistoryCopy.kt `filterEdited` |
| Nothing recorded yet | ยังไม่มีรายการ | HistoryCopy.kt `emptyHeadline` |
| Slips file themselves as they arrive, and anything you add by hand lands here too. | สลิปจะบันทึกเองเมื่อเข้ามา และรายการที่คุณเพิ่มเองก็จะแสดงที่นี่ด้วย | HistoryCopy.kt `emptyBody` |
| Your history fills itself as slips arrive | ประวัติจะเพิ่มขึ้นเองเมื่อมีสลิปเข้ามา | HistoryCopy.kt `emptyStatus` |
| No transactions match | ไม่มีรายการที่ตรงกัน | HistoryCopy.kt `noMatchHeadline` |
| Nothing here matches what you are looking for. Clear the filters, or search for less. | ไม่มีรายการที่ตรงกับสิ่งที่คุณค้นหา ล้างตัวกรอง หรือค้นหาให้กว้างขึ้น | HistoryCopy.kt `noMatchBody` |
| {n} transactions outside this filter | อีก {n} รายการอยู่นอกตัวกรองนี้ | HistoryCopy.kt `hiddenByFilter` |
| Clear filters | ล้างตัวกรอง | HistoryCopy.kt `clearFilters` |
| Clear search | ล้างคำค้นหา | HistoryCopy.kt `clearSearch` |

## Reports

| English | Thai | Where |
|---|---|---|
| Spending | การใช้จ่าย | ReportsCopy.kt `title` |
| Spent {range} | ใช้ไป {range} | ReportsCopy.kt `spent` |
| 3 months | 3 เดือน | ReportsCopy.kt `threeMonths` |
| 6 months | 6 เดือน | ReportsCopy.kt `sixMonths` |
| 12 months | 12 เดือน | ReportsCopy.kt `twelveMonths` |
| Week of {date} | สัปดาห์ที่เริ่ม {date} | ReportsCopy.kt `weekOf` |
| avg {amount} | เฉลี่ย {amount} | ReportsCopy.kt `average` |
| {n} purchases | {n} รายการ | ReportsCopy.kt `purchases` |
| last week | สัปดาห์ที่แล้ว | ReportsCopy.kt `lastWeek` |
| last month | เดือนที่แล้ว | ReportsCopy.kt `lastMonth` |
| the 3 months before | ช่วง 3 เดือนก่อนหน้า | ReportsCopy.kt `threeMonthsBefore` |
| the 6 months before | ช่วง 6 เดือนก่อนหน้า | ReportsCopy.kt `sixMonthsBefore` |
| the year before | ปีก่อนหน้า | ReportsCopy.kt `yearBefore` |
| same | เท่าเดิม | ReportsCopy.kt `same` |
| W, M, 3M, 6M, Y | not translated | ReportsCopy.kt `tabWeek` … `tabYear` |
| Week (spoken) | สัปดาห์ | ReportsCopy.kt `spokenWeek` |
| Month (spoken) | เดือน | ReportsCopy.kt `spokenMonth` |
| Year (spoken) | ปี | ReportsCopy.kt `spokenYear` |
| Net | สุทธิ | ReportsCopy.kt `net` |
| Top categories | หมวดหมู่ที่ใช้มากที่สุด | ReportsCopy.kt `topCategories` |
| Nothing was spent in this period. | ไม่มีรายจ่ายในช่วงนี้ | ReportsCopy.kt `nothingSpent` |
| Nothing to chart yet. | ยังไม่มีข้อมูลให้แสดง | ReportsCopy.kt `nothingToChart` |
| Bars fill in as purchases arrive. A {bucket} with no spend shows a short stub, never a gap. | แท่งกราฟจะเพิ่มขึ้นเมื่อมีรายการเข้ามา {bucket}ที่ไม่มีรายจ่ายจะแสดงเป็นแท่งสั้น ๆ ไม่เว้นว่าง | ReportsCopy.kt `emptyBody` |
| day | วัน | ReportsCopy.kt `bucketDay` |
| week | สัปดาห์ | ReportsCopy.kt `bucketWeek` |
| month | เดือน | ReportsCopy.kt `bucketMonth` |
| Reports build themselves as slips arrive | รายงานจะสร้างขึ้นเองเมื่อมีสลิปเข้ามา | ReportsCopy.kt `buildsItself` |
| M T W T F S S | จ อ พ พฤ ศ ส อา | ReportsCopy.kt `weekAxis` |
| J F M A M J J A S O N D | not translated | ReportsCopy.kt `yearAxis` |
| Show in History | ดูในประวัติ | ReportsCopy.kt `showInHistory` |

## A transaction's record, its edit and its delete

| English | Thai | Where |
|---|---|---|
| Purchase | รายจ่าย | RecordCopy.kt `purchase` |
| Edit purchase | แก้ไขรายจ่าย | RecordCopy.kt `editPurchase` |
| Edit income | แก้ไขรายรับ | RecordCopy.kt `editIncome` |
| Close without saving | ปิดโดยไม่บันทึก | RecordCopy.kt `closeWithoutSaving` |
| This transaction is gone | รายการนี้ไม่อยู่แล้ว | RecordCopy.kt `gone` |
| It was deleted while this screen was open. Your totals already reflect that, and there is nothing left here to change. | รายการนี้ถูกลบขณะที่หน้านี้เปิดอยู่ ยอดรวมของคุณอัปเดตแล้ว และไม่มีอะไรให้เปลี่ยนที่นี่ | RecordCopy.kt `goneBody` |
| Nothing here to delete | ไม่มีอะไรให้ลบที่นี่ | RecordCopy.kt `goneStatus` |
| It was deleted while this screen was open, so there is nothing left to correct. Your totals already reflect that. | รายการนี้ถูกลบขณะที่หน้านี้เปิดอยู่ จึงไม่มีอะไรให้แก้ ยอดรวมของคุณอัปเดตแล้ว | RecordCopy.kt `goneEditBody` |
| Nothing here to edit | ไม่มีอะไรให้แก้ไขที่นี่ | RecordCopy.kt `goneEditStatus` |
| When | วันเวลา | RecordCopy.kt `whenLabel` |
| From | ที่มา | RecordCopy.kt `fromLabel` |
| Reference | เลขอ้างอิง | RecordCopy.kt `referenceLabel` |
| Shop | ร้าน | RecordCopy.kt `shopLabel` |
| Shared in {group} | แชร์ในกลุ่ม {group} | RecordCopy.kt `sharedIn` |
| Share this cost | แชร์ค่าใช้จ่ายนี้ | RecordCopy.kt `shareThisCost` |
| Make a group on the Shared tab first, then share this from here. | สร้างกลุ่มในแท็บกลุ่มก่อน แล้วค่อยแชร์จากที่นี่ | RecordCopy.kt `shareNoGroups` |
| {n} people | {n} คน | RecordCopy.kt `people` |
| Why {category}? | ทำไมถึงเป็น {category} | RecordCopy.kt `whyFiled` |
| You picked it for {shop} on {day} {month}. | คุณเลือกหมวดหมู่นี้ให้ {shop} เมื่อวันที่ {day} {month} | RecordCopy.kt `pickedOn` |
| You picked it for {shop}. | คุณเลือกหมวดหมู่นี้ให้ {shop} | RecordCopy.kt `picked` |
| {n} slips since went the same way. | หลังจากนั้นมีสลิปอีก {n} ใบที่เข้าหมวดเดียวกัน | RecordCopy.kt `slipsSince` |
| This is the first slip to go the same way. | นี่เป็นสลิปแรกที่เข้าหมวดนี้ | RecordCopy.kt `firstSlip` |
| The slip image for this transaction was already deleted. | รูปสลิปของรายการนี้ถูกลบไปแล้ว | RecordCopy.kt `slipImageGone` |
| Deleting it here takes {amount} off {periods}. | ถ้าลบที่นี่ ยอด{periods} จะลดลง {amount} | RecordCopy.kt `takesOff` |
| Deleting it here takes {amount} off your history. Today, this week and this month do not change. | ถ้าลบที่นี่ ประวัติของคุณจะลดลง {amount} ยอดวันนี้ สัปดาห์นี้ และเดือนนี้ไม่เปลี่ยน | RecordCopy.kt `takesOffHistory` |
| {amount} comes off {periods}. | ยอด{periods} จะลดลง {amount} | RecordCopy.kt `comesOff` |
| {amount} comes off your history. Today, this week and this month do not change. | ประวัติของคุณจะลดลง {amount} ยอดวันนี้ สัปดาห์นี้ และเดือนนี้ไม่เปลี่ยน | RecordCopy.kt `comesOffHistory` |
| Only this row goes. Nothing added by hand teaches a shop rule. | ลบเฉพาะรายการนี้ รายการที่เพิ่มเองไม่ได้สร้างกฎของร้าน | RecordCopy.kt `handFooter` |
| Only this one purchase is removed. | ลบเฉพาะรายการนี้รายการเดียว | RecordCopy.kt `onlyThisOne` |
| {shop} stays sorted into {category}. Only this one purchase is removed. | {shop} ยังอยู่ในหมวด {category} ลบเฉพาะรายการนี้รายการเดียว | RecordCopy.kt `staysSorted` |
| edited {date} | แก้ไขเมื่อ {date} | RecordCopy.kt `editedOn` |
| K PLUS slip | สลิป K PLUS | RecordCopy.kt `kplusSlip` |
| Shared slip | สลิปที่แชร์มา | RecordCopy.kt `sharedSlip` |
| Added by hand | เพิ่มเอง | RecordCopy.kt `addedByHand` |
| received (spoken beside the amount) | ได้รับ | RecordCopy.kt `received` |
| spent (spoken beside the amount) | จ่ายไป | RecordCopy.kt `spent` |
| Deleted {name} | ลบ {name} แล้ว | RecordCopy.kt `deleted` |
| Updated {name} | อัปเดต {name} แล้ว | RecordCopy.kt `updated` |
| Deleted {name} · removed from {group} | ลบ {name} แล้ว · นำออกจาก {group} แล้ว | RecordCopy.kt `deletedFromGroup` |
| Delete this purchase? | ลบรายจ่ายนี้ไหม | RecordCopy.kt `deletePurchase` |
| Delete this income? | ลบรายรับนี้ไหม | RecordCopy.kt `deleteIncome` |
| You can undo it until you do something else. | เลิกทำได้จนกว่าคุณจะทำอย่างอื่น | RecordCopy.kt `undoUntilNext` |
| Also remove it from {group} | นำออกจาก {group} ด้วย | RecordCopy.kt `alsoRemoveFrom` |
| This also changes {group} | การเปลี่ยนนี้มีผลกับ {group} ด้วย | RecordCopy.kt `alsoChanges` |
| Read from the slip as {amount} | อ่านจากสลิปได้ {amount} | RecordCopy.kt `readFromSlip` |
| Amount, {digits} baht. Edit (spoken) | จำนวนเงิน {digits} บาท แก้ไข | RecordCopy.kt `amountSpoken` |
| The row is marked edited in your history. | รายการนี้จะมีเครื่องหมายว่าแก้ไขแล้วในประวัติ | RecordCopy.kt `markedEdited` |
| Nothing here came from a slip. Your correction is kept as typed. | ไม่มีข้อมูลใดที่นี่มาจากสลิป สิ่งที่คุณแก้จะเก็บตามที่พิมพ์ | RecordCopy.kt `nothingFromSlip` |
| The amount came from the slip. Your edit is kept beside it. | จำนวนเงินมาจากสลิป สิ่งที่คุณแก้จะเก็บไว้คู่กัน | RecordCopy.kt `amountFromSlip` |
| The reference came from the slip and stays as read. Your edit is kept beside them. | เลขอ้างอิงมาจากสลิปและคงตามที่อ่านได้ สิ่งที่คุณแก้จะเก็บไว้คู่กัน | RecordCopy.kt `referenceStays` |
| The shop came from the slip and stays as read. Your edit is kept beside them. | ชื่อร้านมาจากสลิปและคงตามที่อ่านได้ สิ่งที่คุณแก้จะเก็บไว้คู่กัน | RecordCopy.kt `shopStays` |
| Shop and reference came from the slip and stay as read. Your edit is kept beside them. | ชื่อร้านและเลขอ้างอิงมาจากสลิปและคงตามที่อ่านได้ สิ่งที่คุณแก้จะเก็บไว้คู่กัน | RecordCopy.kt `bothStay` |
| Changing the category here does not change how {shop} is sorted. | การเปลี่ยนหมวดหมู่ที่นี่ไม่เปลี่ยนวิธีจัดหมวดของ {shop} | RecordCopy.kt `sortingStays` |
