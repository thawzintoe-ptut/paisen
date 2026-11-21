package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.play.GetWheelUseCase
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WhoPaysWheelViewModelTest : ViewModelTest() {

    /** Always the first slice: the wheel's only chance is injected, so the test knows where it stops. */
    private val first = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private fun TestScope.wheel(repository: FakeSharedRepository = FakeSharedRepository()): WhoPaysWheelViewModel {
        val wheel = WhoPaysWheelViewModel("g1", ObserveSharedSnapshotUseCase(repository), GetWheelUseCase(GetHomeTodayUseCase()), random = first)
        backgroundScope.launch { wheel.uiState.collect {} }
        runCurrent()
        return wheel
    }

    @Test
    fun `a spin lands on the slice the chance picks and offers to add the round`() = runTest {
        val wheel = wheel()
        assertNull(assertIs<WheelUiState.Loaded>(wheel.uiState.value).result)

        wheel.spin()
        runCurrent()

        val result = assertNotNull(assertIs<WheelUiState.Loaded>(wheel.uiState.value).result)
        assertEquals("aye", result.memberId)
        assertEquals("Aye pays this round", result.line)
        assertEquals("Add the round, Aye paid", result.addLabel)
    }

    @Test
    fun `turning the fair wheel off makes every slice equal and clears the last spin`() = runTest {
        val wheel = wheel()
        wheel.spin()
        runCurrent()

        wheel.setFair(false)
        runCurrent()

        val state = assertIs<WheelUiState.Loaded>(wheel.uiState.value)
        assertEquals(listOf(1, 1), state.slices.map { it.weight })
        assertNull(state.result)
        assertEquals("Every slice is the same size", state.fairLine)
    }

    @Test
    fun `a pull that moves the slices after a spin keeps the name it landed on`() = runTest {
        val repository = FakeSharedRepository()
        val wheel = wheel(repository)
        wheel.spin()
        runCurrent()
        val landed = assertNotNull(assertIs<WheelUiState.Loaded>(wheel.uiState.value).result).memberId

        // A pull brings a new name that sorts first, so the same spin would now land on it.
        repository.snapshot.value = repository.snapshot.value.copy(
            groups = repository.snapshot.value.groups.map { it.copy(members = listOf(Member(id = "a0", groupId = "g1", name = "Ann", personId = null)) + it.members) },
        )
        runCurrent()

        assertEquals(landed, assertNotNull(assertIs<WheelUiState.Loaded>(wheel.uiState.value).result).memberId)
    }
}
