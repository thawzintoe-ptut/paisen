package com.ptut.expense.sync.flags

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.hours

/** The cached entries and when they were last fetched; zero means never. */
data class StoredFlags(val entries: Map<String, String> = emptyMap(), val fetchedAtMillis: Long = 0)

/** What Remote Config answered: [entries] only matter for an update. */
internal data class FetchReply(val state: String, val entries: Map<String, String>)

/** Who is asking: the Firebase installation, its auth token and the platform's Firebase app. */
internal data class Installation(val id: String, val token: String, val appId: String, val packageName: String)

internal val FETCH_INTERVAL = 12.hours

internal fun fetchDue(stored: StoredFlags, nowMillis: Long): Boolean =
    nowMillis - stored.fetchedAtMillis >= FETCH_INTERVAL.inWholeMilliseconds || nowMillis < stored.fetchedAtMillis

/** Null for a body that is not a Remote Config reply. Values other than "true" and "false" are dropped. */
internal fun parseFetchReply(text: String): FetchReply? {
    val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val state = (json["state"] as? JsonPrimitive)?.content ?: return null
    val entries = (json["entries"] as? JsonObject).orEmpty()
        .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.takeIf { it == "true" || it == "false" }?.let { key to it } }
        .toMap()
    return FetchReply(state, entries)
}

/** The cache after [reply], or null to keep the old one untouched, as for a failure. */
internal fun StoredFlags.after(reply: FetchReply, nowMillis: Long): StoredFlags? = when (reply.state) {
    "UPDATE" -> StoredFlags(reply.entries, nowMillis)
    "NO_CHANGE" -> copy(fetchedAtMillis = nowMillis)
    "NO_TEMPLATE", "EMPTY_CONFIG" -> StoredFlags(emptyMap(), nowMillis)
    else -> null
}

/** Remote Config's client fetch over REST, the call the Firebase SDK makes, with no SDK. */
internal class RemoteConfigApi(private val http: HttpClient, private val apiKey: String, private val projectNumber: String) {

    suspend fun fetch(installation: Installation, appVersion: String, languageCode: String): FetchReply? {
        val response = http.post(
            "https://firebaseremoteconfig.googleapis.com/v1/projects/$projectNumber/namespaces/firebase:fetch?key=$apiKey",
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("appInstanceId", installation.id)
                    put("appInstanceIdToken", installation.token)
                    put("appId", installation.appId)
                    put("packageName", installation.packageName)
                    put("appVersion", appVersion)
                    put("languageCode", languageCode)
                    put("sdkVersion", "0")
                }.toString(),
            )
        }
        if (!response.status.isSuccess()) return null
        return parseFetchReply(response.bodyAsText())
    }
}

/** The entries as one JSON string, for a platform cache that keeps strings. */
internal fun encodeEntries(entries: Map<String, String>): String =
    JsonObject(entries.mapValues { (_, value) -> JsonPrimitive(value) }).toString()

/** A cache written by an older or broken build reads as nothing fetched. */
internal fun decodeFlags(entries: String?, fetchedAtMillis: Long): StoredFlags {
    val parsed = entries?.let { text -> runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() }
        ?: return StoredFlags()
    return StoredFlags(parsed.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.let { key to it } }.toMap(), fetchedAtMillis)
}
