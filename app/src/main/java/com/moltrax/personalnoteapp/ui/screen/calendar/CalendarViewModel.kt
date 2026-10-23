package com.moltrax.personalnoteapp.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    taskRepo: TaskRepository,
) : ViewModel() {

    // Tasks shown on the calendar: incomplete ones (recurring ones always stay open anyway).
    val tasks: StateFlow<List<Task>> = taskRepo.observeAll()
        .map { list -> list.filter { !it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Computes whether a task appears on the calendar on a given [date]. For recurring tasks, checks
 * whether a repetition falls on that day per the repetition type (daily / weekdays / monthly / day
 * interval). One-shot tasks only appear on their own due day.
 * No repetition is shown on days before the task's first day (anchor).
 */
fun Task.occursOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val anchorMillis = dueDate ?: createdAt
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()

    if (!isRecurring) {
        // A one-shot task without a due date has no place on the calendar.
        return dueDate != null && anchor == date
    }
    if (date.isBefore(anchor)) return false
    return when (recurrenceType) {
        RecurrenceType.DAILY -> true
        RecurrenceType.WEEKLY -> {
            val days = recurrenceDaysOfWeek.ifEmpty { listOf(anchor.dayOfWeek.value) }
            date.dayOfWeek.value in days
        }
        RecurrenceType.MONTHLY -> {
            // Clamp to the last day of the month: a task set to the 29th/30th/31st appears on the LAST
            // day of months lacking that day (February, 30-day months) — so it never disappears in short
            // months. This matches the plusMonths clamping on the completion side (Task.nextRecurrenceDue).
            val targetDay = minOf(anchor.dayOfMonth, date.lengthOfMonth())
            date.dayOfMonth == targetDay
        }
        RecurrenceType.INTERVAL, null -> {
            val step = intervalDays ?: return false
            if (step <= 0) false else ChronoUnit.DAYS.between(anchor, date) % step == 0L
        }
    }
}
