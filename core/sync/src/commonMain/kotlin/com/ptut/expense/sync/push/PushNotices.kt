package com.ptut.expense.sync.push

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.LogOrder
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.locale.SharedCopy

/**
 * The notice for each change a member makes, as ids and amounts. [actor] is the name this phone's
 * member carries in the group; it words [sentence] here, while the other phones find the name by
 * the sender's account.
 */
class PushNotices(
    private val groupName: String,
    private val actor: String,
    private val members: List<Member> = emptyList(),
    /** #48: the group's own money, so a kyat group's notice says K on every member's phone. */
    private val money: HomeMoney = Home.money,
) {

    fun expense(version: SharedExpense, history: List<SharedExpense>): PushNotice {
        val previous = history.filter { it.id == version.id && LogOrder.compare(it, version) < 0 }.maxWithOrNull(LogOrder)
        val kind = when {
            previous == null && version.cause == ExpenseCause.REPEAT -> PushKind.RepeatAdded
            previous == null -> PushKind.ExpenseAdded
            version.removed -> PushKind.ExpenseRemoved
            previous.removed -> PushKind.ExpensePutBack
            else -> PushKind.ExpenseEdited
        }
        val added = kind == PushKind.ExpenseAdded || kind == PushKind.RepeatAdded
        return PushNotice(kind, about = version.versionId, amount = version.satang.toMinorUnits().takeIf { added }, currency = money.code.takeIf { added })
    }

    fun payment(payment: SharedPayment): PushNotice = PushNotice(
        PushKind.Paid,
        about = payment.id,
        from = payment.fromMemberId,
        to = payment.toMemberId,
        amount = payment.amount.toMinorUnits(),
        currency = money.code,
    )

    fun paymentRemoval(removal: PaymentRemoval): PushNotice =
        PushNotice(if (removal.removed) PushKind.PaymentRemoved else PushKind.PaymentPutBack, about = removal.paymentId)

    fun repeat(rule: RepeatRule, isNew: Boolean): PushNotice {
        val kind = when {
            isNew -> PushKind.RepeatSetUp
            !rule.active -> PushKind.RepeatStopped
            else -> PushKind.RepeatChanged
        }
        return PushNotice(kind, about = rule.id)
    }

    fun personAdded(name: String): PushNotice = PushNotice(PushKind.PersonAdded, about = memberNamed(name))

    fun personRemoved(name: String): PushNotice = PushNotice(PushKind.PersonRemoved, about = memberNamed(name))

    fun nameChanged(): PushNotice = PushNotice(PushKind.NameChanged)

    fun renamed(): PushNotice = PushNotice(PushKind.Renamed)

    fun joined(): PushNotice = PushNotice(PushKind.Joined)

    fun left(): PushNotice = PushNotice(PushKind.Left)

    fun deleted(): PushNotice = PushNotice(PushKind.Deleted)

    /** [notice] worded on this phone, for the iOS refresh's local notices about rows others added. */
    fun sentence(notice: PushNotice, snapshot: SharedSnapshot, copy: SharedCopy): String =
        pushSentence(notice, members, snapshot, actor, groupName, copy, money)

    // The callers pass a name; the other phones look the member up by id. Same name, same words.
    private fun memberNamed(name: String): String? = members.firstOrNull { it.name == name }?.id
}
