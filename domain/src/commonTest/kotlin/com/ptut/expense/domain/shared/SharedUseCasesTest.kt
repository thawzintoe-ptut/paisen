package com.ptut.expense.domain.shared

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedUseCasesTest {

    private val repository = RecordingSharedRepository()

    @Test
    fun aPersonBecomesAnOtherGroupNamedAfterThemWithThemAsItsMember() = runTest {
        val id = CreatePersonGroupUseCase(repository)("  Aye  ", personId = "p1", icon = "person")

        assertEquals("new-group", id)
        assertEquals(listOf(Triple("Aye", GroupType.Other, listOf(NewMember("  Aye  ", "p1")))), repository.createdGroups)
        assertEquals(listOf("person"), repository.createdIcons)
    }

    @Test
    fun aPromptPayIdIsSavedForTheGroupAndNullTakesItOff() = runTest {
        val save = SavePromptPayUseCase(repository)

        save("g1", "pp-id")
        save("g1", null)

        assertEquals(listOf("g1" to "pp-id", "g1" to null), repository.promptPays)
    }
}
