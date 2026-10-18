package com.ptut.expense.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreValuesTest {

    @Test
    fun everyKindOfValueTheAppWritesReadsBackTheSame() {
        val fields: Fields = mapOf(
            "name" to "Chiang Mai",
            "archived" to false,
            "createdAt" to 1_760_000_000_000L,
            "memberUids" to listOf("uid-a", "uid-b"),
            "shares" to listOf(mapOf("memberId" to "m1", "satang" to 12_345L)),
            "inviteCode" to null,
        )

        assertEquals(fields, fields.toFirestoreFields().fromFirestoreFields())
    }

    /** p36: satang never pass through a double, so a large amount keeps every digit. */
    @Test
    fun moneyCrossesAsADecimalStringNotANumber() {
        val satang = 9_007_199_254_740_993L

        val json = mapOf("satang" to satang).toFirestoreFields()

        assertEquals("""{"satang":{"integerValue":"$satang"}}""", json.toString())
    }

    @Test
    fun aDocumentTakesItsIdFromTheEndOfItsName() {
        val response = Json.parseToJsonElement(
            """{"name":"projects/p/databases/(default)/documents/groups/g1/members/m7","fields":{"name":{"stringValue":"Nok"}}}""",
        )

        val doc = response.toDoc()

        assertEquals("m7", doc.id)
        assertEquals("Nok", doc.fields.string("name"))
    }

    @Test
    fun aDocumentWithNoFieldsReadsAsEmpty() {
        val doc = Json.parseToJsonElement("""{"name":"projects/p/databases/(default)/documents/groups/g1"}""").toDoc()

        assertEquals(emptyMap(), doc.fields)
        assertEquals(false, doc.fields.bool("archived"))
    }

    @Test
    fun aValueTheAppNeverWritesReadsBackAsItsText() {
        val value = Json.parseToJsonElement("""{"timestampValue":"2026-10-13T00:00:00Z"}""").jsonObject

        assertEquals("2026-10-13T00:00:00Z", value.fromFirestoreValue())
    }
}
