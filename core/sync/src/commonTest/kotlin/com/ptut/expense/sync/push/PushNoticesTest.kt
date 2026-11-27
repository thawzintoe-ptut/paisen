package com.ptut.expense.sync.push

import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.locale.Language
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushNoticesTest {

    private val notices = PushNotices(groupName = "Trip", actor = "Mali", members = listOf(mali, cara), money = HomeMoney.Baht)

    @Test
    fun `a new expense names its version and amount and carries no words`() {
        val notice = notices.expense(version("v1"), history = listOf(version("v1")))

        assertEquals(PushNotice(PushKind.ExpenseAdded, about = "v1", amount = 32_000, currency = "THB"), notice)
    }

    @Test
    fun `each later version is an edit a removal or a put-back`() {
        val first = version("v1")
        val removed = version("v2", version = 2, removed = true)
        val back = version("v3", version = 3)
        val history = listOf(first, removed, back)

        assertEquals(PushKind.ExpenseRemoved, notices.expense(removed, history).kind)
        assertEquals(PushKind.ExpensePutBack, notices.expense(back, history).kind)
        assertEquals(PushNotice(PushKind.ExpenseEdited, about = "v2"), notices.expense(version("v2", version = 2), listOf(first)))
    }

    @Test
    fun `an expense a repeat added is told as one`() {
        val notice = notices.expense(version("v1", cause = ExpenseCause.REPEAT), history = emptyList())

        assertEquals(PushKind.RepeatAdded, notice.kind)
        assertEquals(32_000L, notice.amount)
    }

    @Test
    fun `a payment names both members by id`() {
        assertEquals(PushNotice(PushKind.Paid, "p1", mali.id, cara.id, 5_000, "THB"), notices.payment(payment))
        assertEquals(PushNotice(PushKind.PaymentPutBack, about = "p1"), notices.paymentRemoval(PaymentRemoval("r1", "g1", "p1", removed = false, mali.id, 4)))
    }

    @Test
    fun `a person is named by the member id that holds the name`() {
        assertEquals("m-cara", notices.personAdded("Cara").about)
        assertNull(notices.personRemoved("Nobody").about)
    }

    @Test
    fun `the wire body holds the group the kind and only the fields that are set`() {
        val wire = notices.joined().toWire("g1")

        assertEquals(setOf("groupId", "kind"), wire.keys)
        assertEquals(JsonPrimitive("joined"), wire["kind"])
        assertEquals(setOf("groupId", "kind", "about", "from", "to"), notices.payment(payment).toWire("g1").keys)
    }

    @Test
    fun `every kind has the wire name the Worker takes`() {
        assertEquals(PushKind.entries, PushKind.entries.map { PushKind.ofWire(it.wire) })
    }

    @Test
    fun `this phone words its own notice in each language`() {
        val sentences = Language.entries.map { notices.sentence(notices.joined(), snapshot(group(mali, cara)), it.copy.shared) }

        assertEquals(Language.entries.map { it.copy.shared.push.joined("Mali") }, sentences)
        assertEquals(Language.entries.size, sentences.toSet().size)
    }
}
