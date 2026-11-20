package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.runSuspendCatching
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** What the repeat sheet hands over: the form read into numbers, and the expense categories it offers. */
data class RepeatInput(
    /** Null for a new repeat. */
    val ruleId: String?,
    val groupId: String,
    val kind: RepeatKind,
    val frequency: RepeatFrequency,
    val start: LocalDate,
    val end: RepeatEnd,
    /** Already false when the phone refused notifications (#182). */
    val remind: Boolean,
    val description: String,
    val amountMinor: Long,
    val payerMemberId: String,
    val categories: List<Category>,
)

/**
 * #182: a repeat split equally between everyone still in the group, in the group's money. Once saved,
 * [onSaved] runs and then the reminders are lined up again; a reminder that fails does not undo the save.
 */
class SaveRepeatUseCase(
    private val repository: SharedRepository,
    private val runDueRepeats: RunDueRepeatsUseCase? = null,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(input: RepeatInput, onSaved: suspend () -> Unit = {}): Result<Unit> {
        val group = repository.snapshot.value.group(input.groupId) ?: return Result.failure(IllegalStateException("Group ${input.groupId} has gone"))
        val saved = runSuspendCatching {
            repository.saveRepeat(
                RepeatRule(
                    id = input.ruleId.orEmpty(),
                    groupId = input.groupId,
                    kind = input.kind,
                    frequency = input.frequency,
                    start = input.start,
                    end = input.end,
                    remind = input.remind,
                    active = true,
                    lastAdded = null,
                    description = input.description.trim().take(SharedTextLimits.DESCRIPTION),
                    categoryId = input.kind.categoryIn(input.categories),
                    currency = group.currency,
                    amountMinor = input.amountMinor,
                    rate = null,
                    payerMemberId = input.payerMemberId,
                    splitMode = SplitMode.Equally,
                    weights = group.membersToSplit().associate { it.id to 1L },
                    createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                ),
            )
        }
        if (saved.isSuccess) {
            onSaved()
            runSuspendCatching { runDueRepeats?.invoke() }
        }
        return saved.map { }
    }
}

/** #182: the repeat adds no more dates, and its reminders are lined up again without it. */
class StopRepeatUseCase(
    private val repository: SharedRepository,
    private val runDueRepeats: RunDueRepeatsUseCase? = null,
) {
    suspend operator fun invoke(ruleId: String) {
        repository.stopRepeat(ruleId)
        runSuspendCatching { runDueRepeats?.invoke() }
    }
}
