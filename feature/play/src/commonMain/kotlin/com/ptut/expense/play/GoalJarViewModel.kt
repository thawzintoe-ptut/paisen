package com.ptut.expense.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.MoneyParts
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatCompactParts
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.play.ArchiveGoalUseCase
import com.ptut.expense.domain.play.CASH_ACCOUNT
import com.ptut.expense.domain.play.ChooseSavingsAccountUseCase
import com.ptut.expense.domain.play.DepositSource
import com.ptut.expense.domain.play.GoalDeposit
import com.ptut.expense.domain.play.GoalJarState
import com.ptut.expense.domain.play.ObserveGoalJarUseCase
import com.ptut.expense.domain.play.ObserveOwnAccountsUseCase
import com.ptut.expense.domain.play.PERCENT
import com.ptut.expense.domain.play.PutBackDepositUseCase
import com.ptut.expense.domain.play.PutMoneyInUseCase
import com.ptut.expense.domain.play.RemoveSaidDepositUseCase
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.play.SeenAccount
import com.ptut.expense.domain.play.depositChoices
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.typedText
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** J03's amount chips: [label] says where the amount comes from. */
data class DepositChoiceUiModel(val amount: Satang, val label: String, val selected: Boolean)

/** J04's rows: [id] is the account mask, or [CASH_ACCOUNT]. */
data class AccountChoiceUiModel(val id: String, val name: String, val line: String, val selected: Boolean)

/** J02's "Money in the jar" rows. */

/** [remove] is set only on a deposit the reader said they made: a slip proved the rest moved. */
data class DepositRowUiModel(val id: String, val name: String, val meta: String, val amount: String, val fromSlip: Boolean, val remove: String? = null, val removeLabel: String? = null)

/** The sheet over the jar: J03's amount and steps, or J04's account question before it the first time. */
sealed interface JarSheetUiModel {
    data class PutMoneyIn(
        val title: String,
        val amount: MoneyParts,
        val choices: List<DepositChoiceUiModel>,
        val steps: List<String>,
        /** Null for a reader who keeps cash: there is no bank app to open. */
        val openBank: String?,
        val moved: String,
    ) : JarSheetUiModel

    data class WhereDoYouSave(
        val title: String,
        val intro: String,
        val options: List<AccountChoiceUiModel>,
        /** Shown in place of the slip accounts when none has come through yet. */
        val none: String?,
        val footer: String,
        val use: String,
        val canUse: Boolean,
    ) : JarSheetUiModel
}

sealed interface GoalJarUiState {
    /** A new jar, or an old one's name and amount being changed. */
    data class Editing(val title: String, val nameLabel: String, val name: String, val targetLabel: String, val target: String, val symbol: String, val save: String, val canSave: Boolean, val noMoney: String) : GoalJarUiState

    data class Jar(
        val title: String,
        val edit: String,
        /** What the jar holds, drawn as a rolling hero amount. */
        val saved: MoneyParts,
        val percent: String,
        val savedOf: String,
        val fraction: Float,
        val label: String,
        /** "฿4,600 to go. Save ฿330 a week and you're there by March." */
        val plan: String,
        val putIn: String,
        val savesTo: String?,
        val change: String,
        val depositsTitle: String,
        val deposits: List<DepositRowUiModel>,
        val empty: String,
        val footer: String,
        val archive: String,
        val sheet: JarSheetUiModel?,
        val archiveAsk: String = AppText.copy.play.extras.jarArchiveAsk,
        val archiveBody: String = AppText.copy.play.extras.jarArchiveBody,
        val keep: String = AppText.copy.common.keepIt,
    ) : GoalJarUiState

    /** Put away, or never there. */
    data object Missing : GoalJarUiState
}

