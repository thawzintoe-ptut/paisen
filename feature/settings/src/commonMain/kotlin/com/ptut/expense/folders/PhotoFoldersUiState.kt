package com.ptut.expense.folders

import com.ptut.expense.domain.folders.FolderReading
import com.ptut.expense.locale.AppText

/**
 * FR-5. The chip's slip figure is the sum over the badged rows and nothing else, so the two can
 * never disagree — [PhotoFoldersViewModel] derives both from one list rather than counting twice.
 */
data class PhotoFoldersUiState(
    val summary: String,
    val rows: List<FolderRowUiModel>,
    val accessGranted: Boolean = true,
    /** Null where the list is already the whole answer, so the screen draws no call to action. */
    val chooseMore: String? = null,
    val chooseMoreExplanation: String = "",
    /** How many folders SAVE would watch: "Save · 2 folders" (p22). */
    val ticked: Int = 0,
    /** True while the ticks differ from what is watched. SAVE is inert otherwise. */
    val canSave: Boolean = false,
)

data class FolderRowUiModel(
    val id: String,
    val name: String,
    val path: String,
    val reading: String,
    val badged: Boolean,
    val watched: Boolean,
    /** The tick as the reader has left it, saved or not. Only SAVE turns it into [watched]. */
    val ticked: Boolean = watched,
    /** "412 images" (p22), or null where the platform did not count. */
    val images: String? = null,
)

/** The three states the design names, in its own words. */
internal fun FolderReading.phrase(slipsRead: Int): String = when (this) {
    FolderReading.Found -> AppText.copy.folders.slipsRead(slipsRead)
    FolderReading.NothingFound -> AppText.copy.folders.noSlipsYet
    FolderReading.NeverChecked -> AppText.copy.folders.notChecked
}
