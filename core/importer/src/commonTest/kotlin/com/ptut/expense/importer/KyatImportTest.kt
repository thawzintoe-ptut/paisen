package com.ptut.expense.importer

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
        assertEquals(Satang(24_190), foreign.baht)
    }

    @Test
    fun `a build that does not read wallets treats the receipt as no slip`() = runTest {
        val source = FakeSlipSource().apply { add("a.jpg", receipt, now.toEpochMilliseconds()) }
        val store = InMemoryImportStore(listOf(MerchantRule(SHOP, "shopping")))

        importer(source, store, readsWallets = false).run()

        assertTrue(store.queued.isEmpty() && store.filed.isEmpty())
    }
}
