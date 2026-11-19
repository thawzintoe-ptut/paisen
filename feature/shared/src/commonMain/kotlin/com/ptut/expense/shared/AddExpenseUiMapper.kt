package com.ptut.expense.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SplitCheck
import com.ptut.expense.domain.shared.SplitMember
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.convertOrNull
import com.ptut.expense.domain.shared.dropsShareDecimal
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.inOddUnitOrder
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.percentText
import com.ptut.expense.domain.shared.wholeShares
import com.ptut.expense.locale.AppText

/** Add expense's screen, worked out from the form, the group and its [members]. No coroutines, no repository. */
internal class AddExpenseUiMapper(private val categories: List<Category>, private val isEdit: Boolean) {

    fun ready(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>): AddExpenseUiState.Ready {
        val text = AppText.copy.shared.expense
        val you = AppText.copy.common.you
        val amount = form.amountMinor()
        val inGroup = form.inGroupMoney(group)
        val own = !form.loaded || form.currency.code == group.currency
        val symbol = group.money.symbol
        val check = form.checkIn(group, members)
        val payerId = form.payerMemberId ?: group.meMemberId
        val category = categories.firstOrNull { it.id == form.categoryId }
        val unit = form.currency.rateUnit
        val missing = missingFor(group, form, amount, inGroup, check)

        return AddExpenseUiState.Ready(
            form = form,
            isEdit = isEdit,
            groupLine = text.groupLine(group.name, members.size),
            currencies = Currencies.all,
            amountShown = form.amountText.ifEmpty { "0" },
            groupMoneyLine = if (own) {
                null
            } else if (inGroup == null) {
                text.typeGroupMoney(group.currency, if (unit == 1L) "1" else formatUnit(unit), form.currency.symbol)
            } else {
                text.bahtAtRate(inGroup.formatCompact(group.moneyFormat()), symbol, form.rateText, if (unit == 1L) form.currency.symbol else "${formatUnit(unit)} ${form.currency.symbol}")
            },
            rateUnitLabel = if (own) null else text.ratePer(symbol, if (unit == 1L) "1" else formatUnit(unit), form.currency.code),
            categories = categories,
            categoryName = category?.name,
            categoryIcon = category?.icon,
            payers = members.map { SplitRowUiModel(it.id, if (it.isMe) you else it.name, it.isMe, "", "") },
            payerName = payerId?.let { group.displayName(it) } ?: you,
            splitLabel = text.splitLabel(form.splitMode.label(), members.size),
            resultLine = resultLine(group, form, check, inGroup),
            meaning = check?.takeIf { it.complete }?.let { complete -> inGroup?.let { meaning(group, form, complete, it)?.first } },
            split = splitModel(group, form, members, check, amount),
            quick = quickModel(group, form, members),
            canSave = form.loaded && !form.saving && missing == null,
            lockedLine = if (form.locked) text.locked else null,
            hint = missing.takeIf { form.loaded && !form.saving },
        )
    }

    /** #216: the first thing Save still needs, in the order the form asks for it; null when nothing. */
    private fun missingFor(group: SharedGroup, form: ExpenseForm, amount: Long?, inGroup: Satang?, check: SplitCheck?): String? {
        val text = AppText.copy.shared.expense
        return when {
            amount == null || amount <= 0 -> text.hintAmount
            inGroup == null -> text.hintRate(group.currency)
            form.categoryId == null -> text.hintCategory
            check?.complete != true -> text.hintSplit
            else -> null
        }
    }

    /** "₫462,500 each · you owe Tom ฿640" (p28): the split, and what it means for you. */
    private fun resultLine(group: SharedGroup, form: ExpenseForm, check: SplitCheck?, inGroup: Satang?): String? {
        if (check == null || !check.complete || inGroup == null) return null
        val each = check.parts.values.takeIf { it.size > 1 }?.distinct()?.singleOrNull()?.let { AppText.copy.shared.expense.each(formatMinor(it, form.currency)) }
        return listOfNotNull(each, meaning(group, form, check, inGroup)?.first).joinToString(Separators.DOT)
    }

