package com.ptut.expense.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FirestoreQueriesTest {

    @Test
    fun aChangedSinceQueryAsksForTimestampsAfterTheWatermark() {
        val json = structuredQuery("entries", "syncedAt", "GREATER_THAN", Instant.fromEpochMilliseconds(1_760_000_000_000L))

        assertEquals(
            """{"structuredQuery":{"from":[{"collectionId":"entries"}],"where":{"fieldFilter":{"field":{"fieldPath":"syncedAt"},"op":"GREATER_THAN","value":{"timestampValue":"2025-10-09T08:53:20Z"}}}}}""",
            json.toString(),
        )
    }

    @Test
    fun aStampedCreateRefusesAnExistingDocumentAndLetsTheServerSetTheTime() {
        val write = stampedWrite("projects/p/databases/(default)/documents/groups/g1/entries/e1", mapOf("kind" to "version"), "syncedAt", create = true)

        assertEquals(
            """{"update":{"name":"projects/p/databases/(default)/documents/groups/g1/entries/e1","fields":{"kind":{"stringValue":"version"}}},""" +
                """"currentDocument":{"exists":false},"updateTransforms":[{"fieldPath":"syncedAt","setToServerValue":"REQUEST_TIME"}]}""",
            write.toString(),
        )
    }

    @Test
    fun aStampedUpsertChangesOnlyItsFieldsWhetherOrNotTheDocumentExists() {
        val write = stampedWrite("projects/p/databases/(default)/documents/groups/g1/repeats/r1", mapOf("active" to false), "syncedAt", create = false)

        assertEquals(
            """{"update":{"name":"projects/p/databases/(default)/documents/groups/g1/repeats/r1","fields":{"active":{"booleanValue":false}}},""" +
                """"updateMask":{"fieldPaths":["active"]},"updateTransforms":[{"fieldPath":"syncedAt","setToServerValue":"REQUEST_TIME"}]}""",
            write.toString(),
        )
    }

    @Test
    fun aDocumentBodyCarriesItsFieldsAlone() {
        assertEquals("""{"fields":{"name":{"stringValue":"Flat"},"archived":{"booleanValue":false}}}""", documentBody(mapOf("name" to "Flat", "archived" to false)))
    }

    @Test
    fun aPatchMaskNamesEachFieldInTheQuery() {
        assertEquals("updateMask.fieldPaths=name&updateMask.fieldPaths=a%20b", fieldMaskQuery(mapOf("name" to "Flat", "a b" to 1L)))
    }

    @Test
    fun aWriteMaskListsTheFieldPaths() {
        assertEquals("""{"fieldPaths":["claimedByUid","promptPay"]}""", fieldMaskJson(mapOf("claimedByUid" to null, "promptPay" to null)).toString())
    }

    @Test
    fun aCommitBodyHoldsItsWritesInOrder() {
        val writes = listOf(fieldMaskJson(mapOf("a" to 1L)), fieldMaskJson(mapOf("b" to 2L)))

        assertEquals("""{"writes":[{"fieldPaths":["a"]},{"fieldPaths":["b"]}]}""", commitBody(writes))
    }

    @Test
    fun theReadTimeComesFromTheFirstResultEvenWhenNothingMatched() {
        val empty = Json.parseToJsonElement("""[{"readTime":"2026-10-15T14:03:12.123456789Z"}]""")
        val matched = Json.parseToJsonElement(
            """[{"document":{"name":"projects/p/databases/(default)/documents/groups/g1/entries/e1"},"readTime":"2026-10-15T14:03:12.500Z"}]""",
        )

        assertEquals(Instant.parse("2026-10-15T14:03:12.123Z").toEpochMilliseconds(), readTimeOf(empty))
        assertEquals(Instant.parse("2026-10-15T14:03:12.500Z").toEpochMilliseconds(), readTimeOf(matched))
        assertNull(readTimeOf(Json.parseToJsonElement("[]")))
    }

    @Test
    fun aGroupNeverPulledTakesEverythingAndOneAlreadyPulledTakesFromJustBeforeItsWatermark() {
        assertNull(pullSince(null))
        assertEquals(1_760_000_000_000L - PULL_OVERLAP_MILLIS, pullSince(1_760_000_000_000L))
    }

    @Test
    fun anIdThisAppMakesGoesIntoAUrlUnchanged() {
        val path = "groups/3f2b8c1e-9a4d-4e7b-8c1a-2d3e4f5a6b7c/entries/repeat:r1:2026-03-01"

        assertEquals(path, urlPath(path))
    }

    @Test
    fun anIdTheServerSentCannotReadAsAQueryOrAnotherPath() {
        assertEquals("groups/a%3Fx=1%23b%25", urlPath("groups/a?x=1#b%"))
    }
}
