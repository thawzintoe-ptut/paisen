package com.ptut.expense.domain.folders

/**
 * One folder the importer reads slips from.
 *
 * [slipsRead] counts reads, not surviving transactions. A slip that was read and later deleted
 * from the history was still read here, and the row says so — the alternative is a count that
 * drops when a user tidies up and reads as the folder having gone quiet.
 *
 * [checked] is separate from a zero count on purpose. FR-5: a folder that was looked at and held
 * nothing is not the same as one never looked at, and that difference is what a reader needs when
 * the totals seem low.
 */
data class WatchedFolder(
    val id: String,
    val name: String,
    val path: String,
    val slipsRead: Int,
    val checked: Boolean,
    /** False for a folder the app knows about but no longer reads. It keeps its count either way. */
    val watched: Boolean = true,
) {
    /**
     * The badge means exactly one thing: the reader has successfully read at least one slip here.
     * The summary's total is the sum over these, so the two can never disagree.
     */
    val foundSlips: Boolean get() = slipsRead > 0

    /** The three states the design names, in the words it names them. */
    val readingState: FolderReading
        get() = when {
            !checked -> FolderReading.NeverChecked
            foundSlips -> FolderReading.Found
            else -> FolderReading.NothingFound
        }
}

enum class FolderReading { Found, NothingFound, NeverChecked }

/** Sum over the badged rows, which is the figure the summary chip is allowed to show. */
fun List<WatchedFolder>.slipsRead(): Int = filter { it.foundSlips }.sumOf { it.slipsRead }

/** The folders the importer actually reads — the scan's list, and the chip's denominator. */
fun List<WatchedFolder>.watched(): List<WatchedFolder> = filter { it.watched }