    /** What a complete split means for this phone's member, and whether it is money they owe. */
    private fun meaning(group: SharedGroup, form: ExpenseForm, check: SplitCheck, inGroup: Satang): Pair<String, Boolean>? {
        val me = group.meMemberId ?: return null
        val amount = form.amountMinor() ?: return null
        val text = AppText.copy.shared
        val payer = form.payerMemberId ?: me
        val money = group.moneyFormat()
        val myShareMinor = check.parts[me] ?: 0L
        val myShare = if (amount == 0L) Satang.Zero else convertOrNull(myShareMinor, form.currency, form.rateIn(group), group.money) ?: return null
        return when {
            payer == me && myShareMinor < amount -> text.expense.owedAmount((inGroup - myShare).formatCompact(money)) to false
            payer != me && myShareMinor > 0 -> text.expense.oweTo(group.displayName(payer), myShare.formatCompact(money)) to true
            payer != me -> text.expense.notInThis to false
            else -> text.allYours to false
        }
    }

    /**
     * The sheet behind the card. Two people: four rows that name the payer. More: a chip per payer
     * and two rows. A locked payer (#186) leaves out the other payers.
     */
    private fun quickModel(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>): QuickSplitUiModel {
        val copy = AppText.copy
        val text = copy.shared.expense
        val ordered = members.inOddUnitOrder()
        val payerId = form.payerMemberId ?: group.meMemberId ?: ordered.firstOrNull()?.id.orEmpty()
        val current = form.quickIn(group, members)
        val named = ordered.size == 2 && ordered.first().isMe
        val payers = if (form.locked) ordered.filter { it.id == payerId } else ordered

        fun label(payer: SplitMember, choice: QuickSplit): String = when {
            choice == QuickSplit.Equally && payer.isMe -> text.quickYouEqual
            choice == QuickSplit.Equally -> text.quickOtherEqual(payer.name)
            payer.isMe -> text.quickYouFull
            else -> text.quickOtherFull(payer.name)
        }

        fun row(payer: SplitMember, choice: QuickSplit): QuickRowUiModel {
            val tried = form.withQuick(payer.id, choice, members)
            val check = tried.checkIn(group, members)?.takeIf { it.complete }
            val inGroup = tried.inGroupMoney(group)
            val meaning = if (check != null && inGroup != null) meaning(group, tried, check, inGroup) else null
            return QuickRowUiModel(
                payerId = payer.id,
                choice = choice,
                label = when {
                    named -> label(payer, choice)
                    choice == QuickSplit.Equally -> text.splitLabel(copy.shared.modeEqually, members.size)
                    else -> text.quickFull
                },
                result = meaning?.first,
                owe = meaning?.second == true,
                selected = current == choice && payer.id == payerId,
            )
        }

        val choices = if (ordered.size > 1) QuickSplit.entries else listOf(QuickSplit.Equally)
        val shown = if (named) payers else payers.filter { it.id == payerId }
        val chosen = ordered.firstOrNull { it.id == payerId }
        val paid = if (chosen == null || chosen.isMe) copy.shared.repeat.youPaid else copy.shared.repeat.paid(chosen.name)
        val byHand = "$paid${Separators.DOT}${text.splitLabel(form.splitMode.label(), members.size)}"
        return QuickSplitUiModel(
            summary = if (current != null && chosen != null) label(chosen, current) else byHand,
            chips = if (named) emptyList() else payers.map { PayerChipUiModel(it.id, if (it.isMe) copy.common.you else it.name, it.id == payerId) },
            rows = shown.flatMap { payer -> choices.map { row(payer, it) } },
            moreHint = if (current == null) {
                text.splitLabel(form.splitMode.label(), members.size)
            } else {
                listOf(SplitMode.Exact, SplitMode.Percent, SplitMode.Shares).joinToString(Separators.DOT) { it.label() }
            },
            moreSelected = current == null,
        )
    }

