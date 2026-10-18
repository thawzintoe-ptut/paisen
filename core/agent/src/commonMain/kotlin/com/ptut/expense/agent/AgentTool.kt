package com.ptut.expense.agent

/**
 * #205: the eight things the agent may ask of the app. A read tool runs at once and changes nothing;
 * a write tool only fills the sheet the person would have used anyway, and that sheet saves (#191's
 * promise holds: none of this reaches the network).
 *
 * [id] is the function name a model is trained on, so it never changes once a model ships.
 */
enum class AgentTool(
    val id: String,
    val writes: Boolean,
    val description: String,
    val parameters: List<ToolParameter>,
) {
    AddTransaction(
        id = "add_transaction",
        writes = true,
        description = "Record money spent or received",
        parameters = listOf(
            ToolParameter("amount", "number", "Baht, up to two decimals", required = true),
            ToolParameter("direction", "string", "expense or income"),
            ToolParameter("category", "string", "A category name"),
            ToolParameter("note", "string", "What it was for"),
            ToolParameter("date", "string", "yyyy-MM-dd; today when left out"),
        ),
    ),
    SpendSummary(
        id = "spend_summary",
        writes = false,
        description = "How much was spent in a period",
        parameters = listOf(ToolParameter("period", "string", "today, week or month", required = true)),
    ),
    FindTransactions(
        id = "find_transactions",
        writes = false,
        description = "Find rows by words, category or period",
        parameters = listOf(
            ToolParameter("text", "string", "Words in the row's name"),
            ToolParameter("category", "string", "A category name"),
            ToolParameter("period", "string", "today, week or month"),
        ),
    ),
    Recategorise(
        id = "recategorise",
        writes = true,
        description = "Move one row to another category",
        parameters = listOf(
            ToolParameter("transaction_id", "string", "The row", required = true),
            ToolParameter("category", "string", "The new category name", required = true),
        ),
    ),
    DeleteTransaction(
        id = "delete_transaction",
        writes = true,
        description = "Remove one row",
        parameters = listOf(ToolParameter("transaction_id", "string", "The row", required = true)),
    ),
    AddGroupExpense(
        id = "add_group_expense",
        writes = true,
        description = "Add an expense to a shared group",
        parameters = listOf(
            ToolParameter("group", "string", "The group's name", required = true),
            ToolParameter("amount", "number", "Baht, up to two decimals", required = true),
            ToolParameter("description", "string", "What it was for"),
        ),
    ),
    RecordPayment(
        id = "record_payment",
        writes = true,
        description = "Record a payment between two members of a group",
        parameters = listOf(
            ToolParameter("group", "string", "The group's name", required = true),
            ToolParameter("from", "string", "Who paid"),
            ToolParameter("to", "string", "Who was paid"),
        ),
    ),
    AddRepeat(
        id = "add_repeat",
        writes = true,
        description = "Set up a repeating expense in a group",
        parameters = listOf(ToolParameter("group", "string", "The group's name", required = true)),
    ),
    ;

    companion object {
        fun byId(id: String): AgentTool? = entries.firstOrNull { it.id == id }
    }
}

/** One argument of a tool, described the way a function-calling model reads a schema. */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
)
