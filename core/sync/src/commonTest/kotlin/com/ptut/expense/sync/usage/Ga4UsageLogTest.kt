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

class Ga4UsageLogTest {

    // A cancelled scope: the send is never started, so no test reaches the network.
    private val quiet = CoroutineScope(Job().apply { cancel() })

    private val noon = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-24T05:00:00Z")
    }

    @Test
    fun `the body holds the event name the install id and 0 or 1 flags and nothing else`() {
        val body = Json.parseToJsonElement(Ga4UsageLog.body("install-1", UsageEvent.SlipChecked, mapOf(UsageFlag.Edited to true))).jsonObject

        assertEquals(setOf("client_id", "events"), body.keys)
        assertEquals(
            """{"client_id":"install-1","events":[{"name":"slip_checked","params":{"edited":1}}]}""",
            Ga4UsageLog.body("install-1", UsageEvent.SlipChecked, mapOf(UsageFlag.Edited to true)),
        )
    }

    @Test
    fun `with the switch off nothing is counted and no id is made`() {
        val settings = appSettings(AppChoices(shareUsage = false))
        val log = Ga4UsageLog(HttpClient(), "G-TEST", "secret", settings, quiet, noon) { "new-id" }

        log.log(UsageEvent.ExpenseAdded)

        assertNull(settings.choices.value.usageId)
    }

    @Test
    fun `the first count makes the install id and keeps it`() {
        val settings = appSettings()
        var made = 0
        val log = Ga4UsageLog(HttpClient(), "G-TEST", "secret", settings, quiet, noon) { "id-${++made}" }

        log.log(UsageEvent.ExpenseAdded)
        log.log(UsageEvent.PaymentRecorded)

        assertEquals("id-1", settings.choices.value.usageId)
        assertEquals(1, made)
    }

    @Test
    fun `today_opened is counted once a day`() {
        val settings = appSettings(AppChoices(usageId = "install-1"))
        val log = Ga4UsageLog(HttpClient(), "G-TEST", "secret", settings, quiet, noon) { "unused" }

        log.log(UsageEvent.TodayOpened)
        val first = settings.choices.value.usageDay
        settings.setUsageDay("2026-11-23")
        log.log(UsageEvent.TodayOpened)

        assertEquals("2026-11-24", first)
        assertEquals("2026-11-24", settings.choices.value.usageDay)
    }
}
