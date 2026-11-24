package com.ptut.expense.sync.flags

import com.ptut.expense.sync.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagsTest {

    private val installation = Installation(id = "fid", token = "token", appId = "app", packageName = "com.ptut.expense")
    private val hour = 3_600_000L

    @Test
    fun `an update replaces the cached entries`() {
        val reply = parseFetchReply("""{"entries":{"planned_on":"false","assistant_on":"true"},"state":"UPDATE"}""")!!

        val next = StoredFlags(mapOf("currencies_on" to "false"), fetchedAtMillis = 1).after(reply, nowMillis = 99)

        assertEquals(StoredFlags(mapOf("planned_on" to "false", "assistant_on" to "true"), 99), next)
    }

    @Test
    fun `no change keeps the cached entries and moves the fetch time`() {
        val reply = parseFetchReply("""{"state":"NO_CHANGE"}""")!!

        val next = StoredFlags(mapOf("planned_on" to "false"), fetchedAtMillis = 1).after(reply, nowMillis = 99)

        assertEquals(StoredFlags(mapOf("planned_on" to "false"), 99), next)
    }

    @Test
    fun `no template or an empty config falls back to the defaults`() {
        val cached = StoredFlags(mapOf("planned_on" to "false"), fetchedAtMillis = 1)

        for (state in listOf("NO_TEMPLATE", "EMPTY_CONFIG")) {
            val next = cached.after(parseFetchReply("""{"state":"$state"}""")!!, nowMillis = 99)!!
            assertEquals(StoredFlags(emptyMap(), 99), next)
            assertTrue(FeatureFlags(next.entries).isOn(Flag.Planned, debug = false))
        }
    }

    @Test
    fun `values that are not true or false are dropped`() {
        val reply = parseFetchReply("""{"entries":{"planned_on":"no","note_tone_on":"FALSE","assistant_on":"false"},"state":"UPDATE"}""")!!

        assertEquals(mapOf("assistant_on" to "false"), reply.entries)
    }

    @Test
    fun `a body that is not a reply keeps the cache`() {
        assertNull(parseFetchReply("<html>quota</html>"))
        assertNull(parseFetchReply("""{"entries":{}}"""))
        assertNull(StoredFlags().after(FetchReply("SOMETHING_NEW", emptyMap()), nowMillis = 1))
    }

    @Test
    fun `a debug build has every switch on`() {
        val flags = FeatureFlags(Flag.entries.associate { it.key to "false" })

        Flag.entries.forEach { assertTrue(flags.isOn(it, debug = true)) }
    }

    @Test
    fun `a release build takes the cached value before the default`() {
        val flags = FeatureFlags(mapOf("thai_language_on" to "false", "currencies_on" to "maybe"))

        assertFalse(flags.isOn(Flag.ThaiLanguage, debug = false))
        assertTrue(flags.isOn(Flag.Currencies, debug = false))
        assertTrue(FeatureFlags.Defaults.isOn(Flag.SlipGroupDebt, debug = false))
    }

    @Test
    fun `play ships on and Remote Config can turn it off`() {
        assertTrue(FeatureFlags.Defaults.isOn(Flag.Play, debug = false))
        assertFalse(FeatureFlags(mapOf("play_on" to "false")).isOn(Flag.Play, debug = false))
    }

    @Test
    fun `v2 and later ship off and Remote Config can turn each on`() {
        assertFalse(FeatureFlags.Defaults.isOn(Flag.V2, debug = false))
        assertFalse(FeatureFlags.Defaults.isOn(Flag.Later, debug = false))
        assertTrue(FeatureFlags(mapOf("v2_on" to "true")).isOn(Flag.V2, debug = false))
        assertFalse(FeatureFlags(mapOf("v2_on" to "true")).isOn(Flag.Later, debug = false))
    }

    @Test
    fun `the cache survives a round trip and a broken value reads as empty`() {
        val entries = mapOf("planned_on" to "false")

        assertEquals(StoredFlags(entries, 5), decodeFlags(encodeEntries(entries), 5))
        assertEquals(StoredFlags(), decodeFlags("not json", 5))
    }

    @Test
    fun `a fetch inside twelve hours is skipped`() = runTest {
        val cache = MemoryCache(StoredFlags(emptyMap(), fetchedAtMillis = 0))
        var calls = 0
        val refresh = refresh(cache, now = 11 * hour) {
            calls++
            FetchReply("NO_CHANGE", emptyMap())
        }

        refresh.refresh("1.0", "en")

        assertEquals(0, calls)
    }

    @Test
    fun `a fetch after twelve hours writes the reply`() = runTest {
        val cache = MemoryCache(StoredFlags(emptyMap(), fetchedAtMillis = 0))
        val refresh = refresh(cache, now = 12 * hour) { FetchReply("UPDATE", mapOf("planned_on" to "false")) }

        refresh.refresh("1.0", "en")

        assertEquals(StoredFlags(mapOf("planned_on" to "false"), 12 * hour), cache.stored)
    }

    @Test
    fun `a failed fetch keeps the cache and its time`() = runTest {
        val before = StoredFlags(mapOf("planned_on" to "false"), fetchedAtMillis = 0)
        val cache = MemoryCache(before)

        refresh(cache, now = 13 * hour) { null }.refresh("1.0", "en")
        refresh(cache, now = 13 * hour, installed = null) { FetchReply("UPDATE", emptyMap()) }.refresh("1.0", "en")

        assertEquals(before, cache.stored)
    }

    private fun refresh(
        cache: FlagCache,
        now: Long,
        installed: Installation? = installation,
        fetch: suspend () -> FetchReply?,
    ) = FlagRefresh(
        fetch = { _, _, _ -> fetch() },
        cache = cache,
        installation = { installed },
        scope = CoroutineScope(Job()),
        clock = TestClock(now),
    )

    private class MemoryCache(var stored: StoredFlags) : FlagCache {
        override fun read(): StoredFlags = stored

        override fun write(flags: StoredFlags) {
            stored = flags
        }
    }
}
