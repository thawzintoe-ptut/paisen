package com.ptut.expense.platform.photos

/** Separates the part that owns an id from the id that part gave out. */
private const val PART_SEPARATOR = ':'

/**
 * Several places slips arrive from, behind the one seam the importer knows.
 *
 * Android has two from #17 onwards — the folder K PLUS writes to, and the pictures the share sheet
 * handed over — and FR-4 falls out of putting both in one list rather than giving each its own run.
 * The same slip down both paths is two candidates carrying the same bytes, so tier-one hashing
 * files it once and counts the second as already seen. A second entry point into [SlipSource] would
 * have had to repeat that rule, and a repeated rule is one that drifts.
 *
 * Every id is prefixed with the part that owns it. [bytesOf] and [delete] arrive long after the
 * scan — from the review queue, from the NFR-4 sweep — carrying nothing but the id the candidate
 * had, and that id has been through Room in the meantime. So the routing has to be written into
 * the id itself rather than worked out from its shape.
 */
class CompositeSlipSource(private val parts: Map<String, SlipSource>) : SlipSource {

    init {
        val unusable = parts.keys.filter { it.isEmpty() || PART_SEPARATOR in it }
        require(unusable.isEmpty()) {
            "A slip source name must not be empty or hold '$PART_SEPARATOR': $unusable"
        }
    }

    /**
     * A part that cannot scan fails the whole run, deliberately. US-8 counts a scan that found
     * nothing as a success, so swallowing the folder's permission failure because the share inbox
     * happened to be empty would report a healthy run that looked at nothing.
     */
    override suspend fun candidates(): List<SlipCandidate> = parts
        .flatMap { (name, part) -> part.candidates().map { it.ownedBy(name) } }
        .sortedBy { it.capturedAtEpochMillis }

    override suspend fun bytesOf(id: String): ByteArray? = route(id) { part, own -> part.bytesOf(own) }

    override suspend fun delete(id: String) {
        route(id) { part, own -> part.delete(own) }
    }

    /**
     * Null for an id no part claims — a prefix from a build that bound a different set of parts.
     * The interface already says a picture that has gone is not an error, and this is that case.
     */
    private suspend fun <T> route(id: String, use: suspend (SlipSource, String) -> T): T? {
        val part = parts[id.substringBefore(PART_SEPARATOR, missingDelimiterValue = "")] ?: return null
        return use(part, id.substringAfter(PART_SEPARATOR))
    }

    private fun SlipCandidate.ownedBy(name: String) = copy(id = "$name$PART_SEPARATOR$id")
}
