package com.ptut.expense.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SplitCheck
import com.ptut.expense.domain.shared.SplitMember
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.domain.shared.convertOrNull
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.parseMinor
import com.ptut.expense.domain.shared.parsePercent
import com.ptut.expense.domain.shared.percentText
import com.ptut.expense.domain.shared.quickSplitOf
import com.ptut.expense.domain.shared.quickSplitWeights
import com.ptut.expense.domain.shared.split
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.domain.shared.wholeShares

/** What the person is typing. Everything shown is worked out from this and the group (#183, #138). */
data class ExpenseForm(
    /** The group's money once [loaded]; until then nothing about money is drawn from it (#48). */
    val currency: Currency = Currencies.Thb,
    val amountText: String = "",
    val rateText: String = "",
    val description: String = "",
    val occurredAtEpochMillis: Long? = null,
    val categoryId: String? = null,
    val note: String = "",
    val payerMemberId: String? = null,
    val splitMode: SplitMode = SplitMode.Equally,
    /** Per member, what was typed in Split options, in the mode's own unit. */
    val weightTexts: Map<String, String> = emptyMap(),
    /** The field edited last, for GIVE TO (p29). */
    val lastEdited: String? = null,
    val splitOpen: Boolean = false,
    val loaded: Boolean = false,
    /** #186 (O4): amount, currency, date and payer come from History. The description and category stay editable. */
    val locked: Boolean = false,
    /** A purchase shared from History keeps History's category until its first save links the two. */
    val categoryLocked: Boolean = false,
    val subCategoryId: String? = null,
    /** #214: true from the tap until the screen leaves, so two fast taps save one expense. */
    val saving: Boolean = false,
) {
    /** What the person typed or picked, without where the screen is or what it is doing. */
    val content: ExpenseForm get() = copy(lastEdited = null, splitOpen = false, saving = false)

    companion object {
        /** A purchase shared from History: History counts in the ledger's home money, which a rate turns into the group's (#48). */
        fun fromSeed(seed: TransactionSeed, group: SharedGroup, categories: List<Category>): ExpenseForm {
            val home = Currencies.of(Home.money.code)
            return ExpenseForm(
                currency = home,
                amountText = home.typedText(seed.amount.toMinorUnits()),
                description = seed.description,
                occurredAtEpochMillis = seed.occurredAtEpochMillis,
                categoryId = seed.categoryId ?: categories.firstOrNull()?.id,
                subCategoryId = seed.subCategoryId,
                payerMemberId = group.meMemberId,
                locked = true,
                categoryLocked = true,
                loaded = true,
            )
        }

        /**
         * An edit, from the latest version. O4: a [linked] expense's amount, date and payer change in
         * History only. Its category changes here too, and saving copies it to your History row (#32).
         */
        fun fromExisting(existing: SharedExpense, linked: Boolean): ExpenseForm {
            val currency = Currencies.of(existing.currency)
            return ExpenseForm(
                currency = currency,
                amountText = currency.typedText(existing.amountMinor),
                rateText = existing.rate?.typedFor(currency.rateUnit).orEmpty(),
                description = existing.description,
                occurredAtEpochMillis = existing.occurredAtEpochMillis,
                categoryId = existing.categoryId,
                note = existing.note.orEmpty(),
                payerMemberId = existing.payerMemberId,
                splitMode = existing.splitMode,
                weightTexts = existing.shares.associate { share ->
                    share.memberId to when (existing.splitMode) {
                        SplitMode.Exact -> currency.typedText(share.weight)
                        SplitMode.Percent -> percentText(share.weight)
                        else -> share.weight.toString()
                    }
                },
                loaded = true,
                locked = linked,
                subCategoryId = existing.subCategoryId,
            )
        }
    }
}

internal fun ExpenseForm.amountMinor(): Long? = parseMinor(amountText, currency)

/** No rate in the group's own money; another currency needs one to count (#48). */
internal fun ExpenseForm.rateIn(group: SharedGroup): Rate? =
    if (currency.code == group.currency) null else Rate.parse(rateText, currency.rateUnit)

/** The amount in the group's money, or null until another currency has its rate, or when it is too large to hold. */
internal fun ExpenseForm.inGroupMoney(group: SharedGroup): Satang? {
    val rate = rateIn(group)
    return amountMinor()?.takeIf { currency.code == group.currency || rate != null }?.let { convertOrNull(it, currency, rate, group.money) }
}

internal fun ExpenseForm.checkIn(group: SharedGroup, members: List<SplitMember>): SplitCheck? {
    val amount = amountMinor() ?: return null
    return split(amount, splitMode, members, weightsIn(group))
}

internal fun ExpenseForm.weightsIn(group: SharedGroup): Map<String, Long> =
    group.members.associate { it.id to weightOf(it.id) }.filterValues { it > 0 }

internal fun ExpenseForm.weightOf(memberId: String): Long {
    val text = weightTexts[memberId].orEmpty()
    return when (splitMode) {
        SplitMode.Equally -> 1L
        SplitMode.Exact -> parseMinor(text, currency) ?: 0L
        SplitMode.Percent -> parsePercent(text) ?: 0L
        SplitMode.Shares -> wholeShares(text)
    }
}

/** [weight] as the field for this mode shows it. */
internal fun ExpenseForm.weightText(weight: Long): String = when (splitMode) {
    SplitMode.Exact -> currency.typedText(weight)
    SplitMode.Percent -> percentText(weight)
    else -> weight.toString()
}

internal fun ExpenseForm.quickIn(group: SharedGroup, members: List<SplitMember>): QuickSplit? {
    val payer = payerMemberId ?: group.meMemberId ?: return null
    return quickSplitOf(splitMode, payer, members, weightsIn(group))
}

internal fun ExpenseForm.withQuick(payerId: String, choice: QuickSplit, members: List<SplitMember>): ExpenseForm = when (choice) {
    QuickSplit.Equally -> copy(payerMemberId = payerId, splitMode = SplitMode.Equally, weightTexts = emptyMap(), lastEdited = null)

    QuickSplit.OwedInFull -> copy(
        payerMemberId = payerId,
        splitMode = SplitMode.Shares,
        weightTexts = quickSplitWeights(payerId, members).mapValues { (_, shares) -> shares.toString() },
        lastEdited = null,
    )
}
