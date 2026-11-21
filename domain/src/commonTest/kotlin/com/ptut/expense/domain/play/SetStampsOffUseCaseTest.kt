package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.RecordingSharedRepository
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetStampsOffUseCaseTest {

    private fun repository(playOff: Boolean) = RecordingSharedRepository(
        SharedSnapshot(
            groups = listOf(SharedGroup("g1", "Flat", GroupType.Flat, "home", true, 0, "me", listOf(Member("me", "g1", "Me", null, playOff = playOff)))),
            people = emptyList(),
            expenseVersions = emptyList(),
            payments = emptyList(),
            links = emptyMap(),
        ),
    )

    private fun use(repository: RecordingSharedRepository, groupStamps: Boolean, on: Boolean = true) =
        SetStampsOffUseCase(repository, ObservePlayChoicesUseCase(ObserveAppChoicesUseCase(appSettings(AppChoices(play = PlayChoices(on = on, groupStamps = groupStamps))))))

    @Test
    fun `turning group stamps off reaches a group that still has them on`() = runTest {
        val repository = repository(playOff = false)

        use(repository, groupStamps = false)()

        assertEquals(listOf("setStampsOff true"), repository.calls)
    }

    @Test
    fun `groups that already match are not written again`() = runTest {
        val repository = repository(playOff = true)

        use(repository, groupStamps = false)()

        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun `play off as a whole opts the groups out even with group stamps on`() = runTest {
        val repository = repository(playOff = false)

        use(repository, groupStamps = true, on = false)()

        assertEquals(listOf("setStampsOff true"), repository.calls)
    }
}
