package com.ptut.expense.agent

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang

/** What a sentence asked for: one [AgentTool] with its arguments, every id already checked (#205). */
sealed interface ToolCall {
    val tool: AgentTool
}

/** A read tool. It runs at once and changes nothing. */
sealed interface ReadCall : ToolCall {
    data class SpendSummary(val window: SpendWindow) : ReadCall {
        override val tool: AgentTool get() = AgentTool.SpendSummary
    }

    data class FindTransactions(
        val text: String? = null,
        val categoryId: String? = null,
        val window: SpendWindow? = null,
    ) : ReadCall {
        override val tool: AgentTool get() = AgentTool.FindTransactions
    }
}

/**
 * What the shell opens, through [AgentInbox]. Nothing here is ever written by the agent: each one
 * fills the sheet a person would have used by hand, and that sheet's own Save is what stores it.
 */
sealed interface ProposedAction {

    /** Every field may be missing. The Add sheet opens with what was read and asks for the rest. */
    data class AddTransaction(
        val amount: Satang? = null,
        val direction: Direction = Direction.Expense,
        val categoryId: String? = null,
        /** Under [categoryId] only, and only when the words named one of the reader's own (#111). */
        val subCategoryId: String? = null,
        val memo: String? = null,
        val occurredAtEpochMillis: Long? = null,
    ) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.AddTransaction
    }

    /** Opens the row's Edit screen: the category is changed and saved there. */
    data class Recategorise(val transactionId: String, val categoryId: String) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.Recategorise
    }

    /** Opens the row's record. FR-21: a delete is confirmed on the record's own screen and nowhere else. */
    data class DeleteTransaction(val transactionId: String) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.DeleteTransaction
    }

    /** [groupId] is null when no group matched; the shell then opens Shared to pick one. */
    data class AddGroupExpense(
        val groupId: String?,
        val amount: Satang? = null,
        val description: String? = null,
    ) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.AddGroupExpense
    }

    /** Without both members the shell opens the group, where Who pays whom names them. */
    data class RecordPayment(
        val groupId: String,
        val fromMemberId: String? = null,
        val toMemberId: String? = null,
    ) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.RecordPayment
    }

    data class AddRepeat(val groupId: String) : ProposedAction, ToolCall {
        override val tool: AgentTool get() = AgentTool.AddRepeat
    }

    /** The Android icon's "Scan slips" (#206): read the watched folders now and open what needs a look. Not a tool. */
    data object ScanSlips : ProposedAction
}

/** The periods a person names out loud. Every one is counted on Bangkok's calendar (FR-19). */
enum class SpendWindow { Today, Week, Month }
