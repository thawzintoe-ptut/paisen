package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.format
import com.ptut.expense.domain.play.GetWheelUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.WheelSlice
import com.ptut.expense.domain.shared.landing
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

/** One slice: whose it is, its weight among the others, and the PaiSeN line that explains its size. */
data class WheelSliceUiModel(val memberId: String, val name: String, val initial: String, val weight: Int, val line: String)

/** Where the last spin stopped. [spin] is 0 to 1 round the wheel; the view turns the wheel to it. */
data class WheelSpinUiModel(val turn: Int, val spin: Float, val memberId: String, val line: String, val addLabel: String)

sealed interface WheelUiState {
    data object Missing : WheelUiState

    data class Loaded(
        val title: String,
        val fair: Boolean,
        val fairLabel: String,
        val fairLine: String,
        val slices: List<WheelSliceUiModel>,
        val usual: String?,
        val spinLabel: String,
        /** Null until the first spin; a group of fewer than two cannot spin. */
        val result: WheelSpinUiModel?,
        val tooFew: String?,
    ) : WheelUiState
}

/** #94 (p11): a fair wheel picks who pays a small round. The spin is the only chance in play, and it picks a payer, never a prize (R1). */
class WhoPaysWheelViewModel(
    private val groupId: String,
    observeSnapshot: ObserveSharedSnapshotUseCase,
    private val getWheel: GetWheelUseCase,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val fair = MutableStateFlow(true)
    private val spun = MutableStateFlow<Spin?>(null)

    /** The slices the wheel was last drawn with, so a spin lands on what the reader saw. */
    private var drawn: List<WheelSlice> = emptyList()

    val uiState: StateFlow<WheelUiState> = combine(observeSnapshot(), fair, spun) { snapshot, isFair, spin ->
        val wheel = getWheel(snapshot, groupId, isFair) ?: return@combine WheelUiState.Missing
        drawn = wheel.slices
        val text = AppText.copy.play.group
        val slices = wheel.slices.map { slice ->
            val name = wheel.group.displayName(slice.memberId)
            WheelSliceUiModel(slice.memberId, name, wheel.group.member(slice.memberId)?.initial().orEmpty(), slice.weight, text.wheelSlice(name, slice.roundsPaid))
        }
        // The landed name is fixed at the spin: a pull that moves the weights later does not move it.
        val result = spin?.let { (turn, at, id) ->
            val name = wheel.group.displayName(id)
            WheelSpinUiModel(turn, at, id, text.wheelLanded(name), text.wheelAdd(name))
        }
        WheelUiState.Loaded(
            title = text.wheelTitle,
            fair = isFair,
            fairLabel = text.wheelFair,
            fairLine = if (isFair) text.wheelFairLine else text.wheelEqualLine,
            slices = slices,
            usual = wheel.usual?.let { text.wheelUsual(it.format(wheel.group.moneyFormat())) },
            spinLabel = if (result == null) text.wheelSpin else text.wheelSpinAgain,
            result = result,
            tooFew = text.wheelNoOne.takeIf { slices.size < MIN_SPINNERS },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), WheelUiState.Missing)

    fun spin() {
        val state = uiState.value as? WheelUiState.Loaded ?: return
        if (state.slices.size < MIN_SPINNERS) return
        val at = random.nextFloat()
        val landed = drawn.landing(at) ?: return
        spun.value = Spin((spun.value?.turn ?: 0) + 1, at, landed)
    }

    private data class Spin(val turn: Int, val at: Float, val memberId: String)

    /** Changing the slices makes the last result stale, so it is cleared. */
    fun setFair(on: Boolean) {
        fair.value = on
        spun.value = null
    }
}

private const val MIN_SPINNERS = 2
