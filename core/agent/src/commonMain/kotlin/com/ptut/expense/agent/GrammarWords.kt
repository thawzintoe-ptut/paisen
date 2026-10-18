package com.ptut.expense.agent

/**
 * #205: the words [CommandGrammar] knows, in English, Thai and Burmese (Unicode; Zawgyi is
 * converted before it gets here). A word of ASCII letters matches whole words without case; Thai and
 * Burmese have no spaces between words, so their words match anywhere in the text.
 *
 * The eval set (#205) is what tells us which words are missing. Add a word here, not a rule.
 */
object GrammarWords {

    /** Longest first where one contains another: เมื่อวานซืน holds เมื่อวาน. */
    val twoDaysAgo = listOf("day before yesterday", "เมื่อวานซืน", "တမြန်နေ့က", "တမြန်နေ့")
    val yesterday = listOf("yesterday", "last night", "เมื่อวานนี้", "เมื่อวาน", "เมื่อคืน", "မနေ့ည", "မနေ့က", "မနေ့")
    val today = listOf("today", "tonight", "this morning", "วันนี้", "คืนนี้", "เช้านี้", "ဒီနေ့", "ယနေ့", "ဒီည", "ဒီမနက်")

    /** Only the "this …" forms in Thai and Burmese: เดือน alone is inside เงินเดือน, a salary. */
    val week = listOf("this week", "week", "สัปดาห์นี้", "อาทิตย์นี้", "ဒီအပတ်", "ဒီတစ်ပတ်")
    val month = listOf("this month", "month", "เดือนนี้", "ဒီလ")

    /** A question about totals, which reads spend_summary rather than adding a row. */
    val howMuch = listOf("how much", "total", "เท่าไหร่", "เท่าไร", "ทั้งหมด", "ဘယ်လောက်", "စုစုပေါင်း")

    val income = listOf(
        "income", "salary", "wage", "earned", "earn", "received", "got paid", "refund", "bonus", "sold",
        "รายรับ", "เงินเดือน", "ได้รับ", "ได้เงิน", "รับเงิน", "โบนัส", "ขายได้", "เงินคืน",
        "ဝင်ငွေ", "လစာ", "ရငွေ", "ရရှိ", "ဘောနပ်",
    )

    /** A shared group's expense rather than your own. A group's name alone is not enough. */
    val share = listOf("split", "share", "shared", "group", "หาร", "แชร์", "ခွဲ", "မျှ")

    /** Numbers inside a shop's name, kept out of the amount. */
    val numberedNames = listOf("7-eleven", "7-11", "7/11", "เซเว่น")

    /** Seed category ids to their words. A category the reader made is matched by its own name. */
    val categories: Map<String, List<String>> = mapOf(
        "food" to listOf(
            "food", "lunch", "dinner", "breakfast", "brunch", "coffee", "tea", "drink", "drinks", "meal",
            "restaurant", "snack", "noodles", "rice", "beer", "cafe", "grab food", "foodpanda",
            "อาหาร", "ข้าว", "กาแฟ", "ชานม", "ชา", "ก๋วยเตี๋ยว", "มื้อ", "เครื่องดื่ม", "ขนม", "ส้มตำ", "เบียร์",
            "စား", "ထမင်း", "ကော်ဖီ", "လက်ဖက်ရည်", "မုန့်", "ညစာ", "နေ့လည်စာ", "မနက်စာ", "ခေါက်ဆွဲ", "ဘီယာ",
        ),
        "groceries" to listOf(
            "groceries", "grocery", "supermarket", "market", "7-eleven", "7-11", "lotus", "lotuss", "big c", "makro",
            "ซูเปอร์", "ตลาด", "เซเว่น", "โลตัส", "บิ๊กซี", "แม็คโคร", "ของใช้",
            "ဈေး", "ကုန်စုံ", "စူပါမားကတ်",
        ),
        "transport" to listOf(
            "taxi", "grab", "bolt", "bts", "mrt", "bus", "train", "fuel", "petrol", "transport", "motorbike",
            "fare", "ferry", "flight", "toll",
            "แท็กซี่", "แกร็บ", "รถไฟฟ้า", "บีทีเอส", "รถเมล์", "รถไฟ", "น้ำมัน", "ค่ารถ", "วิน", "เรือ", "ทางด่วน",
            "တက္ကစီ", "ဘတ်စ်ကား", "ကားခ", "ဓာတ်ဆီ", "ရထား", "ဆိုင်ကယ်",
        ),
        "bills" to listOf(
            "bill", "bills", "rent", "electricity", "electric", "water bill", "internet", "wifi", "phone bill",
            "utilities", "insurance", "subscription", "netflix", "spotify",
            "ค่าไฟ", "ค่าน้ำ", "ค่าเช่า", "ค่าเน็ต", "อินเทอร์เน็ต", "ค่าโทรศัพท์", "ค่าโทร", "บิล", "ประกัน",
            "မီးဖိုး", "ရေဖိုး", "အိမ်လခ", "အခန်းခ", "ဖုန်းဘေ", "အင်တာနက်", "ဘေလ်",
        ),
        "shopping" to listOf(
            "shopping", "clothes", "shirt", "shoes", "shopee", "lazada", "uniqlo", "gift", "mall",
            "ช้อปปิ้ง", "เสื้อ", "รองเท้า", "ช็อปปี้", "ลาซาด้า", "ของขวัญ", "ห้าง",
            "အဝတ်", "ဖိနပ်", "ရှော့ပင်း", "လက်ဆောင်", "အင်္ကျီ",
        ),
        "income" to listOf(
            "salary", "income", "wage", "bonus", "refund",
            "เงินเดือน", "รายรับ", "โบนัส", "เงินคืน",
            "လစာ", "ဝင်ငွေ", "ဘောနပ်",
        ),
    )

    /** English words that say nothing about the row, dropped from the note. */
    val englishFiller = setOf(
        "i", "my", "a", "an", "the", "for", "on", "at", "in", "to", "with", "of", "and",
        "spent", "spend", "paid", "pay", "bought", "buy", "cost", "add", "expense", "got",
    )

    /** Thai verbs a command starts with: ซื้อกาแฟ is กาแฟ bought. */
    val thaiLeadingVerbs = listOf("ซื้อ", "จ่าย", "ใช้ไป", "ใช้", "เพิ่ม")
    val thaiTrailingParticles = listOf("ครับ", "ค่ะ", "คะ", "นะ", "ไป")

    /** Burmese verbs and particles that end a command: ကော်ဖီသောက်ခဲ့တယ် keeps ကော်ဖီသောက်. */
    val burmeseTrailing = listOf("သုံး", "ဝယ်", "ပေး", "ခဲ့", "တယ်", "ပြီ", "ပါ", "မယ်", "လိုက်", "ထည့်")
}
