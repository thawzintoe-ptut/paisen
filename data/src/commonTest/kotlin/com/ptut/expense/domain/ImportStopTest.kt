package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportStopTest {

    private val sentences = StopSentences(
        accessDenied = setOf("photo access is off", "Photos access is off"),
        noAnswer = setOf("the library did not answer"),
        unnamed = "the importer stopped",
    )

    private fun run(outcome: ImportOutcome, reason: String? = null, kind: ImportFailureKind? = null) =
        ImportRun(id = "r", startedAtEpochMillis = 0, finishedAtEpochMillis = 1, outcome = outcome, slipsFound = 0, failureReason = reason, failureKind = kind)

    @Test
    fun nothingStoppedWhenTheNewestFinishedRunWorked() {
        assertNull(listOf(run(ImportOutcome.Success), run(ImportOutcome.Failure, kind = ImportFailureKind.Other)).latestStop(sentences))
        assertNull(emptyList<ImportRun>().latestStop(sentences))
    }

    @Test
    fun aRunStillGoingIsSkippedForTheOneBeforeIt() {
        val runs = listOf(run(ImportOutcome.Running), run(ImportOutcome.Failure, kind = ImportFailureKind.PhotoAccessDenied))

        assertEquals(ImportStopReason.PhotoAccessOff, runs.latestStop(sentences))
    }

    @Test
    fun theFailureKindNamesTheStop() {
        assertEquals(ImportStopReason.LibraryNoAnswer, listOf(run(ImportOutcome.Failure, kind = ImportFailureKind.LibraryNoAnswer)).latestStop(sentences))
        assertEquals(
            ImportStopReason.Other("the folder could not be read"),
            listOf(run(ImportOutcome.Failure, "the folder could not be read", ImportFailureKind.Other)).latestStop(sentences),
        )
    }

    @Test
    fun aFailureWithNoReasonGetsTheUnnamedSentence() {
        assertEquals(ImportStopReason.Other("the importer stopped"), listOf(run(ImportOutcome.Failure, "  ", ImportFailureKind.Other)).latestStop(sentences))
    }

    @Test
    fun aRowFromBeforeSchema23IsToldApartByItsSentence() {
        assertEquals(ImportStopReason.PhotoAccessOff, listOf(run(ImportOutcome.Failure, "Photos access is off")).latestStop(sentences))
        assertEquals(ImportStopReason.LibraryNoAnswer, listOf(run(ImportOutcome.Failure, "the library did not answer")).latestStop(sentences))
        assertEquals(ImportStopReason.Other("disk full"), listOf(run(ImportOutcome.Failure, "disk full")).latestStop(sentences))
    }

    @Test
    fun onlyALibraryThatDidNotAnswerHasNoFix() {
        assertEquals(ImportFix.AllowAllPhotos, ImportStopReason.PhotoAccessOff.fix())
        assertEquals(ImportFix.AllowAllPhotos, ImportStopReason.Other("disk full").fix())
        assertNull(ImportStopReason.LibraryNoAnswer.fix())
    }
}
