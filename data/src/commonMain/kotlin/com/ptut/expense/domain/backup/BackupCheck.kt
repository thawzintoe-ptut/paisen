package com.ptut.expense.domain.backup

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.readableRate
import com.ptut.expense.domain.shared.sumsTo
import com.ptut.expense.domain.shared.toLocalDateOrNull

/**
 * Every row of a file, checked before one is stored. A file is refused whole over a value this app
 * never writes: a stored enum or date no build can read stops the app at each start, and a split
 * that does not add up moves a balance. Nothing is defaulted, for the reason [LedgerBackup.decode] gives.
 *
 * @throws BackupUnreadable naming the value when there is one to name.
 */
internal fun LedgerBackup.refuseUnreadableRows() {
    homeCurrency?.checkedCurrency()
    transactions.forEach { it.check() }
    categories.forEach { it.direction.checked(Direction.entries.map(Direction::name), "direction") }
    shared?.check()
}

private fun BackupTransaction.check() {
    direction.checked(Direction.entries.map(Direction::name), "direction")
    source.checked(TransactionSource.entries.map(TransactionSource::name), "source")
    currency?.checkedCurrency()
    // A row in other money holds all three, as the app writes it: the amount it was, and a whole rate.
    val foreignWithoutRate = originalMinor != null && rateUnscaled == null
    if (amountMinor < 0 || (originalMinor ?: 0) < 0 || foreignWithoutRate || !readableRate(rateUnscaled, rateScale)) notReadable()
}

private fun BackupShared.check() {
    val moneyOf = groups.associate { it.id to (it.currency ?: HomeMoney.Baht.code) }
    groups.forEach { group ->
        group.type.checked(GroupType.entries.map(GroupType::name), "type")
        group.currency?.checkedCurrency()
    }
    val sharesOf = shares.groupBy { it.versionId }
    versions.forEach { version ->
        version.splitMode.checked(SplitMode.entries.map(SplitMode::name), "splitMode")
        version.currency.checkedCurrency()
        val needsRate = version.currency.differsFrom(moneyOf[version.groupId]) && version.rateUnscaled == null
        if (needsRate || !readableRate(version.rateUnscaled, version.rateScale)) notReadable()
        if (!version.addsUp(sharesOf[version.versionId].orEmpty())) notReadable()
    }
    payments.forEach { payment ->
        payment.method.checked(PaymentMethod.entries.map(PaymentMethod::name), "method")
        if (payment.amountSatang <= 0) notReadable()
    }
    repeats.forEach { it.check(moneyOf[it.groupId]) }
}

private fun BackupRepeat.check(groupMoney: String?) {
    kind.checked(RepeatKind.entries.map(RepeatKind::name), "kind")
    frequency.checked(RepeatFrequency.entries.map(RepeatFrequency::name), "frequency")
    splitMode.checked(SplitMode.entries.map(SplitMode::name), "splitMode")
    endKind.checked(END_KINDS, "endKind")
    startDate.checkedDate("startDate")
    endDate?.checkedDate("endDate")
    lastAdded?.checkedDate("lastAdded")
    currency.checkedCurrency()
    val needsRate = currency.differsFrom(groupMoney) && rateUnscaled == null
    if (amountMinor < 0 || needsRate || !readableRate(rateUnscaled, rateScale)) notReadable()
}

/**
 * The rule every phone saves by and sync receives by (#134): the parts are not negative and add up
 * to the total, in the expense's money and in the group's. A sum past a `Long` does not add up.
 */
private fun BackupExpenseVersion.addsUp(parts: List<BackupShare>): Boolean =
    parts.isNotEmpty() &&
        amountMinor >= 0 &&
        amountSatang >= 0 &&
        parts.map { it.amountMinor }.sumsTo(amountMinor) &&
        parts.map { it.amountSatang }.sumsTo(amountSatang)

/** Null when the group is not in the file: its rows are left out by the restore, so there is nothing to compare. */
private fun String.differsFrom(groupMoney: String?): Boolean = groupMoney != null && this != groupMoney

private fun String.checked(allowed: List<String>, field: String) {
    if (this !in allowed) throw BackupUnreadable(BackupProblem.UnknownValue(field, take(SHOWN_CHARS)))
}

private fun String.checkedCurrency() {
    if (!CURRENCY_CODE.matches(this)) throw BackupUnreadable(BackupProblem.UnknownValue("currency", take(SHOWN_CHARS)))
}

private fun String.checkedDate(field: String) {
    if (toLocalDateOrNull() == null) throw BackupUnreadable(BackupProblem.UnknownValue(field, take(SHOWN_CHARS)))
}

private fun notReadable(): Nothing = throw BackupUnreadable(BackupProblem.NotReadable)

private val CURRENCY_CODE = Regex("[A-Z]{3}")

/** [com.ptut.expense.data.repository.END_NEVER] and its two siblings, as the repeat table holds them. */
private val END_KINDS = listOf("Never", "OnDate", "After")

/** How much of a refused value the message repeats: a file's text is not this app's to print in full. */
private const val SHOWN_CHARS = 40
