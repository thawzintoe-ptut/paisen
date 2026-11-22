package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction

/** Slips from the bank's own folder before a sender counts as the person holding the phone. */
private const val OWNER_AFTER = 2

/**
 * #26: whose phone this is, as the slips print it ("MR. THAW Z"). [name] is null until learned;
 * [candidate] and [seen] are the learning: the same sender on [OWNER_AFTER] of your own slips.
 */
data class SlipOwner(val name: String? = null, val candidate: String? = null, val seen: Int = 0) {

    /**
     * A sender read off a slip the bank app saved, which is always your own payment. A different
     * name starts the count again, so one odd slip cannot become you. A name set by hand stays.
     */
    fun learnFrom(sender: String): SlipOwner = when {
        name != null -> this

        candidate != null && SlipNames.same(candidate, sender) ->
            if (seen + 1 >= OWNER_AFTER) SlipOwner(name = candidate) else copy(seen = seen + 1)

        else -> SlipOwner(candidate = sender, seen = 1)
    }
}

/**
 * #26: money in or money out. A slip is money in only when its sender is someone else and its
 * recipient is you. Until your name is known, every slip stays money out, as before.
 */
fun SlipRead.Readable.direction(owner: String?): Direction {
    val me = owner ?: return Direction.Expense
    val from = sender ?: return Direction.Expense
    return if (!SlipNames.same(from, me) && SlipNames.same(counterparty, me)) Direction.Income else Direction.Expense
}

/**
 * J04: the recipient's account when the recipient is the reader, or null. With the reader's name known,
 * the recipient must be them; before it is learned, a slip whose sender and recipient are one person is
 * a move between their own accounts. Only such a transfer can fill a jar, so a shop stays spending.
 */
fun SlipRead.Readable.ownAccount(owner: String?): String? {
    val account = recipientAccount ?: return null
    val mine = if (owner != null) SlipNames.same(owner, counterparty) else sender?.let { SlipNames.same(it, counterparty) } == true
    return account.takeIf { mine }
}

/**
 * K PLUS prints the sender as `MR. THAW Z` and the recipient as `MR. THAW ZIN TOE`: the title, the
 * given name, then the surname whole or as one letter. Two names are the same person when the
 * given names match and one surname starts with the other.
 */
object SlipNames {

    private val TITLES = setOf("MR", "MRS", "MS", "MISS", "DR")

    fun same(a: String, b: String): Boolean {
        val left = a.parts()
        val right = b.parts()
        if (left.isEmpty() || right.isEmpty() || left.first() != right.first()) return false
        val surnameA = left.getOrNull(1) ?: return right.size == 1
        val surnameB = right.getOrNull(1) ?: return false
        return surnameA.startsWith(surnameB) || surnameB.startsWith(surnameA)
    }

    private fun String.parts(): List<String> = uppercase()
        .replace('.', ' ')
        .replace(',', ' ')
        .split(' ')
        .filter { it.isNotBlank() && it !in TITLES }
}
