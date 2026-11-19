package com.ptut.expense.confirm

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.slip.SlipField
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.AmountEntry

/**
 * #217: Room answers after the screen is built when Android restores it after killing the app, so
 * the slip is waited for, and a slip that has gone says so instead of leaving a blank form.
 * Saved and Dismissed are where the shell leaves the screen.
 */
enum class ConfirmSlipPhase { Loading, Ready, Saving, Saved, Dismissed, Missing }

/** A slip for exactly what I owe in a shared group: the question, and which debt is picked when several fit. */
data class DebtMatchUiModel(val question: String, val choices: List<String>, val chosen: Int)

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
    /** #26: the slip names you as the recipient, so the payee row reads "Money in from". */
    val moneyIn: Boolean = false,
    /** #22: marked planned before saving; offered for money out in builds with the weekly note. */
    val planned: Boolean = false,
    val offersPlanned: Boolean = false,
    /** The chosen category's own sub-categories. Empty until one with any is chosen (#111). */
    val subCategories: List<SubCategory> = emptyList(),
    val subCategory: SubCategory? = null,
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
    /** Null unless the slip's amount is a debt I owe in a shared group, and until it is answered. */
    val debtMatch: DebtMatchUiModel? = null,
    /** Said once the payment is recorded and the slip stays to be filed as my own spending. */
    val debtPaidNote: String? = null,
    /** #33: the home symbol, or the slip's own ("K") for a slip in another currency, typed in that currency. */
    val currencySymbol: String = Home.money.symbol,
    /** #33: a slip in another money asks for the home money per its rate unit, opened on the last rate saved. */
    val asksRate: Boolean = false,
    val rateText: String = "",
    /** "1,000 K": what one rate is typed for. */
    val rateUnit: String = "",
    /** "Saved as ฿236.00" once the foreign amount and the rate both read. */
    val homeLine: String? = null,
    /** Confirm was pressed on a foreign slip with no rate. Cleared by typing one. */
    val askForRate: Boolean = false,
) {
    val saving: Boolean get() = phase == ConfirmSlipPhase.Saving

    /** True once the row is durable. The shell leaves the screen on it. */
    val saved: Boolean get() = phase == ConfirmSlipPhase.Saved

    /** True once NOT MINE or a paid debt has taken the slip off the queue. The shell leaves on it too. */
    val dismissed: Boolean get() = phase == ConfirmSlipPhase.Dismissed

    /** The screen has finished with the slip, so its leaving the queue is expected. */
    val done: Boolean get() = saving || saved || dismissed

    /**
     * Inline, beside Confirm, rather than a toast: the keyboard covers the toast, and Confirm then
     * seemed to do nothing (#216).
     */
    val categoryHint: String? get() = when {
        askForRate -> currencyCopy().askRate
        askForCategory -> AppText.copy.slips.pickCategory
        else -> null
    }

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
    val canSave: Boolean get() = !reading && !entry.isEmpty && !saving

    /** #72: the slip is still being read, so nothing half-read can be saved or thrown away yet. */
    val reading: Boolean get() = phase == ConfirmSlipPhase.Loading

    val confirmLabel: String get() = if (reading) AppText.copy.slips.reading else AppText.copy.slips.confirm
}