    private fun splitModel(group: SharedGroup, form: ExpenseForm, members: List<SplitMember>, check: SplitCheck?, amount: Long?): SplitUiModel {
        val total = amount ?: 0L
        val left = check?.left ?: 0L
        val showCheck = check != null && !check.complete && (form.splitMode == SplitMode.Exact || form.splitMode == SplitMode.Percent)
        val lastName = form.lastEdited?.let { group.displayName(it) }
        val text = AppText.copy.shared.expense
        return SplitUiModel(
            title = text.splitTitle(formatMinor(total, form.currency)),
            mode = form.splitMode,
            rows = members.map { member ->
                SplitRowUiModel(
                    memberId = member.id,
                    name = if (member.isMe) AppText.copy.common.you else member.name,
                    isMe = member.isMe,
                    text = form.weightTexts[member.id].orEmpty(),
                    part = check?.parts?.get(member.id)?.let { formatMinor(it, form.currency) } ?: formatMinor(0, form.currency),
                    detail = weightDetail(group, form, form.weightTexts[member.id].orEmpty(), check?.parts?.get(member.id) ?: 0L).takeIf { amount != null },
                )
            },
            leftTitle = if (!showCheck) {
                null
            } else if (form.splitMode == SplitMode.Exact) {
                if (left >= 0) text.leftToAssign(formatMinor(left, form.currency)) else text.tooMuch(formatMinor(-left, form.currency))
            } else {
                if (left >= 0) text.leftToAssign("${percentText(left)}%") else text.tooMuch("${percentText(-left)}%")
            },
            leftBody = if (!showCheck) {
                null
            } else if (form.splitMode == SplitMode.Exact) {
                text.splitSoFar(formatMinor(check?.assigned ?: 0, form.currency), formatMinor(total, form.currency))
            } else {
                text.splitSoFar("${percentText(check?.assigned ?: 0)}%", "100%")
            },
            giveToLabel = if (showCheck && lastName != null && left > 0) text.giveTo(lastName) else null,
            progress = when {
                check == null -> 0f
                check.target > 0 -> (check.assigned.coerceAtLeast(0).toFloatRatio(check.target))
                else -> if (check.complete) 1f else 0f
            },
            complete = check?.complete == true,
        )
    }

    /**
     * #218: under a Percent or Shares field, what that person pays ("฿320", or "$12.00 · ≈ ฿410"),
     * and "1.5 counts as 1" when Shares drops a decimal.
     */
    private fun weightDetail(group: SharedGroup, form: ExpenseForm, typed: String, partMinor: Long): String? {
        if (form.splitMode != SplitMode.Percent && form.splitMode != SplitMode.Shares) return null
        val pays = listOfNotNull(
            formatMinor(partMinor, form.currency),
            form.rateIn(group)?.let { rate -> convertOrNull(partMinor, form.currency, rate, group.money)?.let { "≈ ${it.formatCompact(group.moneyFormat())}" } },
        ).joinToString(Separators.DOT)
        val dropped = form.splitMode == SplitMode.Shares && dropsShareDecimal(typed)
        return if (dropped) "${AppText.copy.shared.expense.countsAs(typed, wholeShares(typed).toString())}${Separators.DOT}$pays" else pays
    }
}

fun SplitMode.label(): String {
    val text = AppText.copy.shared
    return when (this) {
        SplitMode.Equally -> text.modeEqually
        SplitMode.Exact -> text.modeExact
        SplitMode.Percent -> text.modePercent
        SplitMode.Shares -> text.modeShares
    }
}

private fun formatUnit(unit: Long): String = unit.toString().reversed().chunked(3).joinToString(",").reversed()

/** A progress bar's fill, the one place a count meets a float: nothing spendable comes back out. */
private fun Long.toFloatRatio(of: Long): Float = if (of <= 0L) 0f else (this * PROGRESS_STEPS / of).coerceIn(0L, PROGRESS_STEPS) / PROGRESS_STEPS_F

private const val PROGRESS_STEPS = 1_000L
private const val PROGRESS_STEPS_F = 1_000f
