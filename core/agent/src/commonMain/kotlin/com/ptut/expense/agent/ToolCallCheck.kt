package com.ptut.expense.agent

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.shared.SharedGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What the app knows when it checks a call: the reader's categories, groups and rows, and the time. */
data class AgentContext(
    val categories: List<Category>,
    val subCategories: List<SubCategory>,
    val groups: List<SharedGroup>,
    val rowIds: Set<String>,
    val now: Instant,
)

sealed interface CheckedCall {
    data class Valid(val call: ToolCall) : CheckedCall

    /** Sent back to the model once, word for word, so it can try again (#207). */
    data class Invalid(val error: String) : CheckedCall
}

/**
 * #207: a model's reply is a guess until this says otherwise. Every id must name something the
 * reader has, every amount must be [Satang] with at most two decimals, and a date may not be later
 * than today. Nothing here writes.
 */
class ToolCallCheck(private val grammar: CommandGrammar = CommandGrammar()) {

    fun check(reply: ModelReply, context: AgentContext): CheckedCall {
        val tool = AgentTool.byId(reply.function) ?: return invalid("There is no tool called ${reply.function}.")
        val missing = tool.parameters.filter { it.required && reply.arguments[it.name].isNullOrBlank() }
        if (missing.isNotEmpty()) return invalid("${tool.id} needs ${missing.joinToString { it.name }}.")
        val args = reply.arguments
        return when (tool) {
            AgentTool.AddTransaction -> addTransaction(args, context)

            AgentTool.SpendSummary -> window(args["period"])?.let { CheckedCall.Valid(ReadCall.SpendSummary(it)) }
                ?: invalid("period must be today, week or month.")

            AgentTool.FindTransactions -> findTransactions(args, context)

            AgentTool.Recategorise -> withRow(args, context) { id ->
                category(args["category"], context.categories)?.let { CheckedCall.Valid(ProposedAction.Recategorise(id, it.id)) }
                    ?: unknownCategory(args["category"], context.categories)
            }

            AgentTool.DeleteTransaction -> withRow(args, context) { CheckedCall.Valid(ProposedAction.DeleteTransaction(it)) }

            AgentTool.AddGroupExpense -> withGroup(args, context) { group ->
                val amount = strictAmount(args["amount"]) ?: return@withGroup badAmount(args["amount"])
                CheckedCall.Valid(ProposedAction.AddGroupExpense(group.id, amount, args["description"]?.trim()?.ifEmpty { null }))
            }

            AgentTool.RecordPayment -> withGroup(args, context) { group ->
                CheckedCall.Valid(
                    ProposedAction.RecordPayment(
                        groupId = group.id,
                        fromMemberId = member(group, args["from"]),
                        toMemberId = member(group, args["to"]),
                    ),
                )
            }

            AgentTool.AddRepeat -> withGroup(args, context) { CheckedCall.Valid(ProposedAction.AddRepeat(it.id)) }
        }
    }

    private fun addTransaction(args: Map<String, String>, context: AgentContext): CheckedCall {
        val amount = strictAmount(args["amount"]) ?: return badAmount(args["amount"])
        val direction = when (args["direction"]?.trim()?.lowercase()) {
            null, "", "expense" -> Direction.Expense
            "income" -> Direction.Income
            else -> return invalid("direction must be expense or income.")
        }
        val offered = context.categories.forDirection(direction)
        val named = args["category"]?.takeIf { it.isNotBlank() }
        val category = named?.let { category(it, offered) ?: return unknownCategory(it, offered) }
        val occurredAt = args["date"]?.takeIf { it.isNotBlank() }?.let { text ->
            val date = runCatching { LocalDate.parse(text.trim()) }.getOrNull() ?: return invalid("date must be yyyy-MM-dd.")
            val now = context.now.toLocalDateTime(BangkokTime)
            if (date > now.date) return invalid("date $text is after today.")
            // Today's own date is "now", which is what a row with no time already means.
            if (date == now.date) null else LocalDateTime(date, now.time).toInstant(BangkokTime).toEpochMilliseconds()
        }
        return CheckedCall.Valid(
            ProposedAction.AddTransaction(
                amount = amount,
                direction = direction,
                categoryId = category?.id,
                memo = args["note"]?.trim()?.ifEmpty { null },
                occurredAtEpochMillis = occurredAt,
            ),
        )
    }

    private fun findTransactions(args: Map<String, String>, context: AgentContext): CheckedCall {
        val named = args["category"]?.takeIf { it.isNotBlank() }
        val category = named?.let { category(it, context.categories) ?: return unknownCategory(it, context.categories) }
        val period = args["period"]?.takeIf { it.isNotBlank() }
        val window = period?.let { window(it) ?: return invalid("period must be today, week or month.") }
        return CheckedCall.Valid(ReadCall.FindTransactions(args["text"]?.trim()?.ifEmpty { null }, category?.id, window))
    }

    private fun withRow(args: Map<String, String>, context: AgentContext, call: (String) -> CheckedCall): CheckedCall {
        val id = args["transaction_id"].orEmpty().trim()
        return if (id in context.rowIds) call(id) else invalid("No row has the id $id. Use find_transactions first.")
    }

    private fun withGroup(args: Map<String, String>, context: AgentContext, call: (SharedGroup) -> CheckedCall): CheckedCall {
        val name = args["group"].orEmpty().trim()
        val group = context.groups.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id == name }
            ?: return invalid("No group is called $name. The groups are: ${context.groups.joinToString { it.name }}.")
        return call(group)
    }

    private fun category(text: String?, offered: List<Category>): Category? {
        val name = text?.trim().orEmpty()
        if (name.isEmpty()) return null
        return offered.firstOrNull { it.id == name || it.name.equals(name, ignoreCase = true) } ?: grammar.categoryIn(name, offered)
    }

    private fun member(group: SharedGroup, name: String?): String? {
        val wanted = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return group.activeMembers.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
    }

    /** The whole argument must be the number: "60.555" is refused, not read as ฿60.55. */
    private fun strictAmount(text: String?): Satang? {
        val number = text?.trim()?.replace(",", "") ?: return null
        if (!STRICT_AMOUNT.matches(number)) return null
        return grammar.amountOf(number)
    }

    private fun window(text: String?): SpendWindow? = when (text?.trim()?.lowercase()) {
        "today" -> SpendWindow.Today
        "week" -> SpendWindow.Week
        "month" -> SpendWindow.Month
        else -> null
    }

    private fun unknownCategory(name: String?, offered: List<Category>) =
        invalid("No category is called $name. The categories are: ${offered.joinToString { it.name }}.")

    private fun badAmount(text: String?) = invalid("amount $text is not baht with at most two decimals.")

    private fun invalid(error: String) = CheckedCall.Invalid(error)

    private companion object {
        val STRICT_AMOUNT = Regex("""\d+(\.\d{1,2})?""")
    }
}
