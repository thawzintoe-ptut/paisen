package com.ptut.expense.sync

import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.data.repository.toEntity
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SplitMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedWireTest {

    @Test
    fun `a payment sent from a baht group reads back the same`() {
        val batch = LogBatch()

        batch.add("g1", payment.fields(UID, "THB").overTheWire("p1"), WireAmount.key("THB"))

        assertEquals(listOf(payment), batch.payments.map { it.toDomain() })
    }

    @Test
    fun `a kyat amount is skipped by a reader that asks for baht`() {
        val doc = payment.fields(UID, "MMK").overTheWire("p1")
        val asBaht = LogBatch().apply { add("g1", doc, WireAmount.key("THB")) }
        val asKyat = LogBatch().apply { add("g1", doc, WireAmount.key("MMK")) }

        assertFalse(asBaht.isNotEmpty())
        assertEquals(listOf(payment), asKyat.payments.map { it.toDomain() })
    }

    @Test
    fun `a payment to the same person or of nothing is not taken`() {
        val batch = LogBatch()

        batch.add("g1", payment.copy(toMemberId = "b").fields(UID, "THB").overTheWire("p1"), "amountSatang")
        batch.add("g1", payment.copy(amount = Satang.Zero).fields(UID, "THB").overTheWire("p2"), "amountSatang")

        assertFalse(batch.isNotEmpty())
    }

    @Test
    fun `an expense and its split read back the same`() {
        val batch = LogBatch()

        batch.add("g1", expense.fields(UID, "THB").overTheWire("v1"), "amountSatang")

        val (version, shares) = batch.versions.single()
        assertEquals(expense, version.toDomain(shares))
    }

    @Test
    fun `a split that does not add up to its total is not taken`() {
        val short = expense.copy(shares = expense.shares.map { it.copy(amountMinor = 1, satang = Satang(1)) })
        val batch = LogBatch()

        batch.add("g1", short.fields(UID, "THB").overTheWire("v1"), "amountSatang")

        assertFalse(batch.isNotEmpty())
    }

    @Test
    fun `an entry missing a field it needs is not taken`() {
        val batch = LogBatch()

        batch.add("g1", (payment.fields(UID, "THB") - "method").overTheWire("p1"), "amountSatang")
        batch.add("g1", (expense.fields(UID, "THB") - "payerMemberId").overTheWire("v1"), "amountSatang")
        batch.add("g1", (mapOf("kind" to "lottery")).overTheWire("x1"), "amountSatang")

        assertFalse(batch.isNotEmpty())
    }

    @Test
    fun `a payment removal reads back the same`() {
        val removal = PaymentRemoval("x1", "g1", paymentId = "p1", removed = true, authorMemberId = "a", createdAtEpochMillis = 4)
        val batch = LogBatch()

        batch.add("g1", removal.fields(UID).overTheWire("x1"), "amountSatang")

        assertEquals(listOf(removal), batch.removals.map { it.toDomain() })
    }

    @Test
    fun `a repeat reads back the same and keeps the progress made here`() {
        val doc = repeat.fields(UID).overTheWire("r1")
        val local = repeat.copy(remind = false, lastAdded = LocalDate(2026, 3, 1)).toEntity()

        assertEquals(repeat.copy(remind = true), repeatEntity("g1", doc, local = null, nowMillis = 9)?.toDomain())
        assertEquals(repeat.copy(remind = false, lastAdded = LocalDate(2026, 3, 1)), repeatEntity("g1", doc, local, nowMillis = 9)?.toDomain())
    }

    @Test
    fun `a repeat with no payer is not taken`() {
        val doc = (repeat.fields(UID) - "payerMemberId").overTheWire("r1")

        assertNull(repeatEntity("g1", doc, local = null, nowMillis = 9))
    }

    @Test
    fun `a group sends its look and never its money`() {
        val group = SharedGroup("g1", "Flat", GroupType.Trip, "plane", simplifyDebts = false, createdAtEpochMillis = 1, meMemberId = "a", members = emptyList(), currency = "MMK")

        assertEquals(mapOf("name" to "Flat", "type" to "Trip", "icon" to "plane", "simplifyDebts" to false, "createdAt" to 1L), group.fields())
    }

    @Test
    fun `a group type from a newer app is stored as Other`() {
        assertEquals("Trip", knownGroupType("Trip"))
        assertEquals(GroupType.Other.name, knownGroupType("Spaceship"))
    }

    @Test
    fun `an entry names its payer its author both sides and everyone in its split`() {
        val named = expense.fields(UID, "THB").overTheWire("v1").memberIds() + payment.fields(UID, "THB").overTheWire("p1").memberIds()

        assertEquals(setOf("a", "b"), named.toSet())
        assertTrue(mapOf("kind" to "version").overTheWire("v2").memberIds().isEmpty())
    }

    @Test
    fun `a split past the ceiling does not add up`() {
        val (version, shares) = LogBatch().apply { add("g1", expense.fields(UID, "THB").overTheWire("v1"), "amountSatang") }.versions.single()
        val huge = MAX_MINOR_UNITS + 1

        assertTrue(addsUp(version, shares))
        assertFalse(addsUp(version.copy(amountMinor = huge), shares))
        assertFalse(addsUp(version, emptyList()))
    }

    /** Through Firestore's typed JSON and back, as a pull reads what another phone sent. */
    private fun Fields.overTheWire(id: String) = FirestoreDoc(id, id, toFirestoreFields().fromFirestoreFields())

    private val payment = SharedPayment(
        id = "p1",
        groupId = "g1",
        fromMemberId = "b",
        toMemberId = "a",
        amount = Satang(5_000),
        method = PaymentMethod.Cash,
        occurredAtEpochMillis = 2,
        authorMemberId = "b",
        createdAtEpochMillis = 3,
    )

    private val expense = SharedExpense(
        id = "e1",
        versionId = "v1",
        version = 1,
        groupId = "g1",
        description = "Rice",
        occurredAtEpochMillis = 2,
        categoryId = "food",
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = 9_000,
        rate = null,
        satang = Satang(9_000),
        payerMemberId = "a",
        splitMode = SplitMode.Exact,
        shares = listOf(Share("a", 4_500, 4_500, Satang(4_500)), Share("b", 4_500, 4_500, Satang(4_500))),
        authorMemberId = "a",
        createdAtEpochMillis = 3,
        removed = false,
    )

    private val repeat = RepeatRule(
        id = "r1",
        groupId = "g1",
        kind = RepeatKind.Rent,
        frequency = RepeatFrequency.Monthly,
        start = LocalDate(2026, 2, 1),
        end = RepeatEnd.After(3),
        remind = true,
        active = true,
        lastAdded = null,
        description = "Rent",
        categoryId = null,
        currency = "THB",
        amountMinor = 1_200_000,
        rate = null,
        payerMemberId = "a",
        splitMode = SplitMode.Equally,
        weights = mapOf("a" to 1L, "b" to 1L),
        createdAtEpochMillis = 2,
    )
}
