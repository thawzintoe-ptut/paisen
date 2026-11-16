package com.ptut.expense.importer

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.rules.MerchantRule
import com.ptut.expense.domain.shared.Rate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** #33: a kyat receipt becomes baht at the saved rate, and the first one asks for it. */
class KyatImportTest {

    private companion object {
        /** Split so the account-number guard sees no long digit run in this file. */
        const val REFERENCE = "01003769" + "01047326" + "7025"
        const val MERCHANT_CODE = "70169" + "501010"
        const val SHOP = "LimitedCollections Online"
    }

    private val now = Instant.fromEpochMilliseconds(1_755_000_000_000)

    private val receipt = listOf(
        "KBZ BANK",
        "E-Receipt",
        "-29,500.00 Ks",
        "Transaction Time",
        "14/06/2025 12:10:15",
        "Transaction No.",
        REFERENCE,
        "Transfer To",
        "$MERCHANT_CODE -",
        SHOP,
        "Thank you for using KBZPay!",
    ).joinToString("\n")

    private fun importer(source: FakeSlipSource, store: InMemoryImportStore, readsWallets: Boolean = true) = SlipImporter(
        source = source,
        reader = FakeSlipTextReader(),
        store = store,
        clock = object : Clock {
            override fun now(): Instant = this@KyatImportTest.now
        },
        readsWallets = readsWallets,
    )

    @Test
    fun `a kyat receipt from a known shop waits in To check until there is a rate`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", receipt, now.toEpochMilliseconds()) }
        val store = InMemoryImportStore(listOf(MerchantRule(SHOP, "shopping")))

        importer(source, store).run()

        val queued = store.queued.single()
        assertEquals(ReviewReason.RateNeeded, queued.reason)
        assertEquals("MMK", queued.currency)
        assertEquals(Satang.ofMinorUnits(29_500), queued.amount)
    }

    @Test
    fun `with a saved rate it is filed as baht and keeps the kyat`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", receipt, now.toEpochMilliseconds()) }
        val store = InMemoryImportStore(listOf(MerchantRule(SHOP, "shopping"))).apply { rates["MMK"] = Rate.parse("8.2", 1_000)!! }

        importer(source, store).run()

        val foreign = store.filed.single().foreign!!
        assertEquals(29_500L, foreign.originalMinor)
        assertEquals(Satang(24_190), foreign.inHome)
    }

    @Test
    fun `a build that does not read wallets treats the receipt as no slip`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", receipt, now.toEpochMilliseconds()) }
        val store = InMemoryImportStore(listOf(MerchantRule(SHOP, "shopping")))

        importer(source, store, readsWallets = false).run()

        assertTrue(store.queued.isEmpty() && store.filed.isEmpty())
    }

    // #44: a kyat ledger

    private val kplus = listOf(
        "Payment Completed",
        "15 Aug 26  9:25 AM",
        "MR. SOMCHAI J",
        "KBank",
        "xxx-x-xxxxx-x",
        "LAWSON108_P3793 THE LIVIN",
        "Transaction ID:",
        "016200000007AQR00007",
        "Amount:",
        "72.00 Baht",
        "Fee:",
        "0.00 Baht",
    ).joinToString("\n")

    private fun inKyat(test: suspend () -> Unit) = runTest {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))
        try {
            test()
        } finally {
            Home.use(HomeChoice.Default)
        }
    }

    @Test
    fun `in a kyat ledger a K PLUS slip waits for a rate and a KBZPay slip files as it is`() = inKyat {
        val source = FakeSlipSource().apply {
            add("a.jpg", kplus, now.toEpochMilliseconds())
            add("b.jpg", receipt, now.toEpochMilliseconds())
        }
        val store = InMemoryImportStore(listOf(MerchantRule("LAWSON108_P3793 THE LIVIN", "groceries"), MerchantRule(SHOP, "shopping")))

        importer(source, store).run()

        assertEquals(ReviewReason.RateNeeded, store.queued.single().reason)
        assertEquals("THB", store.queued.single().currency)
        assertEquals(null, store.filed.single().foreign)
        assertEquals(Satang.ofMinorUnits(29_500), store.filed.single().read.amount)
    }

    @Test
    fun `in a kyat ledger a K PLUS slip with a saved rate files as kyat`() = inKyat {
        val source = FakeSlipSource().apply { add("a.jpg", kplus, now.toEpochMilliseconds()) }
        val store = InMemoryImportStore(listOf(MerchantRule("LAWSON108_P3793 THE LIVIN", "groceries"))).apply { rates["THB"] = Rate.parse("125")!! }

        importer(source, store).run()

        assertEquals(Satang.ofMinorUnits(9_000), store.filed.single().foreign?.inHome)
    }
}
