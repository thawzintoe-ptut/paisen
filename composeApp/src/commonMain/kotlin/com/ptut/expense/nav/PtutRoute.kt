package com.ptut.expense.nav

import kotlinx.serialization.Serializable

/**
 * Every destination the app can reach, as types rather than strings. The serialization plugin
 * turns each one into a route, so a typo is a compile error instead of a blank screen.
 *
 * Grouped under one sealed interface so the set is readable in one place, and so a screen can
 * take a `PtutRoute` rather than an `Any`. Adding a screen means adding an object here and
 * wiring it in [com.ptut.expense.PtutApp]; nothing else has to know.
 */
sealed interface PtutRoute {

    /** The four the bottom bar offers. */
    @Serializable
    data object Today : PtutRoute

    @Serializable
    data object Reports : PtutRoute

    @Serializable
    data object Ledger : PtutRoute

    /**
     * History narrowed to what a Reports bar or category row counted (#220): ISO days [from] to [to],
     * and [categoryId] when a row was tapped. Its own route so the pill keeps Reports lit and back
     * returns there. Every argument is a [String] for the iOS reason [Settings] gives.
     */
    @Serializable
    data class LedgerSlice(val from: String, val to: String, val categoryId: String? = null) : PtutRoute

    /**
     * [focusRow] names the row the reader was sent to, and is null every time but that one.
     *
     * A [String] rather than the enum it stands for: navigation 2.9.2 resolves an enum argument
     * to `UNKNOWN` off Android (`NavTypeConverter.nonAndroid.kt`), so the iOS build would fail on
     * the first tap. `com.ptut.expense.settings.focusedRow` turns it back.
     */
    @Serializable
    data class Settings(val focusRow: String? = null) : PtutRoute

    /**
     * Pushed, not tabbed. The review queue is a job the app hands back to the reader, so it is
     * offered from Home where the totals it would change are visible — matching the handoff's
     * `nav="bar"` and the four labels above.
     */
    @Serializable
    data object Review : PtutRoute

    /**
     * The review queue's detail view, so a card's button has somewhere to go. #14 draws it; until
     * then it is a placeholder, which is still better than a button that does nothing.
     */
    @Serializable
    data class ConfirmSlip(val slipId: String) : PtutRoute

    /**
     * FR-21's record. Pushed from a row on Today and on the Ledger, and the only way to reach
     * the delete (#52) and edit (#53) steps — neither is reachable from a list, on purpose.
     *
     * [transactionId] is a [String] because it is one. The rule the Settings route learned the
     * hard way still applies: navigation resolves anything richer than a primitive to `UNKNOWN`
     * off Android, so a route argument that is not primitive compiles here and crashes on iOS.
     */
    @Serializable
    data class TransactionDetail(val transactionId: String) : PtutRoute

    /**
     * One day's receipt (#120, p09), opened from Home's morning and evening cards. [date] is ISO
     * `yyyy-MM-dd` and a [String] for the same reason as above. Not a pill slot, so the pill hides.
     */
    @Serializable
    data class DayReceipt(val date: String) : PtutRoute

    /**
     * FR-21's correction, pushed from the record and from nowhere else. A list row opens the
     * record; the record is what opens this — so nothing can be changed without being looked at.
     *
     * [transactionId] is a [String] for the reason the two routes above are: navigation resolves
     * anything richer than a primitive to `UNKNOWN` off Android, so a non-primitive argument
     * compiles here and crashes on iOS.
     */
    @Serializable
    data class EditTransaction(val transactionId: String) : PtutRoute

    /**
     * FR-5's picker, pushed from the Settings row that has drawn a chevron since the first
     * handoff and had nothing behind it until now.
     */
    @Serializable
    data object PhotoFolders : PtutRoute

