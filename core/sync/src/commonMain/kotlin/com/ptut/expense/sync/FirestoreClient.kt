package com.ptut.expense.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** A Firestore call that came back with anything but success. [status] 0 means it never got an answer. */
internal class FirestoreException(val status: Int, message: String) : Exception(message)

/** What shared-group sync asks of Firestore, so a test can stand in for it. [FirestoreClient] is the real one. */
internal interface Firestore {
    suspend fun get(path: String): FirestoreDoc?

    suspend fun create(collection: String, id: String, fields: Fields): Boolean

    suspend fun patch(path: String, fields: Fields)

    suspend fun set(path: String, fields: Fields)

    suspend fun delete(path: String)

    suspend fun whereContains(collection: String, field: String, value: String): List<FirestoreDoc>

    suspend fun changedSince(parent: String, collection: String, field: String, since: Long): Pulled

    suspend fun createStamped(collection: String, id: String, fields: Fields, stamp: String): Boolean

    suspend fun upsertStamped(relative: String, fields: Fields, stamp: String)

    suspend fun list(collection: String): List<FirestoreDoc>

    suspend fun commit(writes: List<JsonObject>)

    fun updateWrite(relative: String, fields: Fields, create: Boolean = false): JsonObject

    fun appendWrite(relative: String, field: String, values: List<String>): JsonObject

    fun removeWrite(relative: String, field: String, values: List<String>): JsonObject
}

/**
 * #188: Firestore over REST (decided 13 Oct), so one Kotlin implementation serves both platforms
 * and no native SDK has to be linked into the iOS app. Every call carries the signed-in account's
 * ID token; the security rules in `firebase/firestore.rules` decide what it may touch.
 */
