package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang

/**
 * What the parser could make of one slip. Three outcomes, because the importer does three
 * different things with them: file it, queue it for a human, or refuse it.
 *
 * There is no fourth "probably" state on purpose. FR-7: a field the reader could not make out
 * stays absent rather than carrying a best guess, because a pre-filled figure invites a careless
 * confirm.
 */
sealed interface SlipRead {

    /**
     * The two outcomes that read a merchant. Complete narrows each field to non-null rather than
     * restating it, so a caller that only needs "whatever was read" has one type to take.
     */
    sealed interface Readable : SlipRead {
        val counterparty: String
        val amount: Satang?
        val occurredAtEpochMillis: Long?
        val reference: String?

        /**
         * Fields the parser produced but had to make a judgement to produce. Empty is the common
         * case and the one to design for: of the nineteen recorded slips, eight carry nothing the
         * reader was unsure of at all.
         *
         * Distinct from a null field, which is the reader having failed outright. A marked field
         * holds a value worth showing — it is just worth checking against the picture first.
         */
        val lowConfidence: Set<SlipField>

        /**
         * #26: the name in the sender block, or null when none read. A slip from your own phone names
         * you here; a slip someone sent you names them, and that is how money in is told apart.
         */
        val sender: String?

        /** #33: the currency [amount] is in; its minor units, so kyat (0 digits) reads 29,500 as 29500. */
        val currency: String

        /** #33: a wallet receipt that says the direction itself (a minus sign, a "To" block); null leaves it to #26. */
        val knownDirection: Direction?

        /** J04: the recipient's account as [accountMask] names it, or null when the slip printed none. */
        val recipientAccount: String?
    }

    /**
     * Every field the ledger needs. [reference] is not optional here — it is half of the F3
     * content key, and a row filed without one cannot be recognised as a duplicate later.
     */
    data class Complete(
        override val counterparty: String,
        override val amount: Satang,
        override val occurredAtEpochMillis: Long,
        override val reference: String,
        override val lowConfidence: Set<SlipField> = emptySet(),
        override val sender: String? = null,
        override val currency: String = "THB",
        override val knownDirection: Direction? = null,
        override val recipientAccount: String? = null,
    ) : Readable

    /**
     * A slip worth showing someone. The counterparty read, so the reader can be asked a question
     * they can actually answer; at least one of the rest did not.
     */
    data class Partial(
        override val counterparty: String,
        override val amount: Satang?,
        override val occurredAtEpochMillis: Long?,
        override val reference: String?,
        override val lowConfidence: Set<SlipField> = emptySet(),
        override val sender: String? = null,
        override val currency: String = "THB",
        override val knownDirection: Direction? = null,
        override val recipientAccount: String? = null,
    ) : Readable

    /**
     * Not enough to ask a question about. C-3 ships no Thai model, so a slip whose recipient
     * name is Thai only lands here — an empty string or a transliteration would both be a guess,
     * and the rule engine would then learn a merchant that does not exist.
     */
    data object Unreadable : SlipRead
}

/**
 * A field on a slip, named so a low-confidence mark can point at one. Only the three the confirm
 * screen lets someone check against the picture — the date is shown but never typed, so marking
 * it would ask for a correction the screen cannot take.
 */
enum class SlipField { Amount, Counterparty, Reference }
