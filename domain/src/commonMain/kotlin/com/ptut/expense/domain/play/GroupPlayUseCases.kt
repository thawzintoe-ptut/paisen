package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Award
import com.ptut.expense.domain.shared.NextTrip
import com.ptut.expense.domain.shared.ReactionKind
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.TripRecap
import com.ptut.expense.domain.shared.WheelSlice
import com.ptut.expense.domain.shared.allSquareAt
import com.ptut.expense.domain.shared.groupAwards
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.nextTrip
import com.ptut.expense.domain.shared.tripRecap
import com.ptut.expense.domain.shared.usualRound
import com.ptut.expense.domain.shared.wheelSlices
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** #94: the group, its slices in member-id order, and a usual round, or null once the group is gone. */
data class Wheel(val group: SharedGroup, val slices: List<WheelSlice>, val usual: Satang?)

class GetWheelUseCase(private val homeToday: GetHomeTodayUseCase) {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String, fair: Boolean): Wheel? {
        val group = snapshot.group(groupId) ?: return null
        val today = homeToday()
        return Wheel(group, snapshot.wheelSlices(groupId, today, fair), snapshot.usualRound(groupId, today))
    }
}

/** #95 (D10): this account's word on a payment; null takes it back. */
class ReactToPaymentUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, paymentId: String, kind: ReactionKind?) = repository.react(groupId, paymentId, kind)
}

/**
 * #95: once every net in the group is zero, the All square stamp for the payment that did it. Never for
 * a member who opted out (D5), and only from an up-to-date phone, so every phone earns the same one.
 * False when nothing was earned now.
 */
class EarnAllSquareUseCase(private val book: PlayBook, private val homeToday: GetHomeTodayUseCase) {
    suspend operator fun invoke(snapshot: SharedSnapshot, groupId: String): Boolean {
        val group = snapshot.group(groupId) ?: return false
        if (group.meMemberId?.let(group::member)?.playOff == true) return false
        val payment = snapshot.allSquareAt(groupId) ?: return false
        return book.earn(StampKind.AllSquare, "$groupId:$payment", homeToday())
    }
}

/**
 * D5: every group this phone holds a name in follows the "Stamps and awards" switch, and the master
 * switch above it. Run when either moves and on each resume, so a group joined since gets the same
 * choice; writes only what differs.
 */
class SetStampsOffUseCase(private val repository: SharedRepository?, private val choices: ObservePlayChoicesUseCase) {
    suspend operator fun invoke() {
        // A graph with no shared groups, such as a screen test's, has nothing to follow the switch.
        val repository = repository ?: return
        val off = !choices.now().allows(PlayPart.Awards)
        // On a cold start the groups may not be read yet; a short wait beats writing the wrong choice.
        val snapshot = withTimeoutOrNull(SNAPSHOT_WAIT) { repository.snapshot.first { it != SharedSnapshot.Empty } } ?: repository.snapshot.value
        val differs = snapshot.groups.any { group -> group.meMemberId?.let(group::member)?.let { it.playOff != off } == true }
        if (differs) repository.setStampsOff(off)
    }
}

private val SNAPSHOT_WAIT = 2.seconds

/** #97 (D6): any member marks the trip ended today, or opens it again. */
class SetTripEndedUseCase(private val repository: SharedRepository, private val homeToday: GetHomeTodayUseCase) {
    suspend operator fun invoke(groupId: String, ended: Boolean) = repository.setTripEnded(groupId, if (ended) homeToday() else null)
}

/** #96: the month's awards; the caller shows none while the phone is behind (FR-96.3). */
class GetGroupAwardsUseCase {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String, month: String): List<Award> = snapshot.groupAwards(groupId, month)
}

/** #97: a trip's recap with the next-trip estimate, or null with nothing spent. */
class GetTripRecapUseCase {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String): Pair<TripRecap, NextTrip>? {
        val money = snapshot.group(groupId)?.moneyFormat() ?: return null
        return snapshot.tripRecap(groupId)?.let { it to it.nextTrip(money) }
    }
}
