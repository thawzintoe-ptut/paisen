package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GetPaymentBetweenUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.RecordPaymentUseCase
import com.ptut.expense.domain.shared.RemovePaymentUseCase
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Half = Satang.ofMinorUnits(24_000)
private val Whole = Satang.ofMinorUnits(48_000)

class RecordPaymentViewModelTest : ViewModelTest() {

    /** #6: the second tap lands while the first payment is still writing. */
    @Test
    fun `two fast taps on Record add one payment`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = recordPaymentViewModel(groupId = "g1", fromMemberId = "aye", toMemberId = "me", repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        runCurrent()

        var closed = 0
        viewModel.record { closed++ }
        viewModel.record { closed++ }
        runCurrent()
        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.payments.size)
        assertEquals(1, closed)
    }

    @Test
    fun `someone who owes you gets Ask to pay with the amount and your PromptPay`() = runTest {
        val ready = ready(dinner(paidBy = "me"), from = "aye", to = "me", myPromptPay = "081-234-5678")

        val ask = assertNotNull(ready.askToPay)
        assertEquals("Ask Aye to pay", ask.label)
        assertEquals("Flat 12: Aye, you owe me ฿240.00.\nMy PromptPay: 081-234-5678", ask.message)
    }

    @Test
    fun `with no PromptPay the message is only the amount`() = runTest {
        val ready = ready(dinner(paidBy = "me"), from = "aye", to = "me")

        assertEquals("Flat 12: Aye, you owe me ฿240.00.", assertNotNull(ready.askToPay).message)
    }

    /** The money goes out, so there is nobody to ask. */
    @Test
    fun `paying someone yourself offers no Ask to pay`() = runTest {
        val ready = ready(dinner(paidBy = "aye"), from = "me", to = "aye")

        assertNull(ready.askToPay)
    }

    @Test
    fun `the settle plan's amount wins over what is owed`() = runTest {
        val repository = FakeSharedRepository()
        repository.snapshot.value = repository.snapshot.value.copy(expenseVersions = listOf(dinner(paidBy = "me")))
        val viewModel = recordPaymentViewModel(groupId = "g1", fromMemberId = "aye", toMemberId = "me", repository = repository, amount = "150.00")
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals("150.00", viewModel.ready().form.amountText)
    }

    @Test
    fun `with no plan amount the payment opens on what is owed`() = runTest {
        assertEquals("240.00", ready(dinner(paidBy = "me"), from = "aye", to = "me").form.amountText)
    }

    @Test
    fun `nothing owed offers no Ask to pay`() = runTest {
        val ready = ready(expense = null, from = "aye", to = "me")

        assertNull(ready.askToPay)
    }

    /** #48: the home stays baht here, and the group's kyat writes every amount. */
    @Test
    fun `a kyat group fills and asks in kyat while the home is baht`() = runTest {
        val kyat = flat12.copy(currency = "MMK")
        val ready = viewModel(dinner(paidBy = "me", currency = "MMK"), from = "aye", to = "me", group = kyat).ready()

        assertEquals("K", ready.symbol)
        assertEquals("24000", ready.form.amountText)
        assertEquals("Record K24,000", ready.buttonLabel)
        assertEquals("Flat 12: Aye, you owe me K24,000.", assertNotNull(ready.askToPay).message)
    }

    /** The bug #48 fixes: a kyat home used to write a baht group's amounts with K. */
    @Test
    fun `a baht group stays in baht for a kyat home`() = runTest {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        val ready = ready(dinner(paidBy = "me"), from = "aye", to = "me")

        assertEquals("฿", ready.symbol)
        assertEquals("240.00", ready.form.amountText)
        assertEquals("Flat 12: Aye, you owe me ฿240.00.", assertNotNull(ready.askToPay).message)
    }

    /** PromptPay moves baht only, so a kyat debt gets no QR to scan. */
    @Test
    fun `paying into a kyat group offers no PromptPay QR`() = runTest {
        val kyat = flat12.copy(currency = "MMK", members = flat12.members.map { if (it.id == "aye") it.copy(promptPayId = "081-234-5678") else it })
        val ready = viewModel(dinner(paidBy = "aye", currency = "MMK"), from = "me", to = "aye", group = kyat).ready()

        assertNull(ready.promptPayQr)
    }

    /** #6: the balance filled in on open is not a change; what is typed or picked after it is. */
    @Test
    fun `leaving asks first only once the payment differs from how it opened`() = runTest {
        val viewModel = viewModel(dinner(paidBy = "me"), from = "aye", to = "me")
        assertFalse(viewModel.ready().changed)

        viewModel.setAmount("100")
        runCurrent()
        assertTrue(viewModel.ready().changed)

        viewModel.setAmount("240.00")
        runCurrent()
        assertFalse(viewModel.ready().changed)

        viewModel.setMethod(PaymentMethod.Cash)
        runCurrent()
        assertTrue(viewModel.ready().changed)
    }

    /** #76: the write throws; nothing closes, the amount stays, and the button retries. */
    @Test
    fun `a failed save keeps the amount and the next tap tries again`() = runTest {
        val repository = FakeSharedRepository()
        repository.failNextPayment = true
        val viewModel = recordPaymentViewModel(groupId = "g1", fromMemberId = "aye", toMemberId = "me", repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        runCurrent()

        var closed = 0
        viewModel.record { closed++ }
        runCurrent()

        val failed = viewModel.ready()
        assertEquals(0, closed)
        assertEquals("240", failed.form.amountText)
        assertEquals("Couldn't record this payment", failed.failedTitle)
        assertEquals("Try again", failed.buttonLabel)
        assertTrue(failed.canSave)

        viewModel.record { closed++ }
        runCurrent()
        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.payments.size)
        assertEquals(1, closed)
        assertNull(viewModel.ready().failedTitle)
    }

    private fun TestScope.ready(expense: SharedExpense?, from: String, to: String, myPromptPay: String? = null): RecordPaymentUiState.Ready =
        viewModel(expense, from, to, myPromptPay).ready()

    private fun RecordPaymentViewModel.ready() = assertIs<RecordPaymentUiState.Ready>(uiState.value)

    private fun TestScope.viewModel(
        expense: SharedExpense?,
        from: String,
        to: String,
        myPromptPay: String? = null,
        group: SharedGroup = flat12.copy(members = flat12.members.map { if (it.id == "me") it.copy(promptPayId = myPromptPay) else it }),
    ): RecordPaymentViewModel {
        val repository = FakeSharedRepository(group)
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(group),
            people = emptyList(),
            expenseVersions = listOfNotNull(expense),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = recordPaymentViewModel(groupId = group.id, fromMemberId = from, toMemberId = to, repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /** A ฿480 dinner split equally, so whoever did not pay owes ฿240. In kyat the same minor units are K48,000. */
    private fun dinner(paidBy: String, currency: String = Currencies.Thb.code) = SharedExpense(
        id = "dinner",
        versionId = "dinner-v1",
        version = 1,
        groupId = flat12.id,
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = currency,
        amountMinor = Whole.toMinorUnits(),
        rate = null,
        satang = Whole,
        payerMemberId = paidBy,
        splitMode = SplitMode.Equally,
        shares = listOf(Share("me", 1, Half.toMinorUnits(), Half), Share("aye", 1, Half.toMinorUnits(), Half)),
        authorMemberId = paidBy,
        createdAtEpochMillis = 0,
        removed = false,
    )
}

/** Record payment as Koin builds it, its use cases over [repository]. */
private fun recordPaymentViewModel(groupId: String, fromMemberId: String, toMemberId: String, repository: FakeSharedRepository, amount: String? = null) = RecordPaymentViewModel(
    groupId = groupId,
    fromMemberId = fromMemberId,
    toMemberId = toMemberId,
    observeSnapshot = ObserveSharedSnapshotUseCase(repository),
    getPaymentBetween = GetPaymentBetweenUseCase(),
    recordPayment = RecordPaymentUseCase(repository),
    removePayment = RemovePaymentUseCase(repository),
    prefillAmount = amount,
)
