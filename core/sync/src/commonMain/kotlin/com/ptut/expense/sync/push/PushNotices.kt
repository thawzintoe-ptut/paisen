package com.ptut.expense.sync.push

import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.LogOrder
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.SharedCopy

/**
 * The words for each change a member makes, in both languages at once. [actor] is the name this
 * phone's member carries in the group, which is the name the other phones show for it.
 */
class PushNotices(
    private val groupName: String,
    private val actor: String,
    private val members: List<Member> = emptyList(),
) {

    fun expense(version: SharedExpense, history: List<SharedExpense>): PushNotice {
        val previous = history.filter { it.id == version.id && LogOrder.compare(it, version) < 0 }.maxWithOrNull(LogOrder)
        val amount = version.satang.formatCompact()
        return notice { shared ->
            val push = shared.push
            val what = version.description.trim().ifEmpty { push.anExpense }
            when {
                previous == null && version.cause == ExpenseCause.REPEAT -> push.repeatAdded(what, amount)
                previous == null -> push.added(actor, what, amount)
                version.removed -> push.removed(actor, what)
                previous.removed -> push.putBack(actor, what)
                else -> push.edited(actor, what)
            }
        }
    }

    fun payment(payment: SharedPayment): PushNotice = notice { shared ->
        val nameOf = { id: String -> members.firstOrNull { it.id == id }?.name ?: shared.someone }
        shared.push.paid(nameOf(payment.fromMemberId), nameOf(payment.toMemberId), payment.amount.formatCompact())
    }

    fun paymentRemoval(removal: PaymentRemoval): PushNotice =
        notice { shared -> if (removal.removed) shared.push.paymentRemoved(actor) else shared.push.paymentPutBack(actor) }

    fun repeat(rule: RepeatRule, isNew: Boolean): PushNotice = notice { shared ->
        when {
            isNew -> shared.push.repeatSetUp(actor, rule.description)
            !rule.active -> shared.push.repeatStopped(actor, rule.description)
            else -> shared.push.repeatChanged(actor, rule.description)
        }
    }

    fun personAdded(name: String): PushNotice = notice { it.push.addedPerson(actor, name) }

    fun personRemoved(name: String): PushNotice = notice { it.push.removedPerson(actor, name) }

    fun nameChanged(): PushNotice = notice { it.push.changedName(actor) }

    fun renamed(): PushNotice = notice { it.push.renamed(actor, groupName) }

    fun joined(): PushNotice = notice { it.push.joined(actor) }

    fun left(): PushNotice = notice { it.push.left(actor) }

    fun deleted(): PushNotice = notice { it.push.deleted(actor) }

    private fun notice(sentence: (SharedCopy) -> String) = PushNotice(
        title = groupName,
        en = sentence(Language.English.copy.shared),
        my = sentence(Language.Burmese.copy.shared),
    )
}
