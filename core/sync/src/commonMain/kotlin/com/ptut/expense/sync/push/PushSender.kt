package com.ptut.expense.sync.push

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the push Worker about a change this phone just wrote, so the other members hear of it at
 * once. Fire and forget: a notice that does not arrive costs nothing, because the other phones pull
 * the change on their next sync anyway.
 */
class PushSender internal constructor(
    private val http: HttpClient,
    private val idToken: suspend () -> String,
    /** The Worker's address; empty in a build without one, and then nothing is ever sent. */
    private val url: String,
    private val scope: CoroutineScope,
) {
    fun tell(groupId: String, notice: PushNotice) {
        if (url.isEmpty()) return
        scope.launch { send(groupId, notice) }
    }

    /**
     * Waits for the Worker. For leaving, whose notice has to arrive before the write that takes
     * this account out of the group: the Worker sends only for members.
     */
    suspend fun tellFirst(groupId: String, notice: PushNotice) {
        if (url.isEmpty()) return
        send(groupId, notice)
    }

    private suspend fun send(groupId: String, notice: PushNotice) {
        runCatching {
            val body = buildJsonObject {
                put("groupId", groupId)
                put("title", notice.title)
                put(
                    "body",
                    buildJsonObject {
                        put("en", notice.en)
                        put("my", notice.my)
                    },
                )
            }
            http.post("${url.trimEnd('/')}/notify") {
                header("Authorization", "Bearer ${idToken()}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                timeout { requestTimeoutMillis = TIMEOUT.inWholeMilliseconds }
            }
        }
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
