package com.ptut.expense.confirm

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.slip.SlipField
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry

/**
 * #217: Room answers after the screen is built when Android restores it after killing the app, so
 * the slip is waited for, and a slip that has gone says so instead of leaving a blank form.
 */
enum class ConfirmSlipPhase { Loading, Ready, Missing }

/**
 * What the confirm screen draws.
 *
 * The measured shape of the nineteen real slips decided the default: eight carry nothing the
 * reader was unsure of, and amount is the rarest doubt at two of nineteen. So the plain, unmarked
 * screen is the common case and [amountUnread] is the exception, not the other way round.
 */
data class ConfirmSlipUiState(
    val merchant: String,
    val date: String,
    val reference: String,
    val entry: AmountEntry,
    /** True when the reader could not make the number out, so the pad started empty. */
    val amountUnread: Boolean,
    /**
     * Fields the parser produced but had to judge. Not the same claim as [amountUnread]: a marked
     * field holds a value, it is just worth checking against the picture before saving.
     */
    val lowConfidence: Set<SlipField>,
    val category: Category?,
    val categories: List<Category>,
    /** The chosen category's own sub-categories. Empty until one with any is chosen (#111). */
    val subCategories: List<SubCategory> = emptyList(),
    val subCategory: SubCategory? = null,
    val saving: Boolean,
    /** True once the row is durable. The shell leaves the screen on it. */
    val saved: Boolean,
    /** True once NOT MINE has taken the slip off the queue. The shell leaves on it too. */
    val dismissed: Boolean = false,
    /** False for a slip that read no shop (#113): it is saved by hand and no rule is made. */
    val shopRead: Boolean = true,
    /** Research finding 2: the shop read as noise (a Thai name), so the screen says why it asks. */
    val nameable: Boolean = false,
    /** The name rows will carry: the slip's own when it read, empty for noise or no shop at all. */
    val shopName: String = "",
    /** The queued slip's id, which is also its picture's id (#118). */
    val slipId: String = "",
    /** p13's line under the picture: the folder and the day, "K PLUS · 14 Aug". */
    val pictureCaption: String = "",
    val phase: ConfirmSlipPhase = ConfirmSlipPhase.Ready,
    /** Confirm was pressed with no category. Cleared by picking one (#216). */
    val askForCategory: Boolean = false,
) {
    /**
     * Inline, beside Confirm, rather than a toast: the keyboard covers the toast, and Confirm then
     * seemed to do nothing (#216).
     */
    val categoryHint: String? get() = if (askForCategory) AppText.copy.slips.pickCategory else null

    /** An unread slip has no date of its own, so the photo's time stands in and says so. */
    val whenLabel: String get() = if (shopRead) AppText.copy.slips.whenLabel else AppText.copy.slips.whenPhotoTime

    /**
     * The badge beside the read amount (p13), from the parser's own flags (#161). It says "read
     * clearly" only when nothing on the slip is marked, so it never claims more than the parser knows.
     */
    val confidence: String
        get() = when {
            amountUnread -> AppText.copy.slips.confidenceNotRead
            lowConfidence.isNotEmpty() -> AppText.copy.slips.confidenceLow
            else -> AppText.copy.slips.confidenceClear
        }

    /**
     * "Amount" until there is something to say about it. Not read beats low confidence when both
     * could apply: an empty pad is the stronger fact, and two clauses on one label is noise.
     */
    val amountLabel: String get() = AppText.copy.common.amount.marked(amountUnread, SlipField.Amount)

    val merchantLabel: String get() = AppText.copy.slips.merchantLabel.marked(field = SlipField.Counterparty)

    val referenceLabel: String get() = AppText.copy.slips.referenceLabel.marked(field = SlipField.Reference)

    private fun String.marked(unread: Boolean = false, field: SlipField): String = when {
        unread -> AppText.copy.slips.labelNotRead(this)
        field in lowConfidence -> AppText.copy.slips.labelLowConfidence(this)
        else -> this
    }

    /**
     * FR-13, and it shows *before* the rule exists. A rule the reader learns about afterwards is
     * one they never agreed to.
     */
    val ruleNote: String
        get() = if (shopRead) AppText.copy.slips.ruleNote(merchant) else AppText.copy.slips.ruleNoteNoShop

    /**
     * The category is deliberately not part of this. The button stays live without one so that
     * pressing it can say what is missing — a dark key with no sentence beside it reads as a
     * broken app, and refusing out loud is this screen's whole job.
     */
    val canSave: Boolean get() = !entry.isEmpty && !saving
}
