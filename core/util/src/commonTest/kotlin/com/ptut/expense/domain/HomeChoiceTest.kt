package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeChoiceTest {

    @Test
    fun aNewMoneyKeepsTheZone() {
        val kyat = HomeMoney("MMK", "K", 0)

        val changed = HomeChoice(HomeMoney.Baht, "Asia/Yangon").withMoney(kyat)

        assertEquals(HomeChoice(kyat, "Asia/Yangon"), changed)
        assertEquals("Asia/Yangon", changed.zone.id)
    }
}
