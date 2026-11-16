package com.ptut.expense.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone

/** #41: what an amount in this ledger is written with. [digits] is how many minor units a whole one has. */
data class HomeMoney(val code: String, val symbol: String, val digits: Int) {
    companion object {
        val Baht = HomeMoney("THB", "฿", 2)
    }
}

/** #41: the ledger's currency and the zone its days start in. A zone this phone does not know reads as Bangkok. */
data class HomeChoice(val money: HomeMoney, val zoneId: String) {
    val zone: TimeZone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.of(BANGKOK) }

    companion object {
        const val BANGKOK = "Asia/Bangkok"
        val Default = HomeChoice(HomeMoney.Baht, BANGKOK)
    }
}

/**
 * #41: the ledger's home, an object for the reason [com.ptut.expense.locale.AppText] is one: the
 * money format, ViewModels and day buckets read it, and none of them lives in a composition. It is
 * set at launch before the first frame, and the shell is rebuilt when it changes.
 */
object Home {
    private val chosen = MutableStateFlow(HomeChoice.Default)
    val choice: StateFlow<HomeChoice> = chosen.asStateFlow()

    val money: HomeMoney get() = chosen.value.money

    val zone: TimeZone get() = chosen.value.zone

    fun use(choice: HomeChoice) {
        chosen.value = choice
    }

    /** Set by Settings just before a change, so the rebuilt shell opens on Settings again. */
    var reopenSettings: Boolean = false
        private set

    fun reopenSettingsAfterChange() {
        reopenSettings = true
    }

    fun settingsReopened() {
        reopenSettings = false
    }
}
