package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupHoldingTest {

    private val group = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null)),
    )

    private val dinner = SharedExpense(
        id = "e1",
        versionId = "v1",
        version = 1,
        groupId = "g1",
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = 90_000,
        rate = null,
        satang = Satang(90_000),
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        shares = listOf(Share("me", 1, 90_000, Satang(90_000))),
        authorMemberId = "me",
        createdAtEpochMillis = 0,
        removed = false,
    )

    private val snapshot = SharedSnapshot.Empty.copy(groups = listOf(group), expenseVersions = listOf(dinner), links = mapOf("e1" to "t1"))

    @Test
    fun aSharedTransactionIsHeldByItsExpensesGroup() {
        assertEquals(group, snapshot.groupHolding("t1"))
    }

    @Test
    fun aTransactionNotSharedIsHeldByNoGroup() {
        assertNull(snapshot.groupHolding("t2"))
    }

    @Test
    fun aRemovedExpenseNoLongerHoldsItsTransaction() {
        assertNull(snapshot.copy(expenseVersions = listOf(dinner.copy(removed = true))).groupHolding("t1"))
    }
}
