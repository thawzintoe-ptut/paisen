package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * FR-19: a day is a home day (#43), Bangkok for a baht ledger and Yangon for a kyat one, never the
 * device's local midnight, so a trip does not move yesterday's spending.
 */
val HomeTime: TimeZone get() = Home.zone

/**
 * Bangkok itself, for what is not the ledger's: the time a K PLUS slip prints, and shared groups,
 * whose members' phones must agree on one calendar.
 */
val BangkokTime: TimeZone = TimeZone.of("Asia/Bangkok")

/**
 * Midnight in [zone] as a UTC instant. F2 — this is the only way a Bangkok day becomes a
 * database bound, because SQLite's own date functions read the device zone.
 */
fun LocalDate.startOfDayEpochMillis(zone: TimeZone = HomeTime): Long =
    atStartOfDayIn(zone).toEpochMilliseconds()

data class DayBucket(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val summary: PeriodSummary,
)

/**
 * [newestFirst] because the two readers want opposite orders: a week chart runs Monday forward,
 * while the Ledger puts today at the top. Defaulting to oldest-first keeps the chart's caller
 * unchanged.
 */
fun List<Transaction>.bucketByDay(
    zone: TimeZone = HomeTime,
    newestFirst: Boolean = false,
): List<DayBucket> =
    groupBy { transaction ->
        Instant.fromEpochMilliseconds(transaction.occurredAtEpochMillis)
            .toLocalDateTime(zone)
            .date
    }
        .map { (date, transactions) -> DayBucket(date, transactions, transactions.summarise()) }
        .let { buckets -> if (newestFirst) buckets.sortedByDescending { it.date } else buckets.sortedBy { it.date } }
