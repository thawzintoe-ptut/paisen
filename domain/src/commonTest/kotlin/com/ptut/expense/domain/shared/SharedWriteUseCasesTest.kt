package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import com.ptut.expense.sync.ChangesWaiting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SharedWriteUseCasesTest {

    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@SharedWriteUseCasesTest.now
    }

    private val flat = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(
            Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null, claimedByUid = "uid-me"),
            Member(id = "aye", groupId = "g1", name = "Aye", personId = null),
            Member(id = "gone", groupId = "g1", name = "Bo", personId = null, archived = true),
        ),
        currency = "MMK",
    )

    private val repository = RecordingSharedRepository(SharedSnapshot.Empty.copy(groups = listOf(flat)))

    private fun expense(description: String = "Dinner", note: String = "", categoryName: String? = "Food & drink") = SharedExpenseInput(
        expenseId = null,
        groupId = "g1",
        description = description,
        categoryName = categoryName,
        occurredAtEpochMillis = null,
        categoryId = "food",
        subCategoryId = null,
        note = note,
        currency = "MMK",
        amountMinor = 12_000,
        rate = null,
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        weights = mapOf("me" to 1L, "aye" to 1L),
        linkTransactionId = null,
        baseVersionId = null,
    )

    // Save expense

    @Test
    fun aNewExpenseIsSavedNowUnderItsDescription() = runTest {
        val result = SaveSharedExpenseUseCase(repository, clock)(expense())

        assertEquals(SaveExpenseResult.Saved("new-expense", "Dinner"), result)
        val draft = repository.savedExpenses.single()
        assertEquals("Dinner", draft.description)
        assertEquals(now.toEpochMilliseconds(), draft.occurredAtEpochMillis)
    }

    @Test
    fun aBlankDescriptionIsSavedUnderTheCategorysName() = runTest {
        val result = SaveSharedExpenseUseCase(repository, clock)(expense(description = "  "))

        assertEquals(SaveExpenseResult.Saved("new-expense", "Food & drink"), result)
        assertEquals("Food & drink", repository.savedExpenses.single().description)
    }

    @Test
    fun theDescriptionAndNoteAreCutToTheirLimits() = runTest {
        SaveSharedExpenseUseCase(repository, clock)(expense(description = "d".repeat(600), note = "n".repeat(2_100)))

        val draft = repository.savedExpenses.single()
        assertEquals(SharedTextLimits.DESCRIPTION, draft.description.length)
        assertEquals(SharedTextLimits.NOTE, draft.note?.length)
    }

    @Test
    fun anExpenseAnotherPhoneChangedComesBackAsChanged() = runTest {
        repository.failNext = ExpenseChanged(latestVersionId = "v9", removed = true)

        assertEquals(SaveExpenseResult.Changed("v9", removed = true), SaveSharedExpenseUseCase(repository, clock)(expense()))
    }

    @Test
    fun aWriteThatFailsComesBackAsFailed() = runTest {
        repository.failNext = IllegalStateException("disk full")

        assertEquals(SaveExpenseResult.Failed, SaveSharedExpenseUseCase(repository, clock)(expense()))
    }

    @Test
    fun aCancelledSaveIsNotAFailure() = runTest {
        repository.failNext = CancellationException("screen left")

        assertFailsWith<CancellationException> { SaveSharedExpenseUseCase(repository, clock)(expense()) }
    }

    // Payments and the log

    @Test
    fun aPaymentWithNoTimeIsRecordedNow() = runTest {
        val id = RecordPaymentUseCase(repository, clock)(PaymentInput("g1", "aye", "me", Satang.ofMinorUnits(6_000), PaymentMethod.Cash, null))

        assertEquals("new-payment", id.getOrThrow())
        assertEquals(now.toEpochMilliseconds(), repository.payments.single().occurredAtEpochMillis)
    }

    @Test
    fun aPaymentThatFailsIsAFailedResult() = runTest {
        repository.failNext = IllegalStateException("offline")

        assertTrue(RecordPaymentUseCase(repository, clock)(PaymentInput("g1", "aye", "me", Satang.ofMinorUnits(6_000), PaymentMethod.Cash, 1L)).isFailure)
    }

    @Test
    fun removalsAndUndosReachTheLog() = runTest {
        RemoveSharedExpenseUseCase(repository)("e1")
        RestoreSharedExpenseUseCase(repository)("e1")
        RemovePaymentUseCase(repository)("p1")
        PutBackPaymentUseCase(repository)("p1")

        assertEquals(listOf("removeExpense e1", "restoreExpense e1", "removePayment p1", "putBackPayment p1"), repository.calls)
    }

    // Repeats

    private fun rent(kind: RepeatKind = RepeatKind.Rent) = RepeatInput(
        ruleId = null,
        groupId = "g1",
        kind = kind,
        frequency = RepeatFrequency.Monthly,
        start = LocalDate(2026, 12, 1),
        end = RepeatEnd.Never,
        remind = true,
        description = "  Rent  ",
        amountMinor = 500_000,
        payerMemberId = "me",
        categories = listOf(Category(id = "food", name = "Food", icon = "restaurant"), Category(id = "bills", name = "Bills", icon = "receipt")),
    )

    private fun runDueRepeats() = RunDueRepeatsUseCase(repository, NoReminders, words = Words, clock = clock)

    @Test
    fun aRepeatIsSplitEquallyInTheGroupsMoneyAndRepeatsRunAfter() = runTest {
        assertTrue(SaveRepeatUseCase(repository, runDueRepeats(), clock)(rent()).isSuccess)

        val rule = repository.savedRepeats.single()
        assertEquals(mapOf("me" to 1L, "aye" to 1L), rule.weights)
        assertEquals("MMK", rule.currency)
        assertEquals("Rent", rule.description)
        assertEquals("bills", rule.categoryId)
        assertEquals(now.toEpochMilliseconds(), rule.createdAtEpochMillis)
        assertEquals(listOf(LocalDate(2026, 11, 20)), repository.dueRepeatDays)
    }

    @Test
    fun aRepeatThatFailsToSaveRunsNothing() = runTest {
        repository.failNext = IllegalStateException("disk full")

        assertTrue(SaveRepeatUseCase(repository, runDueRepeats(), clock)(rent()).isFailure)
        assertTrue(repository.dueRepeatDays.isEmpty())
    }

    @Test
    fun aRepeatInAGroupThatHasGoneIsNotSaved() = runTest {
        assertTrue(SaveRepeatUseCase(repository, clock = clock)(rent().copy(groupId = "gone")).isFailure)
        assertTrue(repository.savedRepeats.isEmpty())
    }

    @Test
    fun aStoppedRepeatLinesUpTheRemindersAgain() = runTest {
        StopRepeatUseCase(repository, runDueRepeats())("r1")

        assertEquals(listOf("stopRepeat r1"), repository.calls)
        assertEquals(listOf(LocalDate(2026, 11, 20)), repository.dueRepeatDays)
    }

    // Remove group

    private val exits = RecordingExits()

    @Test
    fun aGroupOnThisPhoneIsDeletedHereAndRemindersRunAgain() = runTest {
        assertEquals(RemoveGroupResult.Removed, RemoveGroupUseCase(repository, exits, runDueRepeats())("g1", dropUnsent = false))

        assertEquals(listOf("deleteGroup g1"), repository.calls)
        assertEquals(listOf(LocalDate(2026, 11, 20)), repository.dueRepeatDays)
    }

    @Test
    fun theCreatorDeletesASharedGroupForEveryoneAndOthersLeave() = runTest {
        repository.snapshot.value = SharedSnapshot.Empty.copy(
            groups = listOf(flat.copy(id = "mine", remote = true, creatorUid = "uid-me"), flat.copy(id = "theirs", remote = true, creatorUid = "uid-aye")),
        )
        val remove = RemoveGroupUseCase(repository, exits)

        assertEquals(RemoveGroupResult.Removed, remove("mine", dropUnsent = false))
        assertEquals(RemoveGroupResult.Removed, remove("theirs", dropUnsent = true))
        assertEquals(listOf("deleteForEveryone mine", "leave theirs dropUnsent=true"), exits.calls)
    }

    @Test
    fun changesStillWaitingAskBeforeLeaving() = runTest {
        repository.snapshot.value = SharedSnapshot.Empty.copy(groups = listOf(flat.copy(remote = true, creatorUid = "uid-aye")))
        exits.failNext = ChangesWaiting(3)

        assertEquals(RemoveGroupResult.Waiting(3), RemoveGroupUseCase(repository, exits)("g1", dropUnsent = false))
    }

    @Test
    fun aSharedGroupInABuildWithoutSyncFails() = runTest {
        repository.snapshot.value = SharedSnapshot.Empty.copy(groups = listOf(flat.copy(remote = true, creatorUid = "uid-aye")))

        assertIs<RemoveGroupResult.Failed>(RemoveGroupUseCase(repository, exits = null)("g1", dropUnsent = false))
    }

    @Test
    fun aGroupThatHasGoneAsksNothing() = runTest {
        assertEquals(RemoveGroupResult.Gone, RemoveGroupUseCase(repository, exits)("gone", dropUnsent = false))
        assertTrue(repository.calls.isEmpty() && exits.calls.isEmpty())
    }

    private class RecordingExits : GroupExits {
        val calls = mutableListOf<String>()
        var failNext: Exception? = null

        override suspend fun deleteForEveryone(groupId: String) {
            failNext?.let {
                failNext = null
                throw it
            }
            calls += "deleteForEveryone $groupId"
        }

        override suspend fun leave(groupId: String, dropUnsent: Boolean) {
            failNext?.let {
                failNext = null
                throw it
            }
            calls += "leave $groupId dropUnsent=$dropUnsent"
        }
    }

    private object NoReminders : RepeatReminders {
        override suspend fun replaceAll(plans: List<ReminderPlan>) = Unit

        override fun allowed() = false
    }

    private object Words : ReminderWords {
        override fun title(description: String) = description

        override fun body(amount: Satang, group: SharedGroup) = group.name
    }
}
