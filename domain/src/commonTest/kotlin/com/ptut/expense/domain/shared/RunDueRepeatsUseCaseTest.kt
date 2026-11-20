package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class RunDueRepeatsUseCaseTest {

    private val group = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null)),
    )

    private val rent = RepeatRule(
        id = "r1",
        groupId = "g1",
        kind = RepeatKind.Rent,
        frequency = RepeatFrequency.Monthly,
        start = LocalDate(2026, 11, 21),
        end = RepeatEnd.Never,
        remind = true,
        active = true,
        lastAdded = null,
        description = "Rent",
        categoryId = null,
        currency = "THB",
        amountMinor = 500_000,
        rate = null,
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        weights = emptyMap(),
        createdAtEpochMillis = 0,
    )

    private val reminders = RecordingReminders()

    // 08:00 in Bangkok on 20 Nov: the rent's reminder, 09:00 the day before 21 Nov, is still ahead.
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-20T01:00:00Z")
    }

    private fun runner(vararg rules: RepeatRule) = RunDueRepeatsUseCase(
        repository = RecordingSharedRepository(SharedSnapshot.Empty.copy(groups = listOf(group), repeats = rules.toList())),
        reminders = reminders,
        words = Words,
        clock = clock,
    )

    @Test
    fun repeatsDueTodayAreAddedOnTheHomeDay() = runTest {
        val repository = RecordingSharedRepository()

        RunDueRepeatsUseCase(repository, reminders, Words, clock)()

        assertEquals(listOf(LocalDate(2026, 11, 20)), repository.dueRepeatDays)
    }

    @Test
    fun aReminderIsPlannedAtNineInBangkokTheDayBefore() = runTest {
        runner(rent)()

        assertEquals(
            listOf(ReminderPlan("r1", "Rent", "5000.00 in Flat 12", Instant.parse("2026-11-20T02:00:00Z").toEpochMilliseconds())),
            reminders.planned.single(),
        )
    }

    @Test
    fun repeatsWithoutAReminderOrAGroupAreLeftOut() = runTest {
        runner(rent.copy(remind = false), rent.copy(id = "r2", groupId = "gone"), rent.copy(id = "r3", active = false))()

        assertEquals(emptyList(), reminders.planned.single())
    }

    @Test
    fun nothingIsPlannedWhenThePhoneBlocksNotifications() = runTest {
        reminders.allowed = false

        runner(rent)()

        assertEquals(emptyList(), reminders.planned)
    }

    private object Words : ReminderWords {
        override fun title(description: String) = description

        override fun body(amount: Satang, group: SharedGroup) = "${amount.toMinorUnits() / 100}.00 in ${group.name}"
    }

    private class RecordingReminders : RepeatReminders {
        var allowed = true
        val planned = mutableListOf<List<ReminderPlan>>()

        override suspend fun replaceAll(plans: List<ReminderPlan>) {
            planned += plans
        }

        override fun allowed() = allowed
    }
}
