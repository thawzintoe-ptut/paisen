package com.ptut.expense.domain.settings

/** #121: the morning recap and evening receipt by the clock. */
class SetMoodByTimeOfDayUseCase(private val settings: AppSettings) {
    operator fun invoke(on: Boolean) = settings.setMoodByTimeOfDay(on)
}

/** #123: the nav pill frosted over the list, or drawn solid. */
class SetTransparencyUseCase(private val settings: AppSettings) {
    operator fun invoke(on: Boolean) = settings.setTransparency(on)
}

/** #20: Monday's look back at last week on Today. */
class SetWeeklyCheckInUseCase(private val settings: AppSettings) {
    operator fun invoke(on: Boolean) = settings.setWeeklyCheckIn(on)
}

/** #26: your name as slips print it; null or blank starts learning it again. */
class SetSlipOwnerUseCase(private val settings: AppSettings) {
    operator fun invoke(name: String?) = settings.setSlipOwner(name)
}

/**
 * #25: on only after the phone's own check passes, so a failed check cannot lock anyone out.
 * True once the lock is on.
 */
class EnableAppLockUseCase(private val settings: AppSettings) {
    suspend operator fun invoke(authenticate: suspend () -> Boolean): Boolean {
        if (!authenticate()) return false
        settings.setAppLock(true)
        return true
    }
}

/** #25: off at once and with no check, since turning it off cannot lock anyone out. */
class DisableAppLockUseCase(private val settings: AppSettings) {
    operator fun invoke() = settings.setAppLock(false)
}
