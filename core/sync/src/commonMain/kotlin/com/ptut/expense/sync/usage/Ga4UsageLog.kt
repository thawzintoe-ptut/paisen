package com.ptut.expense.sync.usage

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageFlag
import com.ptut.expense.domain.usage.UsageLog
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #115: launch counts to Google Analytics 4 over its Measurement Protocol, the HTTPS call the SDK
 * makes, with no SDK. A count carries an event name, the install's random id and 0/1 flags; a
 * failed send is dropped rather than kept, since the numbers only need to be close.
 */
@OptIn(ExperimentalUuidApi::class)
internal class Ga4UsageLog(
    private val http: HttpClient,
    private val measurementId: String,
    private val apiSecret: String,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : UsageLog {

    override val sends: Boolean get() = true

    override fun log(event: UsageEvent, flags: Map<UsageFlag, Boolean>) {
        val choices = settings.choices.value
        if (!choices.shareUsage) return
        if (event == UsageEvent.TodayOpened) {
            val day = clock.now().toLocalDateTime(Home.zone).date.toString()
            if (choices.usageDay == day) return
            settings.setUsageDay(day)
        }
        val id = choices.usageId ?: newId().also(settings::setUsageId)
        scope.launch { runSuspendCatching { send(id, event, flags) } }
    }

    private suspend fun send(clientId: String, event: UsageEvent, flags: Map<UsageFlag, Boolean>) {
        http.post("https://www.google-analytics.com/mp/collect?measurement_id=$measurementId&api_secret=$apiSecret") {
            contentType(ContentType.Application.Json)
            setBody(body(clientId, event, flags))
        }
    }

    companion object {
        /** The request body, alone so a test can read exactly what would leave the phone. */
        internal fun body(clientId: String, event: UsageEvent, flags: Map<UsageFlag, Boolean>): String = buildJsonObject {
            put("client_id", clientId)
            put(
                "events",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("name", event.wire)
                            putJsonObject("params") { flags.forEach { (flag, on) -> put(flag.wire, if (on) 1 else 0) } }
                        },
                    )
                },
            )
        }.toString()
    }
}