internal class FirestoreClient(
    private val http: HttpClient,
    projectId: String,
    private val idToken: suspend () -> String,
) : Firestore {
    private val database = "projects/$projectId/databases/(default)"
    private val base = "https://firestore.googleapis.com/v1/$database/documents"

    fun pathOf(relative: String): String = "$database/documents/$relative"

    /** Null when the document is not there. */
    override suspend fun get(path: String): FirestoreDoc? {
        val (status, body) = call(HttpMethod.Get, "$base/$path")
        if (status == NOT_FOUND) return null
        status.orThrow(body)
        return Json.parseToJsonElement(body).toDoc()
    }

    /** False when a document with this id already exists, which a retried upload is allowed to meet. */
    override suspend fun create(collection: String, id: String, fields: Fields): Boolean {
        val (status, body) = call(
            HttpMethod.Post,
            "$base/$collection?documentId=${id.encodeURLParameter()}",
            buildJsonObject { put("fields", fields.toFirestoreFields()) }.toString(),
        )
        if (status == CONFLICT || refusedButThere(status, "$collection/$id")) return false
        status.orThrow(body)
        return true
    }

    /**
     * The rules can answer a create on a document that already exists as a refused update (403)
     * rather than 409. One read tells the two apart, and only on that path.
     */
    private suspend fun refusedButThere(status: Int, relative: String): Boolean = status == FORBIDDEN && get(relative) != null

    /** Changes only [fields]' keys, and only on a document that exists. */
    override suspend fun patch(path: String, fields: Fields) {
        val mask = fields.keys.joinToString("&") { "updateMask.fieldPaths=${it.encodeURLParameter()}" }
        val (status, body) = call(
            HttpMethod.Patch,
            "$base/$path?$mask&currentDocument.exists=true",
            buildJsonObject { put("fields", fields.toFirestoreFields()) }.toString(),
        )
        status.orThrow(body)
    }

    /** Writes [fields] whether or not the document exists yet. */
    override suspend fun set(path: String, fields: Fields) {
        val mask = fields.keys.joinToString("&") { "updateMask.fieldPaths=${it.encodeURLParameter()}" }
        val (status, body) = call(HttpMethod.Patch, "$base/$path?$mask", buildJsonObject { put("fields", fields.toFirestoreFields()) }.toString())
        status.orThrow(body)
    }

    /** Removes a document. One that is not there counts as removed. */
    override suspend fun delete(path: String) {
        val (status, body) = call(HttpMethod.Delete, "$base/$path")
        if (status != NOT_FOUND) status.orThrow(body)
    }

    /** The documents in [collection] whose array [field] holds [value]. */
    override suspend fun whereContains(collection: String, field: String, value: String): List<FirestoreDoc> =
        runQuery(null, structuredQuery(collection, field, "ARRAY_CONTAINS", value)).docs

    /** The documents in [parent]'s [collection] whose server-stamped [field] is later than [since]. */
    override suspend fun changedSince(parent: String, collection: String, field: String, since: Long): Pulled =
        runQuery(parent, structuredQuery(collection, field, "GREATER_THAN", Instant.fromEpochMilliseconds(since)))

    /** Creates [id] in [collection] with [stamp] set to the server's time. False when it is already there. */
    override suspend fun createStamped(collection: String, id: String, fields: Fields, stamp: String): Boolean {
        val write = stampedWrite(pathOf("$collection/$id"), fields, stamp, create = true)
        val (status, body) = call(HttpMethod.Post, "$base:commit", buildJsonObject { put("writes", JsonArray(listOf(write))) }.toString())
        if (status == CONFLICT || (status == BAD_REQUEST && ALREADY_EXISTS in body) || refusedButThere(status, "$collection/$id")) return false
        status.orThrow(body)
        return true
    }

    /** Writes [fields] whether or not the document exists, with [stamp] set to the server's time. */
    override suspend fun upsertStamped(relative: String, fields: Fields, stamp: String) {
        commit(listOf(stampedWrite(pathOf(relative), fields, stamp, create = false)))
    }

    private suspend fun runQuery(parent: String?, query: JsonObject): Pulled {
        val url = (if (parent == null) base else "$base/$parent") + ":runQuery"
        val (status, body) = call(HttpMethod.Post, url, query.toString())
        status.orThrow(body)
        // One element per match, plus a lone readTime when nothing matched.
        val results = Json.parseToJsonElement(body)
        return Pulled(results.arrayOrEmpty().mapNotNull { it.jsonObject["document"]?.toDoc() }, readTimeOf(results))
    }

    /** Every document in a collection, following page tokens. */
    override suspend fun list(collection: String): List<FirestoreDoc> {
        val docs = mutableListOf<FirestoreDoc>()
        var pageToken: String? = null
        do {
            val url = "$base/$collection?pageSize=$PAGE_SIZE" + (pageToken?.let { "&pageToken=${it.encodeURLParameter()}" } ?: "")
            val (status, body) = call(HttpMethod.Get, url)
            if (status == NOT_FOUND) return docs
            status.orThrow(body)
            val json = Json.parseToJsonElement(body).jsonObject
            json["documents"]?.arrayOrEmpty()?.forEach { docs += it.toDoc() }
            pageToken = json["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        return docs
    }

    /** Several writes that land together or not at all, checked by the rules as one request. */
    override suspend fun commit(writes: List<JsonObject>) {
        val (status, body) = call(
            HttpMethod.Post,
            "$base:commit",
            buildJsonObject { put("writes", JsonArray(writes)) }.toString(),
        )
        status.orThrow(body)
    }

    /** A write that changes [fields] on an existing document, or creates one when [create] is set. */
    override fun updateWrite(relative: String, fields: Fields, create: Boolean): JsonObject = buildJsonObject {
        put(
            "update",
            buildJsonObject {
                put("name", pathOf(relative))
                put("fields", fields.toFirestoreFields())
            },
        )
        if (!create) put("updateMask", buildJsonObject { put("fieldPaths", buildJsonArray { fields.keys.forEach { add(JsonPrimitive(it)) } }) })
        put("currentDocument", buildJsonObject { put("exists", !create) })
    }

    /** A write that adds [values] to an array field, leaving the ones already there. */
    override fun appendWrite(relative: String, field: String, values: List<String>): JsonObject = arrayWrite(relative, field, values, "appendMissingElements")

    /** A write that takes [values] out of an array field. */
    override fun removeWrite(relative: String, field: String, values: List<String>): JsonObject = arrayWrite(relative, field, values, "removeAllFromArray")

    private fun arrayWrite(relative: String, field: String, values: List<String>, transform: String): JsonObject = buildJsonObject {
        put(
            "transform",
            buildJsonObject {
                put("document", pathOf(relative))
                put(
                    "fieldTransforms",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("fieldPath", field)
                                put(transform, buildJsonObject { put("values", buildJsonArray { values.forEach { add(it.toFirestoreValue()) } }) })
                            },
                        )
                    },
                )
            },
        )
    }

    private suspend fun call(method: HttpMethod, url: String, json: String? = null): Pair<Int, String> {
        val token = idToken()
        return try {
            val response = http.request(url) {
                this.method = method
                header("Authorization", "Bearer $token")
                if (json != null) {
                    contentType(ContentType.Application.Json)
                    setBody(json)
                }
            }
            response.status.value to response.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw FirestoreException(0, failure.message ?: "No answer from Firestore")
        }
    }

    private fun Int.orThrow(body: String) {
        if (!HttpStatusCode.fromValue(this).isSuccess()) throw FirestoreException(this, body.take(MESSAGE_LENGTH))
    }

    private companion object {
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409

        /** How a commit says a create met a document already there, should it come as a 400. */
        const val ALREADY_EXISTS = "ALREADY_EXISTS"
        const val PAGE_SIZE = 300
        const val MESSAGE_LENGTH = 300
    }
}
