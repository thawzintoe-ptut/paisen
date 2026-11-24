package com.ptut.expense.sync.usage

import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageFlag
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class PostHogUsageLogTest {

    // A cancelled scope: the send is never started, so no test reaches the network.
    private val quiet = CoroutineScope(Job().apply { cancel() })

    private val noon = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-24T05:00:00Z")
    }

    @Test
    fun `the body holds the event name the install id and 0 or 1 flags and nothing else`() {
        val body = PostHogUsageLog.body("phc_test", "install-1", UsageEvent.SlipChecked, mapOf(UsageFlag.Edited to true))

        assertEquals(setOf("api_key", "event", "distinct_id", "properties"), Json.parseToJsonElement(body).jsonObject.keys)
        assertEquals(
            """{"api_key":"phc_test","event":"slip_checked","distinct_id":"install-1",""" +
                """"properties":{"edited":1,"${'$'}process_person_profile":false,"${'$'}geoip_disable":true}}""",
            body,
        )
    }

    @Test
    fun `with the switch off nothing is counted and no id is made`() {
        val settings = appSettings(AppChoices(shareUsage = false))
        val log = PostHogUsageLog(HttpClient(), "https://eu.i.posthog.com", "phc_test", settings, quiet, noon) { "new-id" }

        log.log(UsageEvent.ExpenseAdded)

        assertNull(settings.choices.value.usageId)
    }

    @Test
    fun `the first count makes the install id and keeps it`() {
        val settings = appSettings()
        var made = 0
        val log = PostHogUsageLog(HttpClient(), "https://eu.i.posthog.com", "phc_test", settings, quiet, noon) { "id-${++made}" }

        log.log(UsageEvent.ExpenseAdded)
        log.log(UsageEvent.PaymentRecorded)

        assertEquals("id-1", settings.choices.value.usageId)
        assertEquals(1, made)
    }

    @Test
    fun `today_opened is counted once a day`() {
        val settings = appSettings(AppChoices(usageId = "install-1"))
        val log = PostHogUsageLog(HttpClient(), "https://eu.i.posthog.com", "phc_test", settings, quiet, noon) { "unused" }

        log.log(UsageEvent.TodayOpened)
        val first = settings.choices.value.usageDay
        settings.setUsageDay("2026-11-23")
        log.log(UsageEvent.TodayOpened)

        assertEquals("2026-11-24", first)
        assertEquals("2026-11-24", settings.choices.value.usageDay)
    }
}
