package com.ptut.expense.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A file is somebody else's text. Each case here is one value the app never writes, in a file that
 * is otherwise the one the current build exports, and the whole file is refused over it.
 */
class LedgerBackupCheckTest {

    @Test
    fun `a file as the current build writes it reads back the same`() {
        assertEquals(whole, LedgerBackup.decode(LedgerBackup.encode(whole)))
    }

    @Test
    fun `a format 2 file with a group but no currencies or repeats still reads`() {
        val format2 = """
            {"format": 2, "exportedAtEpochMillis": 1, "transactions": [
              {"id": "t1", "occurredAtEpochMillis": ${1_755_000_000_000L}, "amountMinor": 2825, "direction": "Expense", "source": "Scan"}
            ], "shared": {
              "people": [{"id": "p1", "name": "Aye", "archived": false}],
              "groups": [{"id": "g1", "name": "Flat", "type": "Flat", "icon": "home", "simplifyDebts": true, "createdAtEpochMillis": 1, "meMemberId": "me"}],
              "members": [{"id": "me", "groupId": "g1", "name": "You", "archived": false, "createdAtEpochMillis": 1}],
              "versions": [{"versionId": "v1", "expenseId": "e1", "groupId": "g1", "version": 1, "description": "Rent", "occurredAtEpochMillis": 1,
                "currency": "THB", "amountMinor": 9000, "amountSatang": 9000, "payerMemberId": "me", "splitMode": "Equally",
                "createdAtEpochMillis": 1, "removed": false}],
              "shares": [{"versionId": "v1", "memberId": "me", "weight": 1, "amountMinor": 9000, "amountSatang": 9000}],
              "payments": [{"id": "pay1", "groupId": "g1", "fromMemberId": "me", "toMemberId": "bo", "amountSatang": 500, "method": "Cash",
                "occurredAtEpochMillis": 1, "createdAtEpochMillis": 1}]
            }}
        """.trimIndent()

        assertEquals(1, LedgerBackup.decode(format2).shared?.versions?.size)
    }

    @Test
    fun `an enum name no build knows is refused and named`() {
        assertEquals(BackupProblem.UnknownValue("kind", "Lottery"), refused(whole.withRepeat { it.copy(kind = "Lottery") }))
        refused(whole.withRepeat { it.copy(frequency = "Hourly") })
        refused(whole.withRepeat { it.copy(splitMode = "Thirds") })
        refused(whole.withRepeat { it.copy(endKind = "Sometime") })
        refused(whole.withVersion { it.copy(splitMode = "Thirds") })
        refused(whole.withShared { it.copy(groups = it.groups.map { group -> group.copy(type = "Cult") }) })
        refused(whole.withShared { it.copy(payments = it.payments.map { payment -> payment.copy(method = "Barter") }) })
        refused(whole.copy(categories = listOf(BackupCategory("c1", "Food", "restaurant", "Sideways", "teal", 0))))
        refused(whole.copy(transactions = whole.transactions.map { it.copy(source = "Dream") }))
    }

    @Test
    fun `a date that is no date is refused and named`() {
        assertEquals(BackupProblem.UnknownValue("startDate", "2026-02-31"), refused(whole.withRepeat { it.copy(startDate = "2026-02-31") }))
        refused(whole.withRepeat { it.copy(endDate = "soon") })
        refused(whole.withRepeat { it.copy(lastAdded = "") })
    }

    @Test
    fun `a currency that is not three capitals is refused and the message keeps it short`() {
        assertEquals(BackupProblem.UnknownValue("currency", "baht"), refused(whole.withVersion { it.copy(currency = "baht") }))
        assertEquals(BackupProblem.UnknownValue("currency", "x".repeat(40)), refused(whole.copy(homeCurrency = "x".repeat(500))))
        refused(whole.withRepeat { it.copy(currency = "") })
        refused(whole.withShared { it.copy(groups = it.groups.map { group -> group.copy(currency = "thb") }) })
        refused(whole.copy(transactions = whole.transactions.map { it.copy(currency = "US") }))
    }

    @Test
    fun `a rate of zero or half a rate or other money with no rate is refused`() {
        refused(whole.withVersion { it.copy(rateUnscaled = 0, rateScale = 2) })
        refused(whole.withVersion { it.copy(rateUnscaled = 5, rateScale = null) })
        refused(whole.withVersion { it.copy(rateUnscaled = 5, rateScale = 10) })
        refused(whole.withVersion { it.copy(currency = "MMK") })
        refused(whole.withRepeat { it.copy(currency = "MMK") })
        refused(whole.withRepeat { it.copy(rateUnscaled = -1, rateScale = 0) })
        refused(whole.copy(transactions = whole.transactions.map { it.copy(rateUnscaled = null, rateScale = null) }))
        refused(whole.copy(transactions = whole.transactions.map { it.copy(rateUnscaled = 0) }))
    }

    @Test
    fun `other money with its rate reads`() {
        val kyat = whole.withVersion { it.copy(currency = "MMK", rateUnscaled = 82, rateScale = 4) }

        assertEquals(kyat, LedgerBackup.decode(LedgerBackup.encode(kyat)))
    }

    @Test
    fun `an amount below zero or a payment of nothing is refused`() {
        refused(whole.copy(transactions = whole.transactions.map { it.copy(amountMinor = -1) }))
        refused(whole.copy(transactions = whole.transactions.map { it.copy(originalMinor = -1) }))
        refused(whole.withVersion { it.copy(amountMinor = -9_000) })
        refused(whole.withVersion { it.copy(amountSatang = -9_000) })
        refused(whole.withRepeat { it.copy(amountMinor = -1) })
        refused(whole.withShared { it.copy(payments = it.payments.map { payment -> payment.copy(amountSatang = 0) }) })
    }