/**
 * J02 to J04: a jar that holds only money that really moved. The reader picks an amount, moves it in K
 * PLUS or says they did; the slip, or their word, fills the jar. No money moves through the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalJarViewModel(
    goalId: String?,
    name: String?,
    target: String?,
    private val observeJar: ObserveGoalJarUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val archiveGoal: ArchiveGoalUseCase,
    private val putMoneyIn: PutMoneyInUseCase? = null,
    private val chooseAccount: ChooseSavingsAccountUseCase? = null,
    private val removeSaidDeposit: RemoveSaidDepositUseCase? = null,
    ownAccounts: ObserveOwnAccountsUseCase? = null,
    /** Opened with Put money in showing, from Today's button (J01). */
    openPutIn: Boolean = false,
    private val putBackDeposit: PutBackDepositUseCase? = null,
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
    /** The half-typed name and target, kept through Android killing the process. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val draftKey = "jar.${goalId ?: "new"}"
    private val opened = JarForm(name.orEmpty(), target.orEmpty())
    private val restored = savedState.get<String>("$draftKey.name")?.let { JarForm(it, savedState.get<String>("$draftKey.target").orEmpty()) }

    private val id = MutableStateFlow(goalId)
    private val form = MutableStateFlow(restored ?: opened)
    private val editing = MutableStateFlow(goalId == null || restored != null)
    private val sheet = MutableStateFlow(if (openPutIn) Sheet.PutIn else Sheet.None)
    private val picked = MutableStateFlow<Satang?>(null)
    private val accountPick = MutableStateFlow<String?>(null)
    private var saving = false
    private var choosing = false

    val uiState: StateFlow<GoalJarUiState?> = combine(
        id.flatMapLatest { current -> current?.let { observeJar(it) } ?: flowOf(null) },
        editing,
        form,
        combine(sheet, picked, accountPick, ::Triple),
        ownAccounts?.invoke() ?: flowOf(emptyList()),
    ) { jar, editingNow, (typedName, typedTarget), (open, amount, account), accounts ->
        when {
            editingNow -> editing(typedName, typedTarget, jar != null)
            jar == null -> GoalJarUiState.Missing
            else -> jar.asUiState(open, amount, account, accounts)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    init {
        viewModelScope.launch {
            combine(form, editing) { typed, open -> typed.takeIf { open && it != opened } }.collect { typed ->
                if (typed == null) {
                    savedState.remove<String>("$draftKey.name")
                    savedState.remove<String>("$draftKey.target")
                } else {
                    savedState["$draftKey.name"] = typed.name
                    savedState["$draftKey.target"] = typed.target
                }
            }
        }
    }

    fun setName(text: String) = form.update { it.copy(name = text) }

    fun setTarget(text: String) = form.update { it.copy(target = text.filter { c -> c.isDigit() || c == '.' }) }

    /** The pencil on J02: the form opens on the jar's own name and amount. */
    fun edit() {
        val goal = current?.goal ?: return
        form.value = JarForm(goal.name, Currencies.of(goal.currency).typedText(goal.target.toMinorUnits()))
        editing.value = true
    }

    fun save() {
        val (typedName, typedTarget) = form.value
        val target = targetOf(typedTarget) ?: return
        if (typedName.isBlank() || saving) return
        saving = true
        viewModelScope.launch {
            runSuspendCatching { saveGoal(id.value, typedName, target) }
                .onSuccess {
                    id.value = it
                    editing.value = false
                }
                .onFailure { failed() }
            saving = false
        }
    }

    fun archive(onDone: () -> Unit) {
        val current = id.value ?: return
        viewModelScope.launch {
            runSuspendCatching { archiveGoal(current) }
                .onSuccess { onDone() }
                .onFailure { failed() }
        }
    }

    /** Put money in. The first time, Where do you save comes first (J04). */
    fun openPutIn() {
        sheet.value = Sheet.PutIn
    }

    /** J02's Change under the plan: ask Where do you save again. */
    fun changeAccount() {
        sheet.value = Sheet.Where
    }

    fun dismissSheet() {
        sheet.value = Sheet.None
        picked.value = null
        accountPick.value = null
    }

    fun pick(amount: Satang) {
        picked.value = amount
    }

    fun pickAccount(id: String) {
        accountPick.value = id
    }

    /** J04's Use this account; the amount sheet follows. */
    fun useAccount() {
        val goal = id.value ?: return
        val account = accountPick.value ?: return
        val choose = chooseAccount ?: return
        if (choosing) return
        choosing = true
        viewModelScope.launch {
            runSuspendCatching { choose(goal, account) }
                .onSuccess {
                    accountPick.value = null
                    sheet.value = Sheet.PutIn
                }
                .onFailure { failed() }
            choosing = false
        }
    }

    /** J02: takes back a deposit tapped in by mistake. */
    fun remove(depositId: String) {
        val take = removeSaidDeposit ?: return
        val deposit = current?.deposits?.firstOrNull { it.id == depositId }
        val putBack = putBackDeposit
        viewModelScope.launch {
            runSuspendCatching { take(depositId) }
                .onSuccess {
                    haptics?.deleted()
                    // The shell runs the Undo, so it holds the deposit, not this screen.
                    val undo = if (deposit != null && putBack != null) ToastAction(AppText.copy.common.undo) { putBack(deposit) } else null
                    toasts?.show(ToastMessage(AppText.copy.play.extras.jarDepositRemoved, tone = ToastTone.Deleted, action = undo))
                }
                .onFailure { failed() }
        }
    }

    private suspend fun failed() {
        toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error))
    }

    /** Open K PLUS: the sheet goes; the slip fills the jar when it comes (J05). */
    fun bankOpened() = dismissSheet()

    /** "I already moved it": the reader's word fills the jar. */
    fun moved() {
        val jar = current ?: return
        val put = putMoneyIn ?: return
        val amount = picked.value ?: depositChoices(jar.leftover, jar.pace.perWeek, minorPerWhole(Home.money)).firstOrNull() ?: return
        viewModelScope.launch {
            runSuspendCatching { put(jar.goal, amount) }
                .onSuccess { dismissSheet() }
                .onFailure { failed() }
        }
    }

    /** The jar as last folded, for the taps that need its goal without a second read. */
    private var current: GoalJarState? = null

    private fun editing(typedName: String, typedTarget: String, hasJar: Boolean): GoalJarUiState.Editing {
        val text = AppText.copy.play.extras
        return GoalJarUiState.Editing(
            title = if (hasJar) text.jarEdit else text.jarTitle,
            nameLabel = text.jarName,
            name = typedName,
            targetLabel = text.jarTarget,
            target = typedTarget,
            symbol = Home.money.symbol,
            save = text.jarSave,
            canSave = typedName.isNotBlank() && targetOf(typedTarget) != null,
            noMoney = text.jarNoMoney,
        )
    }

    private fun targetOf(typed: String): Satang? = AmountEntry.ofText(typed)?.toSatang()?.takeIf { it > Satang.Zero }

    private fun GoalJarState.asUiState(open: Sheet, amount: Satang?, account: String?, accounts: List<SeenAccount>): GoalJarUiState.Jar {
        current = this
        val text = AppText.copy.play.extras
        val fraction = pace.saved.ratioTo(goal.target).coerceIn(0f, 1f)
        val percent = (fraction * PERCENT).toInt()
        val sheetNow = when (open) {
            Sheet.None -> null
            Sheet.Where -> whereSheet(account, accounts)
            Sheet.PutIn -> if (goal.savingsAccount == null) whereSheet(account, accounts) else putInSheet(amount)
        }
        return GoalJarUiState.Jar(
            title = goal.name,
            edit = text.jarEdit,
            saved = pace.saved.formatCompactParts(),
            percent = text.jarPercent(percent),
            savedOf = text.jarSavedOf(pace.saved.formatCompact(), goal.target.formatCompact()),
            fraction = fraction,
            label = text.jarLabel(percent),
            plan = plan(),
            putIn = text.jarPutIn,
            savesTo = goal.savingsAccount?.let { if (it == CASH_ACCOUNT) text.jarSavesCash else text.jarSavesTo(it) },
            change = text.jarChange,
            depositsTitle = text.jarMoneyIn,
            deposits = deposits.map { it.asRow() },
            empty = text.jarEmpty,
            footer = text.jarFooter,
            archive = text.jarArchive,
            sheet = sheetNow,
        )
    }

    private fun GoalJarState.plan(): String {
        val text = AppText.copy.play.extras
        val perWeek = pace.perWeek
        val reach = pace.reachOn
        return when {
            pace.fills(goal.target) -> text.jarFull
            !pace.hasDeposits || perWeek == null -> text.jarPlanNone
            reach == null -> text.jarPlanFar(perWeek.formatCompact())
            else -> text.jarToGo((goal.target - pace.saved).formatCompact()) + " " + text.jarPlan(perWeek.formatCompact(), reach.month.longName())
        }
    }

    private fun GoalDeposit.asRow(): DepositRowUiModel {
        val text = AppText.copy.play.extras
        val date = day.dayLabel()
        return when (source) {
            DepositSource.Slip -> DepositRowUiModel(id, text.jarDepositSlip(account.orEmpty()), text.jarDepositFromSlip(date), "+${amount.formatCompact()}", fromSlip = true)

            DepositSource.Said -> DepositRowUiModel(
                id = id,
                name = text.jarDepositSaid,
                meta = if (account == null) text.jarDepositCash(date) else text.jarDepositByHand(date),
                amount = "+${amount.formatCompact()}",
                fromSlip = false,
                remove = text.jarRemove,
                removeLabel = text.jarRemoveLabel(amount.formatCompact()),
            )
        }
    }

    private fun GoalJarState.putInSheet(amount: Satang?): JarSheetUiModel.PutMoneyIn {
        val text = AppText.copy.play.extras
        val choices = depositChoices(leftover, pace.perWeek, minorPerWhole(Home.money))
        val chosen = amount?.takeIf { it in choices } ?: choices.first()
        val account = goal.savingsAccount
        val cash = goal.savesCash
        return JarSheetUiModel.PutMoneyIn(
            title = text.jarPutIn,
            amount = chosen.formatCompactParts(),
            choices = choices.map { choice ->
                val label = when (choice) {
                    leftover -> text.putInChoiceLeftover(choice.formatCompact())
                    pace.perWeek -> text.putInChoiceWeek(choice.formatCompact())
                    else -> choice.formatCompact()
                }
                DepositChoiceUiModel(choice, label, selected = choice == chosen)
            },
            steps = if (cash || account == null) {
                listOf(text.putInStepCash(chosen.formatCompact()), text.putInStepCashDone)
            } else {
                listOf(text.putInStepMove(chosen.formatCompact(), account), text.putInStepDone)
            },
            openBank = text.putInOpenBank.takeUnless { cash },
            moved = if (cash) text.putInMovedCash else text.putInAlreadyMoved,
        )
    }

    private fun GoalJarState.whereSheet(account: String?, accounts: List<SeenAccount>): JarSheetUiModel.WhereDoYouSave {
        val text = AppText.copy.play.extras
        val chosen = account ?: goal.savingsAccount
        val rows = accounts.map { AccountChoiceUiModel(it.mask, text.whereAccount(it.mask), text.whereSeen(it.count), selected = it.mask == chosen) } +
            AccountChoiceUiModel(CASH_ACCOUNT, text.whereCash, text.whereCashLine, selected = chosen == CASH_ACCOUNT)
        return JarSheetUiModel.WhereDoYouSave(
            title = text.whereTitle,
            intro = text.whereIntro,
            options = rows,
            none = text.whereNone.takeIf { accounts.isEmpty() },
            footer = text.whereFooter,
            use = text.whereUse,
            canUse = account != null,
        )
    }

    private enum class Sheet { None, Where, PutIn }
}

private data class JarForm(val name: String, val target: String)
