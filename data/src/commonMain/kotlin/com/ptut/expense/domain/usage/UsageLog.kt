package com.ptut.expense.domain.usage

/**
 * #115: the launch numbers, as events with a name and small whole-number flags. There is no field
 * for text or money, so no event can carry an amount, a shop, a reference or a person's name.
 */
enum class UsageEvent(val wire: String) {
    SlipFiled("slip_filed"),
    SlipChecked("slip_checked"),
    ExpenseAdded("expense_added"),
    JarMoneyIn("jar_money_in"),
    GroupCreated("group_created"),
    PaymentRecorded("payment_recorded"),

    /** Sent at most once a day, however often Today opens. */
    TodayOpened("today_opened"),
    /** Not `session_start`: GA4 keeps that name for itself and drops an event sent under it. */
    AppStarted("app_started"),

    /** One per crash report written since the last launch; the report itself stays on the phone. */
    AppCrashed("app_crashed"),
}

/** The few yes/no facts an event may carry, each 0 or 1. */
enum class UsageFlag(val wire: String) {
    /** slip_filed: through Check slip rather than by itself. */
    Checked("checked"),

    /** slip_checked: the amount or the shop was changed before Confirm. */
    Edited("edited"),

    /** jar_money_in: from a transfer slip rather than "I moved it". */
    FromSlip("from_slip"),
}

interface UsageLog {
    /** Never throws and never waits: a count that cannot be sent is dropped. */
    fun log(event: UsageEvent, flags: Map<UsageFlag, Boolean> = emptyMap())

    /** Whether this build sends anything at all, so Settings can leave out a switch that does nothing. */
    val sends: Boolean get() = false

    companion object {
        /** A build or a test graph with nowhere to send. */
        val None: UsageLog = object : UsageLog {
            override fun log(event: UsageEvent, flags: Map<UsageFlag, Boolean>) = Unit
        }
    }
}
