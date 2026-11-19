package com.ptut.expense.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** What a query returned, and when the server read it, which the next pull continues from. */
internal data class Pulled(val docs: List<FirestoreDoc>, val readTimeEpochMillis: Long?)

/** A pull starts this long before its watermark, so a write committed right at it is never missed. */
internal const val PULL_OVERLAP_MILLIS = 30_000L

/** Where a group's next pull starts, or null for a group never pulled, which takes everything. */
internal fun pullSince(lastSyncedAtEpochMillis: Long?): Long? = lastSyncedAtEpochMillis?.let { it - PULL_OVERLAP_MILLIS }

/** `runQuery`'s body: the documents in [collection] where [field] [op] [value]. */
internal fun structuredQuery(collection: String, field: String, op: String, value: Any?): JsonObject = buildJsonObject {
    put(
        "structuredQuery",
        buildJsonObject {
            put("from", buildJsonArray { add(buildJsonObject { put("collectionId", collection) }) })
            put(
                "where",
                buildJsonObject {
                    put(
                        "fieldFilter",
                        buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", field) })
                            put("op", op)
                            put("value", value.toFirestoreValue())
                        },
                    )
                },
            )
        },
    )
}

/**
 * A commit write that sets [fields] and has the server stamp [stamp] with the commit time, so every
 * phone agrees on what is "newer". [create] refuses a document already there; otherwise only
 * [fields] change, whether or not the document exists.
 */
internal fun stampedWrite(name: String, fields: Fields, stamp: String, create: Boolean): JsonObject = buildJsonObject {
    put(
        "update",
        buildJsonObject {
            put("name", name)
            put("fields", fields.toFirestoreFields())
        },
    )
    if (create) {
        put("currentDocument", buildJsonObject { put("exists", false) })
    } else {
        put("updateMask", fieldMaskJson(fields))
    }
    put(
        "updateTransforms",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("fieldPath", stamp)
                    put("setToServerValue", "REQUEST_TIME")
                },
            )
        },
    )
}

/**
 * One write that takes [values] out of the array [field] and sets [fields] on an existing document,
 * so the rules see both changes as one update.
 */
internal fun removeAndSetWrite(name: String, field: String, values: List<String>, fields: Fields): JsonObject = buildJsonObject {
    put(
        "update",
        buildJsonObject {
            put("name", name)
            put("fields", fields.toFirestoreFields())
        },
    )
    put("updateMask", fieldMaskJson(fields))
    put("updateTransforms", buildJsonArray { add(arrayTransform(field, values, "removeAllFromArray")) })
    put("currentDocument", buildJsonObject { put("exists", true) })
}

/** One field transform that adds or takes out [values] in the array [field]. */
internal fun arrayTransform(field: String, values: List<String>, transform: String): JsonObject = buildJsonObject {
    put("fieldPath", field)
    put(transform, buildJsonObject { put("values", buildJsonArray { values.forEach { add(it.toFirestoreValue()) } }) })
}

/** A write's `updateMask`: only [fields]' keys change. */
internal fun fieldMaskJson(fields: Fields): JsonObject = buildJsonObject { put("fieldPaths", buildJsonArray { fields.keys.forEach { add(JsonPrimitive(it)) } }) }

/** When the server read a `runQuery` answer, from its first element; null for an empty answer. */
internal fun readTimeOf(results: JsonElement): Long? = results.arrayOrEmpty().firstOrNull()
    ?.jsonObject?.get("readTime")?.jsonPrimitive?.content
    ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
