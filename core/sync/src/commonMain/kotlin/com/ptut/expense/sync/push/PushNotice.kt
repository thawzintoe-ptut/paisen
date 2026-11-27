package com.ptut.expense.sync.push

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** M6: what changed, by name. The wire names are the Worker's `NOTICE_KINDS`. */
enum class PushKind(val wire: String) {
    ExpenseAdded("expenseAdded"),
    ExpenseEdited("expenseEdited"),
    ExpenseRemoved("expenseRemoved"),
    ExpensePutBack("expensePutBack"),
    RepeatAdded("repeatAdded"),
    Paid("paid"),
    PaymentRemoved("paymentRemoved"),
    PaymentPutBack("paymentPutBack"),
    RepeatSetUp("repeatSetUp"),
    RepeatChanged("repeatChanged"),
    RepeatStopped("repeatStopped"),
    PersonAdded("personAdded"),
    PersonRemoved("personRemoved"),
    NameChanged("nameChanged"),
    Renamed("renamed"),
    Joined("joined"),
    Left("left"),
    Deleted("deleted"),
    ;

    companion object {
        fun ofWire(name: String?): PushKind? = entries.firstOrNull { it.wire == name }
    }
}

/**
 * One change in a shared group, as ids and numbers only (M6). Each receiving phone words it from
 * its own copy of the group, so no text the sender typed reaches another member's lock screen.
 * [about] is the expense version, payment, repeat or member it names; [amount] is whole minor
 * units of [currency].
 */
data class PushNotice(
    val kind: PushKind,
    val about: String? = null,
    val from: String? = null,
    val to: String? = null,
    val amount: Long? = null,
    val currency: String? = null,
) {
    /** The body the Worker takes, for [groupId]. Fields left null are not sent. */
    fun toWire(groupId: String): JsonObject = buildJsonObject {
        put("groupId", groupId)
        put("kind", kind.wire)
        about?.let { put("about", it) }
        from?.let { put("from", it) }
        to?.let { put("to", it) }
    }
}
