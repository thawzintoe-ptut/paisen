package com.ptut.expense.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Firestore's REST API wraps every value in its type ("stringValue", "integerValue"…). These turn
 * plain Kotlin values into that shape and back. Money crosses as integerValue, a decimal string
 * in the JSON, so a satang count never passes through a double (p36).
 */
internal typealias Fields = Map<String, Any?>

/** One Firestore document: its id, its full resource name and its fields. */
internal data class FirestoreDoc(val id: String, val name: String, val fields: Fields)

internal fun Fields.toFirestoreFields(): JsonObject = buildJsonObject {
    forEach { (key, value) -> put(key, value.toFirestoreValue()) }
}

internal fun Any?.toFirestoreValue(): JsonObject = buildJsonObject {
    when (val value = this@toFirestoreValue) {
        null -> put("nullValue", JsonNull)

        is String -> put("stringValue", value)

        is Boolean -> put("booleanValue", value)

        is Int -> put("integerValue", value.toString())

        is Long -> put("integerValue", value.toString())

        is Instant -> put("timestampValue", value.toString())

        is List<*> -> put("arrayValue", buildJsonObject { put("values", buildJsonArray { value.forEach { add(it.toFirestoreValue()) } }) })

        is Map<*, *> -> put(
            "mapValue",
            buildJsonObject {
                put("fields", buildJsonObject { value.forEach { (k, v) -> put(k.toString(), v.toFirestoreValue()) } })
            },
        )

        else -> error("Firestore cannot hold a ${value::class.simpleName}")
    }
}

internal fun JsonObject.fromFirestoreFields(): Fields = mapValues { (_, value) -> value.jsonObject.fromFirestoreValue() }

/** Values this app never writes (doubles, timestamps, references) read back as their text. */
internal fun JsonObject.fromFirestoreValue(): Any? {
    val (type, raw) = entries.firstOrNull() ?: return null
    return when (type) {
        "nullValue" -> null
        "stringValue" -> raw.jsonPrimitive.content
        "booleanValue" -> raw.jsonPrimitive.booleanOrNull
        "integerValue" -> raw.jsonPrimitive.content.toLongOrNull()
        "arrayValue" -> (raw.jsonObject["values"] as? JsonArray).orEmpty().map { it.jsonObject.fromFirestoreValue() }
        "mapValue" -> (raw.jsonObject["fields"] as? JsonObject)?.fromFirestoreFields().orEmpty()
        else -> (raw as? JsonPrimitive)?.content
    }
}

/** A document out of a REST response: "projects/p/databases/(default)/documents/groups/abc". */
internal fun JsonElement.toDoc(): FirestoreDoc {
    val json = jsonObject
    val name = json["name"]?.jsonPrimitive?.content.orEmpty()
    return FirestoreDoc(
        id = name.substringAfterLast('/'),
        name = name,
        fields = (json["fields"] as? JsonObject)?.fromFirestoreFields().orEmpty(),
    )
}

internal fun Fields.string(key: String): String? = this[key] as? String

internal fun Fields.long(key: String): Long? = this[key] as? Long

internal fun Fields.int(key: String): Int? = (this[key] as? Long)?.toInt()

internal fun Fields.bool(key: String): Boolean = this[key] as? Boolean ?: false

@Suppress("UNCHECKED_CAST")
internal fun Fields.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

@Suppress("UNCHECKED_CAST")
internal fun Any?.asFields(): Fields = this as? Map<String, Any?> ?: emptyMap()

internal fun JsonElement.longOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

internal fun JsonElement.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray)?.jsonArray.orEmpty()
