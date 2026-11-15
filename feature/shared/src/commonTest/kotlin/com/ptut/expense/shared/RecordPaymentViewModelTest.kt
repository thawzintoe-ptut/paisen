package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
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
        val viewModel = RecordPaymentViewModel(groupId = "g1", fromMemberId = "aye", toMemberId = "me", repository = repository)
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
    fun `nothing owed offers no Ask to pay`() = runTest {
        val ready = ready(expense = null, from = "aye", to = "me")

        assertNull(ready.askToPay)
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

    private fun TestScope.ready(expense: SharedExpense?, from: String, to: String, myPromptPay: String? = null): RecordPaymentUiState.Ready =
        viewModel(expense, from, to, myPromptPay).ready()

    private fun RecordPaymentViewModel.ready() = assertIs<RecordPaymentUiState.Ready>(uiState.value)

    private fun TestScope.viewModel(expense: SharedExpense?, from: String, to: String, myPromptPay: String? = null): RecordPaymentViewModel {
        val group = flat12.copy(members = flat12.members.map { if (it.id == "me") it.copy(promptPayId = myPromptPay) else it })
        val repository = FakeSharedRepository(group)
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(group),
            people = emptyList(),
            expenseVersions = listOfNotNull(expense),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = RecordPaymentViewModel(groupId = group.id, fromMemberId = from, toMemberId = to, repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /** A ฿480 dinner split equally, so whoever did not pay owes ฿240. */
    private fun dinner(paidBy: String) = SharedExpense(
        id = "dinner",
        versionId = "dinner-v1",
        version = 1,
        groupId = flat12.id,
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = Currencies.Thb.code,
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
