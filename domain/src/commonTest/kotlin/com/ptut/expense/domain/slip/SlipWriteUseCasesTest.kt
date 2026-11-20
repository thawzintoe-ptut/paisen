package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.SlipDebt
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SlipWriteUseCasesTest {

    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@SlipWriteUseCasesTest.now
    }

    private val store = InMemoryImportStore()
    private val importer = SlipImporter(FakeSlipSource(), FakeSlipTextReader(), store)
    private var ids = 0
    private val confirm = ConfirmSlipUseCase(importer, clock) { "id${++ids}" }

    private val slip = QueuedSlip(
        id = "q1",
        merchant = "MK Restaurant",
        occurredAtEpochMillis = 1_000,
        note = "ref 0142K9",
        amount = Satang(48_500),
        reason = ReviewReason.NewMerchant,
        capturedAtEpochMillis = 1_000,
    )

    private fun confirmation(
        slip: QueuedSlip = this.slip,
        typedShop: String = "MK Restaurant",
        shopRead: Boolean = true,
        planned: Boolean = false,
        foreign: ForeignAmount? = null,
    ) = SlipConfirmation(slip, categoryId = "food", subCategoryId = null, amount = Satang(48_500), foreign = foreign, typedShop = typedShop, shopRead = shopRead, planned = planned)

    @Test
    fun aReadShopIsTheRulesKeyAndAFiledSlipLeavesTheQueue() = runTest {
        val filed = confirm(confirmation()).getOrThrow()

        assertEquals("MK Restaurant", filed.counterparty)
        assertNull(filed.displayName)
        assertEquals("ref 0142K9", filed.reference)
        assertEquals(now.toEpochMilliseconds(), filed.confirmedAtEpochMillis)
        assertEquals(listOf(filed), store.confirmed)
    }

    @Test
    fun aNameTypedOverAReadShopIsWhatRowsShow() = runTest {
        val filed = confirm(confirmation(typedShop = "  MK Rama 9  ")).getOrThrow()

        assertEquals("MK Restaurant", filed.counterparty)
        assertEquals("MK Rama 9", filed.displayName)
    }

    @Test
    fun aSlipThatReadNoShopTakesTheTypedNameOrNone() = runTest {
        val unread = slip.copy(merchant = "")

        assertEquals("Noodle stall", confirm(confirmation(unread, typedShop = "Noodle stall", shopRead = false)).getOrThrow().counterparty)
        assertNull(confirm(confirmation(unread, typedShop = " ", shopRead = false)).getOrThrow().counterparty)
    }

    @Test
    fun onlySpendingCanBePlanned() = runTest {
        assertTrue(confirm(confirmation(planned = true)).getOrThrow().planned)
        assertEquals(false, confirm(confirmation(slip = slip.copy(direction = Direction.Income), planned = true)).getOrThrow().planned)
    }

    @Test
    fun aForeignSlipKeepsWhatItReadAtTheTypedRate() = runTest {
        val kyat = slip.copy(currency = "MMK", amount = Satang.ofMinorUnits(12_500))
        val foreign = ForeignAmount("MMK", 12_500, Rate(8, 3))

        val filed = confirm(confirmation(kyat, foreign = foreign)).getOrThrow()

        assertEquals(ForeignAmount("MMK", 12_500, Rate(8, 3)).inHome, filed.readAmount)
        assertEquals(foreign, filed.foreign)
    }

    @Test
    fun aDebtIsPaidByBankTransferAtTheSlipsTime() = runTest {
        val recorded = mutableListOf<PaymentDraft>()
        val debt = SlipDebt("g1", "Flat 12", "me", "aye", "Aye", Satang(48_500))

        assertTrue(PaySlipDebtUseCase { recorded += it }(slip, debt).isSuccess)
        assertEquals(listOf(PaymentDraft("g1", "me", "aye", Satang(48_500), PaymentMethod.BankTransfer, 1_000)), recorded)
    }

    @Test
    fun aDebtThatFailsToRecordIsAFailure() = runTest {
        val debt = SlipDebt("g1", "Flat 12", "me", "aye", "Aye", Satang(48_500))

        assertTrue(PaySlipDebtUseCase { error("offline") }(slip, debt).isFailure)
    }
}
