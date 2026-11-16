package com.ptut.expense.domain.home

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.shared.Rate

/**
 * #41: [RoomHomeBook]'s rules without Room: home changes only while [rows] is 0, and a convert
 * fails when [convertFails]. It sets [Home] as the real book does, because a ViewModel reads
 * `Home.money` right after a change; `ViewModelTest` puts baht back after every test.
 */
class FakeHomeBook(var rows: Int = 0, private var held: HomeChoice = HomeChoice.Default, var convertFails: Boolean = false) : HomeBook {

    val chosen = mutableListOf<HomeChoice>()

    override fun atLaunch(): HomeChoice = held

    override suspend fun reconcile() = Unit

    override suspend fun isEmpty(): Boolean = rows == 0

    /** What #47 was asked to convert, and at what rate. */
    val converted = mutableListOf<Pair<HomeChoice, Rate>>()

    override suspend fun convert(choice: HomeChoice, rate: Rate): Boolean {
        if (convertFails) return false
        held = choice
        converted += choice to rate
        Home.use(choice)
        return true
    }

    override suspend fun choose(choice: HomeChoice): Boolean {
        if (!isEmpty()) return false
        adopt(choice)
        return true
    }

    override suspend fun adopt(choice: HomeChoice) {
        held = choice
        chosen += choice
        Home.use(choice)
    }
}
