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
    fun `a notice missing the record it names is read as an old build sentence`() {
        assertNull(push("expenseAdded", "amount" to "32000").notice)
        assertNull(push("paid", "from" to "m-mali", "to" to "m-cara", "amount" to "5000").notice)
        assertNull(ReceivedPush.from(mapOf("kind" to "joined")))
    }

    @Test
    fun `a record this phone already holds is worded without a sync`() = runTest {
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
    fun `the amount comes from the stored version never the payload`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara), listOf(version("v1")))), sync = {})

        val words = reader.words(push("expenseAdded", "about" to "v1", "amount" to "99999900", "currency" to "USD"), english)

        assertEquals(english.push.added("Mali", "Rice", "฿320"), words.body)
    }

    @Test
    fun `a payment is worded from its stored record never the payload`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara), payments = listOf(payment))), sync = {})

        val forged = push("paid", "about" to "p1", "from" to "m-cara", "to" to "m-mali", "amount" to "99999900", "currency" to "THB")

        assertEquals(english.push.paid("Mali", "Cara", "฿50"), reader.words(forged, english).body)
    }

    @Test
    fun `a payment another member wrote gets the generic line`() = runTest {
        val theirs = payment.copy(authorMemberId = cara.id)
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara), payments = listOf(theirs))), sync = {})

        val words = reader.words(push("paid", "about" to "p1", "from" to "m-cara", "to" to "m-mali", "amount" to "99999900"), english)

        assertEquals(english.home.newSince(1), words.body)
    }

    @Test
    fun `an expense another member wrote gets the generic line`() = runTest {
        val theirs = version("v1").copy(authorMemberId = cara.id)
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara), listOf(theirs))), sync = {})

        assertEquals(english.home.newSince(1), reader.words(push("expenseRemoved", "about" to "v1"), english).body)
    }

    @Test
    fun `a record the sync never brings gets the generic line`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara))), sync = { error("offline") })

        val words = reader.words(push("paid", "about" to "p9", "from" to "m-mali", "to" to "m-cara", "amount" to "5000"), english)

        assertEquals(PushWords("Trip", english.home.newSince(1)), words)
    }

    @Test
    fun `the sender is named by the account that claimed the name never by the payload`() = runTest {
        val reader = PushReader(MutableStateFlow(snapshot(group(mali, cara))), sync = {})

        assertEquals(english.push.left("Mali"), reader.words(push("left"), english).body)
    }

    @Test
    fun `a joiner this phone does not know yet is fetched first`() = runTest {
        val dan = mali.copy(id = "m-dan", name = "Dan", claimedByUid = "uid-dan")
        val held = MutableStateFlow(snapshot(group(mali, cara)))
        val reader = PushReader(held, sync = { held.value = snapshot(group(mali, cara, dan)) })

        val words = reader.words(push("joined").copy(by = "uid-dan"), english)

        assertEquals(english.push.joined("Dan"), words.body)
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
