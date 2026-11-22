package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.ArchiveGoalUseCase
import com.ptut.expense.domain.play.CASH_ACCOUNT
import com.ptut.expense.domain.play.ChooseSavingsAccountUseCase
import com.ptut.expense.domain.play.DepositSource
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.play.GoalDeposit
import com.ptut.expense.domain.play.ObserveGoalJarUseCase
import com.ptut.expense.domain.play.ObserveOwnAccountsUseCase
import com.ptut.expense.domain.play.PutMoneyInUseCase
import com.ptut.expense.domain.play.RemoveSaidDepositUseCase
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.play.SeenAccount
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class GoalJarViewModelTest : ViewModelTest() {

    private val today = LocalDate(2026, 11, 25)
    private val goal = Goal("g1", "Chiang Mai", Satang(1_200_000), "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0)

    private fun TestScope.jar(book: FakePlayBook, goalId: String? = "g1", name: String? = null, target: String? = null, rows: List<Transaction> = emptyList(), putIn: Boolean = false): GoalJarViewModel {
        val ledger = ObserveTransactionsUseCase(FakeLedger(rows))
        val homeToday = GetHomeTodayUseCase(clockAt(today))
        val jar = GoalJarViewModel(
            goalId = goalId,
            name = name,
            target = target,
            observeJar = ObserveGoalJarUseCase(book, ledger, homeToday, evening),
            saveGoal = SaveGoalUseCase(book, homeToday, clockAt(today)),
            archiveGoal = ArchiveGoalUseCase(book, clockAt(today)),
            putMoneyIn = PutMoneyInUseCase(book, homeToday, clockAt(today)),
            chooseAccount = ChooseSavingsAccountUseCase(book),
            removeSaidDeposit = RemoveSaidDepositUseCase(book),
            ownAccounts = ObserveOwnAccountsUseCase(book),
            openPutIn = putIn,
        )
        backgroundScope.launch { jar.uiState.collect {} }
        runCurrent()
        return jar
    }

    /** 20:00, after the hour the jar starts offering today's leftover. */
    private val evening = object : Clock {
        override fun now(): Instant = LocalDateTime(today.year, today.month, today.day, 20, 0).toInstant(BangkokTime)
    }

    private fun slipDeposit(id: String, day: LocalDate, minor: Long) = GoalDeposit(id, "g1", Satang(minor), day, atEpochMillis = minor, source = DepositSource.Slip, account = "••4821")

    @Test
    fun `a trip estimate opens the form filled and saving starts the jar today`() = runTest {
        val book = FakePlayBook()
        val jar = jar(book, goalId = null, name = "Chiang Mai", target = "1200.00")
        assertEquals(true, assertIs<GoalJarUiState.Editing>(jar.uiState.value).canSave)

        jar.save()
        runCurrent()

        val saved = book.goals.value.single()
        assertEquals(Satang(120_000), saved.target)
        assertEquals(today, saved.start)
        assertIs<GoalJarUiState.Jar>(jar.uiState.value)
    }

    @Test
    fun `an empty jar asks for the first money and lists nothing`() = runTest {
        val state = assertIs<GoalJarUiState.Jar>(jar(FakePlayBook(goals = listOf(goal))).uiState.value)

        assertEquals("Put the first money in and PaiSeN works out a weekly pace.", state.plan)
        assertEquals("0%", state.percent)
        assertEquals(emptyList(), state.deposits)
        assertNull(state.savesTo)
        assertNull(state.sheet)
    }

    @Test
    fun `deposits fill the jar and set the plan and list newest first`() = runTest {
        val book = FakePlayBook(
            goals = listOf(goal.copy(savingsAccount = "••4821")),
            deposits = listOf(slipDeposit("a", LocalDate(2026, 11, 1), 50_000), slipDeposit("b", LocalDate(2026, 11, 22), 22_000)),
        )

        val state = assertIs<GoalJarUiState.Jar>(jar(book).uiState.value)

        assertEquals("6%", state.percent)
        assertEquals("฿720 saved of ฿12,000", state.savedOf)
        assertEquals("฿11,280 to go. Save ฿180 a week and you're there by February.", state.plan)
        assertEquals(listOf("b", "a"), state.deposits.map { it.id })
        assertEquals("Moved to Savings ••4821", state.deposits.first().name)
        assertEquals("+฿220", state.deposits.first().amount)
        assertEquals("Saves to Savings ••4821", state.savesTo)
    }

    @Test
    fun `the first Put money in asks where you save and Use this account leads to the amount`() = runTest {
        val book = FakePlayBook(goals = listOf(goal), seenAccounts = listOf(SeenAccount("••4821", "MR. SOMCHAI J", 3, 0)))
        val jar = jar(book, putIn = true)

        val where = assertIs<JarSheetUiModel.WhereDoYouSave>(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet)
        assertEquals(listOf("Savings ••4821", "I keep it as cash"), where.options.map { it.name })
        assertEquals("you moved money here 3 times", where.options.first().line)
        assertEquals(false, where.canUse)

        jar.pickAccount("••4821")
        runCurrent()
        jar.useAccount()
        runCurrent()

        assertEquals("••4821", book.goals.value.single().savingsAccount)
        val putIn = assertIs<JarSheetUiModel.PutMoneyIn>(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet)
        assertEquals(listOf("฿500"), putIn.choices.map { it.label })
        assertEquals("Move ฿500 to Savings ••4821 in K PLUS.", putIn.steps.first())
        assertEquals("Open K PLUS", putIn.openBank)
    }

    @Test
    fun `a cash saver gets no bank button and I moved it fills the jar by their word`() = runTest {
        val book = FakePlayBook(goals = listOf(goal.copy(savingsAccount = CASH_ACCOUNT)))
        val jar = jar(book)

        jar.openPutIn()
        runCurrent()
        val sheet = assertIs<JarSheetUiModel.PutMoneyIn>(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet)
        assertNull(sheet.openBank)
        assertEquals("I moved it", sheet.moved)

        jar.moved()
        runCurrent()

        val deposit = book.deposits.value.single()
        assertEquals(Satang(50_000), deposit.amount)
        assertEquals(DepositSource.Said, deposit.source)
        assertNull(deposit.account)
        assertNull(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet)
        assertEquals("You said you moved it", assertIs<GoalJarUiState.Jar>(jar.uiState.value).deposits.single().name)
    }

    @Test
    fun `the leftover of today comes first among the amounts and a pick changes the step`() = runTest {
        val book = FakePlayBook(goals = listOf(goal.copy(savingsAccount = "••4821")))
        val fridays = (1..3).map { week -> spendOn("f$week", today.minus(week, DateTimeUnit.WEEK), 30_000) } + spendOn("today", today, 8_000)
        val jar = jar(book, rows = fridays, putIn = true)

        val sheet = assertIs<JarSheetUiModel.PutMoneyIn>(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet)
        assertEquals(listOf("฿220 · today's leftover", "฿500"), sheet.choices.map { it.label })
        assertEquals("220", sheet.amount.whole)

        jar.pick(Satang(50_000))
        runCurrent()

        assertEquals("Move ฿500 to Savings ••4821 in K PLUS.", assertIs<JarSheetUiModel.PutMoneyIn>(assertIs<GoalJarUiState.Jar>(jar.uiState.value).sheet).steps.first())
    }

    @Test
    fun `a deposit you said you made can be taken out and a slip deposit cannot`() = runTest {
        val said = GoalDeposit("said:1", "g1", Satang(30_000), today, atEpochMillis = 2, source = DepositSource.Said)
        val book = FakePlayBook(goals = listOf(goal.copy(savingsAccount = CASH_ACCOUNT)), deposits = listOf(slipDeposit("slip:1", today, 50_000), said))
        val jar = jar(book)
        val rows = assertIs<GoalJarUiState.Jar>(jar.uiState.value).deposits
        assertEquals(listOf("Take out", null), rows.sortedBy { it.id }.map { it.remove })

        jar.remove("said:1")
        jar.remove("slip:1")
        runCurrent()

        assertEquals(listOf("slip:1"), book.deposits.value.map { it.id })
    }

    @Test
    fun `edit goal opens on the jar name and amount so Save works at once`() = runTest {
        val jar = jar(FakePlayBook(goals = listOf(goal)))

        jar.edit()
        runCurrent()

        val form = assertIs<GoalJarUiState.Editing>(jar.uiState.value)
        assertEquals("Chiang Mai", form.name)
        assertEquals("12000.00", form.target)
        assertEquals(true, form.canSave)
    }

    @Test
    fun `a jar already holding its amount says it is full`() = runTest {
        val book = FakePlayBook(goals = listOf(goal), deposits = listOf(slipDeposit("a", today, 1_200_000)))

        assertEquals("The jar is full.", assertIs<GoalJarUiState.Jar>(jar(book).uiState.value).plan)
    }

    @Test
    fun `a jar with no name or amount cannot be saved`() = runTest {
        val book = FakePlayBook()
        val jar = jar(book, goalId = null)
        jar.setName("Trip")
        runCurrent()

        assertEquals(false, assertIs<GoalJarUiState.Editing>(jar.uiState.value).canSave)
        jar.save()
        runCurrent()
        assertEquals(emptyList(), book.goals.value)
    }
}