    @Test
    fun `a jar with no target or money in below zero or a day out of range is refused`() {
        val play = BackupPlay(
            goals = listOf(BackupGoal("j1", "Trip", 1_000_000, "THB", "2026-11-01", createdAtEpochMillis = 1)),
            deposits = listOf(BackupDeposit("d1", "j1", 50_000, "2026-11-02", atEpochMillis = 1, source = "Slip")),
        )

        assertEquals(whole.copy(play = play), LedgerBackup.decode(LedgerBackup.encode(whole.copy(play = play))))
        refused(whole.copy(play = play.copy(goals = play.goals.map { it.copy(targetMinor = 0) })))
        refused(whole.copy(play = play.copy(deposits = play.deposits.map { it.copy(amountMinor = -1) })))
        refused(whole.copy(play = play.copy(deposits = play.deposits.map { it.copy(date = "1970-01-01") })))
        refused(whole.copy(play = play.copy(weeks = listOf(BackupPlayWeek("9999-12-27")))))
        refused(whole.copy(transactions = whole.transactions.map { it.copy(occurredAtEpochMillis = 0) }))
    }

    @Test
    fun `a split that does not add up to its total is refused`() {
        refused(whole.withShared { it.copy(shares = it.shares.dropLast(1)) })
        refused(whole.withShared { it.copy(shares = emptyList()) })
        refused(whole.withShared { it.copy(shares = it.shares.map { share -> share.copy(amountSatang = share.amountSatang + 1) }) })
        refused(whole.withShared { it.copy(shares = it.shares.map { share -> share.copy(amountMinor = -share.amountMinor) }) })
    }

    @Test
    fun `parts that only add up by wrapping round are refused`() {
        // Four parts of 2^62 sum to zero in a Long.
        val wrapped = (1..4).map { BackupShare("v1", "m$it", 1, 1L shl 62, 1L shl 62) }

        refused(whole.withVersion { it.copy(amountMinor = 0, amountSatang = 0) }.withShared { it.copy(shares = wrapped) })
    }

    @Test
    fun `a row of a group the file does not hold is not judged by the money of a group`() {
        val stray = whole.withRepeat { it.copy(groupId = "elsewhere", currency = "MMK") }

        assertEquals(stray, LedgerBackup.decode(LedgerBackup.encode(stray)))
    }

    private fun refused(backup: LedgerBackup): BackupProblem = assertFailsWith<BackupUnreadable> { LedgerBackup.decode(LedgerBackup.encode(backup)) }.problem

    private fun LedgerBackup.withShared(change: (BackupShared) -> BackupShared) = copy(shared = change(shared!!))

    private fun LedgerBackup.withVersion(change: (BackupExpenseVersion) -> BackupExpenseVersion) = withShared { it.copy(versions = it.versions.map(change)) }

    private fun LedgerBackup.withRepeat(change: (BackupRepeat) -> BackupRepeat) = withShared { it.copy(repeats = it.repeats.map(change)) }

    /** One of every row, as the export writes them: a kyat transaction, a baht group, an expense split in two, a payment, a repeat. */
    private val whole = LedgerBackup(
        format = LedgerBackup.FORMAT,
        exportedAtEpochMillis = 1_755_000_000_000,
        transactions = listOf(
            BackupTransaction(
                id = "t1", occurredAtEpochMillis = 1_755_000_000_000, amountMinor = 24_190, direction = "Expense", source = "Scan",
                currency = "MMK", originalMinor = 29_500, rateUnscaled = 82, rateScale = 4,
            ),
        ),
        shared = BackupShared(
            people = listOf(BackupPerson("p1", "Aye", archived = false)),
            groups = listOf(BackupGroup("g1", "Flat 12", "Flat", "home", simplifyDebts = true, createdAtEpochMillis = 1, meMemberId = "me", currency = "THB")),
            members = listOf(
                BackupMember("me", "g1", "You", archived = false, createdAtEpochMillis = 1),
                BackupMember("aye", "g1", "Aye", personId = "p1", archived = false, createdAtEpochMillis = 1),
            ),
            versions = listOf(
                BackupExpenseVersion(
                    versionId = "v1", expenseId = "e1", groupId = "g1", version = 1, description = "Dinner", occurredAtEpochMillis = 1,
                    currency = "THB", amountMinor = 9_001, amountSatang = 9_001, payerMemberId = "me", splitMode = "Equally",
                    createdAtEpochMillis = 1, removed = false,
                ),
            ),
            shares = listOf(BackupShare("v1", "me", 1, 4_501, 4_501), BackupShare("v1", "aye", 1, 4_500, 4_500)),
            payments = listOf(BackupPayment("pay1", "g1", "aye", "me", 4_500, "PromptPay", occurredAtEpochMillis = 2, createdAtEpochMillis = 2)),
            links = listOf(BackupLink("e1", "t1")),
            repeats = listOf(
                BackupRepeat(
                    id = "r1", groupId = "g1", kind = "Rent", frequency = "Monthly", startDate = "2026-01-31", endKind = "OnDate", endDate = "2026-12-31",
                    remind = true, active = true, lastAdded = "2026-10-31", description = "Rent", currency = "THB", amountMinor = 1_200_000,
                    payerMemberId = "me", splitMode = "Shares", weights = "me=1;aye=2", createdAtEpochMillis = 1,
                ),
            ),
        ),
        categories = listOf(BackupCategory("c1", "Food", "restaurant", "Expense", "teal", 0)),
        homeCurrency = "THB",
        homeZone = "Asia/Bangkok",
    )
}
