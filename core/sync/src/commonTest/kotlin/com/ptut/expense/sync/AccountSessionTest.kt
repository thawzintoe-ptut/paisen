package com.ptut.expense.sync

import com.ptut.expense.sync.auth.authTimeOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountSessionTest {

    @Test
    fun theSignInTimeIsReadFromTheTokensMiddlePart() {
        val token = jwt("""{"iss":"https://securetoken.google.com/p","auth_time":17600,"user_id":"u1"}""")

        assertEquals(17_600L, authTimeOf(token))
    }

    @Test
    fun aTokenThatIsNotAJwtHasNoSignInTime() {
        assertNull(authTimeOf("not-a-token"))
        assertNull(authTimeOf(jwt("""{"user_id":"u1"}""")))
    }

    @Test
    fun aPhoneClaimsTheAccountWhenNoSignInIsStoredYet() {
        assertEquals(SessionVerdict.Claim, sessionVerdict(onServer = null, mine = 100))
    }

    @Test
    fun aNewerSignInOnThisPhoneReplacesTheStoredOne() {
        assertEquals(SessionVerdict.Claim, sessionVerdict(onServer = 90, mine = 100))
    }

    @Test
    fun thePhoneThatHoldsTheSignInKeepsIt() {
        assertEquals(SessionVerdict.Keep, sessionVerdict(onServer = 100, mine = 100))
    }

    @Test
    fun aNewerSignInOnAnotherPhoneEndsThisOne() {
        assertEquals(SessionVerdict.EndHere, sessionVerdict(onServer = 110, mine = 100))
    }

    @Test
    fun onlyGroupsMissingFromThePhoneAreRestored() {
        val mine = listOf(group("g1"), group("g2"), group("g3", deleted = true))

        assertEquals(listOf("g2"), groupsToRestore(mine, onPhone = setOf("g1")))
    }

    @Test
    fun aRestoredGroupFindsTheNameThisAccountClaimed() {
        val members = listOf(member("m1", claimedBy = "someone-else"), member("m2", claimedBy = null), member("m3", claimedBy = "u1"))

        assertEquals("m3", claimedBy(members, uid = "u1"))
        assertNull(claimedBy(members, uid = "u9"))
    }

    private fun jwt(payload: String): String {
        val part = Base64.UrlSafe.encode(payload.encodeToByteArray()).trimEnd('=')
        return "eyJhbGciOiJSUzI1NiJ9.$part.c2lnbmF0dXJl"
    }

    private fun group(id: String, deleted: Boolean = false) =
        FirestoreDoc(id, "projects/p/databases/(default)/documents/groups/$id", mapOf("memberUids" to listOf("u1"), "deleted" to deleted))

    private fun member(id: String, claimedBy: String?) =
        FirestoreDoc(id, "projects/p/databases/(default)/documents/groups/g1/members/$id", mapOf("claimedByUid" to claimedBy))
}
