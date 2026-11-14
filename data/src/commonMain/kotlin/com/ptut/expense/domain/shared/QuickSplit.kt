package com.ptut.expense.domain.shared

/**
 * The two splits one tap picks on Add expense. [OwedInFull] is saved as [SplitMode.Shares] with the
 * payer left out and one share for everyone else, so nothing new is stored or synced.
 */
enum class QuickSplit { Equally, OwedInFull }

/** [QuickSplit.OwedInFull]'s shares: the payer carries nothing, the others split the total equally. */
fun quickSplitWeights(payerId: String, members: List<SplitMember>): Map<String, Long> =
    members.filter { it.id != payerId }.associate { it.id to 1L }

/** Which quick choice a split is, or null when it was set by hand in Split options. */
fun quickSplitOf(mode: SplitMode, payerId: String, members: List<SplitMember>, weights: Map<String, Long>): QuickSplit? = when (mode) {
    SplitMode.Equally -> QuickSplit.Equally

    SplitMode.Shares -> {
        val owedInFull = quickSplitWeights(payerId, members)
        QuickSplit.OwedInFull.takeIf { owedInFull.isNotEmpty() && members.any { it.id == payerId } && weights.filterValues { it > 0 } == owedInFull }
    }

    SplitMode.Exact, SplitMode.Percent -> null
}
