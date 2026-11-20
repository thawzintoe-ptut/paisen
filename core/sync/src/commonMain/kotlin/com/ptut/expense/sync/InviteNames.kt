package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.GroupMemberEntity

/**
 * L4: the invite field listing the names nobody holds, so opening a link writes nothing. One text
 * of "id<TAB>name<LF>" lines, the shape the rules can check line by line with one pattern.
 */
internal const val INVITE_NAMES = "names"

/** The names someone joining may pick: claimed by nobody and not removed, oldest first, within the rules' limits. */
internal fun pickableNames(members: List<GroupMemberEntity>): String {
    val lines = members
        .filter { it.claimedByUid == null && !it.archived && APP_ID.matches(it.id) && it.name.fits() }
        .sortedBy { it.createdAtEpochMillis }
        .map { "${it.id}\t${it.name}\n" }
    var size = 0
    return lines.takeWhile { line -> (size + line.encodeToByteArray().size).also { size = it } <= MAX_NAMES_SIZE }.joinToString("")
}

/**
 * The invite's names, or null for an invite made by a build before L4, which carries none. Any
 * member can write this copy, so a line whose id is not one the app makes is left out.
 */
internal fun invitedNames(invite: Fields): List<Pair<String, String>>? = invite.string(INVITE_NAMES)?.lineSequence()
    ?.mapNotNull { line ->
        val id = line.substringBefore('\t', missingDelimiterValue = "")
        val name = line.substringAfter('\t', missingDelimiterValue = "")
        (id to name).takeIf { APP_ID.matches(id) && name.fits() }
    }
    ?.toList()

/** The shape of every id the app makes: a lowercase UUID. */
private val APP_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

private fun String.fits(): Boolean = length in 1..MAX_NAME_LENGTH && '\t' !in this && '\n' !in this

/** A name's longest, as the rules hold it on every member. */
private const val MAX_NAME_LENGTH = 60

/** The rules' cap on an invite's names, in UTF-8 bytes to stay under it in any script. */
private const val MAX_NAMES_SIZE = 25_000
