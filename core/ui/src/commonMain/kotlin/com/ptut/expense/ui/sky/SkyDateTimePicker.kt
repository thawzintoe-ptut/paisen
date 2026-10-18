package com.ptut.expense.ui.sky

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.locale.LocalCopy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private enum class PickStep { None, Date, Time }

/**
 * "Date & time" as a row: a tap picks the day, then the clock, never later than now. The one file
 * that reads the phone's own time zone (the reader's choice); totals still count the Bangkok day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyDateTimeRow(
    epochMillis: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = LocalCopy.current.shell.dateAndTime,
    clock: Clock = Clock.System,
    /** False shows the time without opening a picker, for a date another screen owns (#186). */
    enabled: Boolean = true,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    var step by remember { mutableStateOf(PickStep.None) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val shown = epochMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone) }

    SkyKeyValueRow(
        label = label,
        value = shown?.let { "${it.date.dayLabel()}, ${it.hour.pad()}:${it.minute.pad()}" } ?: LocalCopy.current.shell.now,
        modifier = if (enabled) modifier.tappable(RectangleShape, Role.Button) { step = PickStep.Date } else modifier,
    )

    val today = clock.now().toLocalDateTime(zone).date
    when (step) {
        PickStep.None -> Unit

        PickStep.Date -> {
            val notAfterToday = remember(today) { NotAfter(today) }
            val state = rememberDatePickerState(
                initialSelectedDateMillis = (shown?.date ?: today).utcMillis(),
                selectableDates = notAfterToday,
            )
            DatePickerDialog(
                onDismissRequest = { step = PickStep.None },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDate = state.selectedDateMillis?.let(::utcDate)
                            step = if (pickedDate == null) PickStep.None else PickStep.Time
                        },
                    ) { Text(LocalCopy.current.shell.next) }
                },
                dismissButton = { TextButton(onClick = { step = PickStep.None }) { Text(LocalCopy.current.common.cancel) } },
            ) { DatePicker(state = state) }
        }

        PickStep.Time -> {
            val start = shown?.time ?: clock.now().toLocalDateTime(zone).time
            val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { step = PickStep.None },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val date = pickedDate ?: today
                            val unchanged = shown != null && date == shown.date &&
                                state.hour == shown.hour && state.minute == shown.minute
                            if (!unchanged) {
                                val picked = LocalDateTime(date, LocalTime(state.hour, state.minute)).toInstant(zone)
                                onPick(minOf(picked.toEpochMilliseconds(), clock.now().toEpochMilliseconds()))
                            }
                            step = PickStep.None
                        },
                    ) { Text(LocalCopy.current.common.done) }
                },
                dismissButton = { TextButton(onClick = { step = PickStep.None }) { Text(LocalCopy.current.common.cancel) } },
                text = { TimePicker(state = state) },
            )
        }
    }
}

/** The date picker speaks in UTC midnights, whatever zone the phone is in. */
private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun utcDate(utcMillis: Long): LocalDate = Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date

@OptIn(ExperimentalMaterial3Api::class)
private class NotAfter(private val last: LocalDate) : SelectableDates {
    private val lastMillis = last.utcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastMillis

    override fun isSelectableYear(year: Int): Boolean = year <= last.year
}

private fun Int.pad(): String = toString().padStart(2, '0')
