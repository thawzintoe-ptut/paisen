package com.ptut.expense.sync.push

import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.locale.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushWordsTest {

    private val english = Language.English.copy.shared

    private fun push(kind: String, vararg fields: Pair<String, String>) =
        ReceivedPush.from(mapOf("groupId" to "g1", "uid" to "uid-cara", "title" to "Trip", "body" to "1 new", "kind" to kind, "by" to "uid-mali") + fields)!!

    @Test
    fun `an old build sentence is shown as the Worker sent it`() = runTest {
        val old = ReceivedPush.from(mapOf("groupId" to "g1", "uid" to "uid-cara", "title" to "Trip", "body" to "Mali added Rice"))!!
        val reader = PushReader(MutableStateFlow(SharedSnapshot.Empty), sync = { error("no sync for an old notice") })

        assertNull(old.notice)
        assertEquals(PushWords("Trip", "Mali added Rice"), reader.words(old, english))
    }

    @Test
    fun `a notice missing what its words need is read as an old build sentence`() {
        assertNull(push("expenseAdded", "about" to "v1").notice)
        assertNull(push("paid", "from" to "m-mali", "amount" to "5000").notice)
        assertNull(ReceivedPush.from(mapOf("kind" to "joined")))
    }

    @Test
    fun `a notice this phone already holds is worded without a sync`() = runTest {
        val held = MutableStateFlow(snapshot(group(mali, cara), listOf(version("v1"))))
        val reader = PushReader(held, sync = { error("nothing to fetch") })

        val words = reader.words(push("expenseAdded", "about" to "v1", "amount" to "32000", "currency" to "THB"), english)

        assertEquals(PushWords("Trip", english.push.added("Mali", "Rice", "฿320")), words)
    }

    @Test
    fun `a new expense is fetched first so its description shows`() = runTest {
        val held = MutableStateFlow(snapshot(group(mali, cara)))
        val reader = PushReader(held, sync = { held.value = snapshot(group(mali, cara), listOf(version("v1", description = "Dinner"))) })

        val words = reader.words(push("expenseAdded", "about" to "v1", "amount" to "32000", "currency" to "THB"), english)

        assertEquals(english.push.added("Mali", "Dinner", "฿320"), words.body)
    }

    @Test
    fun `what a failed sync leaves out reads as someone and an expense`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(cara))), sync = { error("offline") })

        val words = reader.words(push("expenseAdded", "about" to "v9", "amount" to "32000", "currency" to "THB"), english)

        assertEquals(english.push.added(english.someone, english.push.anExpense, "฿320"), words.body)
    }

    @Test
    fun `the sender is named by the account that claimed the name never by the payload`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara))), sync = {})

        assertEquals(english.push.left("Mali"), reader.words(push("left"), english).body)
    }

    @Test
    fun `a group the sync lets go still names its sender and title`() = runTest {
        val held = MutableStateFlow(snapshot(group(mali, cara)))
        val reader = PushReader(held, sync = { held.value = SharedSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()) })

        val words = reader.words(push("expenseRemoved", "about" to "v9"), english)

        assertEquals(PushWords("Trip", english.push.removed("Mali", english.push.anExpense)), words)
    }

    @Test
    fun `a payment names both members and its amount in the group money`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara))), sync = {})

        val words = reader.words(push("paid", "about" to "p1", "from" to "m-mali", "to" to "m-cara", "amount" to "5000", "currency" to "THB"), english)

        assertEquals(english.push.paid("Mali", "Cara", "฿50"), words.body)
    }

    @Test
    fun `each phone words the same notice in its own language`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara))), sync = {})

        val sentences = Language.entries.map { reader.words(push("joined"), it.copy.shared).body }

        assertEquals(Language.entries.map { it.copy.shared.push.joined("Mali") }, sentences)
    }

    @Test
    fun `a tap opens only a group this phone holds`() = runTest {
        val held = MutableStateFlow(snapshot(group(mali, cara)))

        assertEquals("g1", heldGroup("g1", held))
        assertNull(heldGroup("g-elsewhere", held))
        assertNull(heldGroup(" ", held))
    }

    @Test
    fun `a tap before Room answers waits and then opens nothing it does not hold`() = runTest {
        assertNull(heldGroup("g1", MutableStateFlow(SharedSnapshot.Empty)))
    }
}
