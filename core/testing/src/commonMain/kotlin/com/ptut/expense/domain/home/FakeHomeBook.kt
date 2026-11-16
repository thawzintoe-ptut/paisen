package com.ptut.expense.domain.home

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.shared.Rate

/** #41: [RoomHomeBook]'s rules without Room: home changes only while [rows] is 0. */
class FakeHomeBook(var rows: Int = 0, private var held: HomeChoice = HomeChoice.Default) : HomeBook {

    val chosen = mutableListOf<HomeChoice>()

    override fun atLaunch(): HomeChoice = held

    override suspend fun reconcile() = Unit

    override suspend fun isEmpty(): Boolean = rows == 0

    /** What #47 was asked to convert, and at what rate. */
    val converted = mutableListOf<Pair<HomeChoice, Rate>>()

    override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean {
        held = choice
        converted += choice to rate
        Home.use(choice)
        return true
    }

    override suspend fun choose(choice: HomeChoice): Boolean {
        if (!isEmpty()) return false
        held = choice
        chosen += choice
        Home.use(choice)
        return true
    }
}