    /**
     * FR-2 and FR-3's schedule, behind the row that has reported it since the first handoff.
     *
     * Reachable on both platforms even though only one has a job to switch. C-4 refuses a *switch*
     * iOS cannot honour, not an explanation — and a row answering a tap with nothing reads as the
     * app having failed, which is the worse of the two.
     */
    @Serializable
    data object BackgroundImport : PtutRoute

    /** NFR-4's retention, which was a fixed constant until the reader was given the choice. */
    @Serializable
    data object KeepFailedSlips : PtutRoute

    /**
     * FR-22's two ends, each on its own screen. They were a picker raised straight off a Settings
     * row, which left an import's result landing over whatever screen came next — and gave the
     * reader nothing to read before a merge they cannot undo.
     */
    @Serializable
    data object ExportData : PtutRoute

    @Serializable
    data object ImportData : PtutRoute

    /**
     * FR-19's day boundary. It offers no picker and never will — every total in the app is bucketed
     * against Asia/Bangkok — so the screen shows the boundary against today's date instead.
     */
    @Serializable
    data object Timezone : PtutRoute

    /** The app's language, English or Burmese, beside Timezone in Settings. */
    @Serializable
    data object Language : PtutRoute

    /**
     * FR-12's list. The shop that triggered a sorting is an implementation detail and no longer
     * has a screen of its own, so this is what Settings opens (#128 took it off History's header).
     */
    @Serializable
    data object Categories : PtutRoute

    /**
     * FR-12, FR-13 and FR-18. [categoryId] is a [String] for the reason every other argument
     * here is a primitive: navigation resolves anything richer to `UNKNOWN` off Android, so a
     * non-primitive compiles here and crashes on iOS. [isNew] opens a draft nothing has stored yet.
     */
    @Serializable
    data class EditCategory(val categoryId: String, val isNew: Boolean = false) : PtutRoute

    /**
     * FR-12's correction, reached from the record a rule filed. [shop] is a [String] like every
     * other argument here: navigation resolves anything richer to `UNKNOWN` off Android.
     */
    @Serializable
    data class ShopRule(val shop: String) : PtutRoute

    @Serializable
    data object Activity : PtutRoute

    /** v2's tab (#136). Shared took History's slot in the pill (#33); History is pushed now. */
    @Serializable
    data object Shared : PtutRoute

    /** #137. Every v2 argument is a [String], for the iOS reason the routes above give. */
    @Serializable
    data class SharedGroup(val groupId: String, val invite: Boolean = false) : PtutRoute

    /**
     * #183, with p29's split options inside it. [expenseId] edits an expense; null adds one.
     * [amount] ("600.00") and [description] prefill a new one from Siri or Quick add (#206).
     */
    @Serializable
    data class AddSharedExpense(
        val groupId: String,
        val expenseId: String? = null,
        val transactionId: String? = null,
        val amount: String? = null,
        val description: String? = null,
    ) : PtutRoute

    /** #184, opened by BALANCES on a group. */
    @Serializable
    data class WhoPaysWhom(val groupId: String) : PtutRoute

    /** #139: a payment between two members of a group. */
    @Serializable
    data class RecordPayment(val groupId: String, val fromMemberId: String, val toMemberId: String) : PtutRoute

    /** #140: every group's timeline, or one group's when [groupId] is set. */
    @Serializable
    data class SharedActivity(val groupId: String? = null) : PtutRoute

    /** #185: set up a repeat in [groupId], or change the one [ruleId] names. */
    @Serializable
    data class RepeatExpense(val groupId: String, val ruleId: String? = null) : PtutRoute

    /** #141: everyone you split with, behind "People >" on Shared. */
    @Serializable
    data object People : PtutRoute

    /** #190: an invite link or a typed code. A task, so the pill hides. */
    @Serializable
    data class Join(val code: String) : PtutRoute

    /** #207: the on-device model's size, download and removal. */
    @Serializable
    data object AssistantModel : PtutRoute

    /** The licences of what ships inside the app: fonts, icons, myanmar-tools and Gemma's terms (#205, #207). */
    @Serializable
    data object Licences : PtutRoute
}
