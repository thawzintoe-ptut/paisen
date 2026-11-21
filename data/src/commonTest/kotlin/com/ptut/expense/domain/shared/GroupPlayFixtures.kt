package com.ptut.expense.domain.shared

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/** A group of #93–#97's tests, its members by id, "me" first. */
internal fun playGroup(vararg ids: String, type: GroupType = GroupType.Trip, endedOn: LocalDate? = null, playOff: Set<String> = emptySet()) = SharedGroup(
    id = "g1",
    name = "Chiang Mai",
    type = type,
    icon = "place",
    simplifyDebts = true,
    createdAtEpochMillis = 0,
    meMemberId = ids.first(),
    members = ids.map { Member(it, "g1", it, personId = null, playOff = it in playOff) },
    endedOn = endedOn,
)

internal fun noon(day: LocalDate): Long = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds()

/** An expense split equally between [sharers]. */
internal fun playExpense(id: String, payer: String, minor: Long, day: LocalDate, vararg sharers: String, author: String = payer) = SharedExpense(
    id = id,
    versionId = "$id-v1",
    version = 1,
    groupId = "g1",
    description = id,
    occurredAtEpochMillis = noon(day),
    categoryId = null,
    subCategoryId = null,
    note = null,
    currency = "THB",
    amountMinor = minor,
    rate = null,
    satang = Satang(minor),
    payerMemberId = payer,
    splitMode = SplitMode.Equally,
    shares = sharers.map { Share(it, 1, minor / sharers.size, Satang(minor / sharers.size)) },
    authorMemberId = author,
    createdAtEpochMillis = noon(day),
    removed = false,
)

internal fun playPayment(id: String, from: String, to: String, minor: Long, day: LocalDate, author: String = from) = SharedPayment(
    id = id,
    groupId = "g1",
    fromMemberId = from,
    toMemberId = to,
    amount = Satang(minor),
    method = PaymentMethod.Cash,
    occurredAtEpochMillis = noon(day),
    authorMemberId = author,
    createdAtEpochMillis = noon(day),
)

internal fun playSnapshot(group: SharedGroup, expenses: List<SharedExpense> = emptyList(), payments: List<SharedPayment> = emptyList()) =
    SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = expenses, payments = payments, links = emptyMap())
