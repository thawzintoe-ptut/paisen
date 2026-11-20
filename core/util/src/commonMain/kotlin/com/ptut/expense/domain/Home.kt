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

/**
 * #41: the ledger's currency and the zone its days start in. A zone this phone does not know is
 * read as Bangkok, and [zoneId] says so, so the Timezone screen and a backup carry the zone in use.
 */
data class HomeChoice(val money: HomeMoney, val zoneId: String) {
    val zone: TimeZone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.of(BANGKOK) }

    /** A new money keeps the zone: the days stay where they are, only how amounts are written changes. */
    fun withMoney(money: HomeMoney): HomeChoice = copy(money = money)

    companion object {
        const val BANGKOK = "Asia/Bangkok"
        val Default = HomeChoice(HomeMoney.Baht, BANGKOK)

        /** [HomeChoice] for a zone id that may be unknown here: the id this phone will use. */
        fun of(money: HomeMoney, zoneId: String): HomeChoice =
            HomeChoice(money, runCatching { TimeZone.of(zoneId).id }.getOrElse { BANGKOK })
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

    /** At launch, after a reconcile, and on first run: the value alone. */
    fun use(choice: HomeChoice) {
        chosen.value = choice
    }

    /** Set while a Settings screen changes the home, so the rebuilt shell opens on Settings again. */
    var reopenSettings: Boolean = false
        private set

    /**
     * Runs [change] from a Settings screen: the rebuilt shell comes back to Settings when the home
     * moved, and nowhere else when it did not. The one owner of the flag, so a new writer of the
     * home cannot forget it.
     */
    suspend fun <T> fromSettings(change: suspend () -> T): T {
        val before = chosen.value
        reopenSettings = true
        try {
            return change()
        } finally {
            if (chosen.value == before) reopenSettings = false
        }
    }

    fun settingsReopened() {
        reopenSettings = false
    }
}
