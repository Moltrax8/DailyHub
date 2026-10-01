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

    // Takvimde gösterilecek görevler: tamamlanmamış olanlar (tekrarlayanlar zaten hep açık kalır).
    val tasks: StateFlow<List<Task>> = taskRepo.observeAll()
        .map { list -> list.filter { !it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Bir görevin belirli bir [date] gününde takvimde görünüp görünmeyeceğini hesaplar. Tekrarlayan
 * görevler için yineleme biçimine göre (her gün / haftanın günleri / aylık / gün aralığı) o günde
 * bir tekrar düşüp düşmediğine bakılır. Tek seferlik görevler yalnızca kendi bitiş gününde görünür.
 * Görevin ilk gününden (anchor) önceki günlerde tekrar gösterilmez.
 */
fun Task.occursOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val anchorMillis = dueDate ?: createdAt
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()

    if (!isRecurring) {
        // Bitiş tarihi olmayan tek seferlik görev takvimde yer almaz.
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
            // Ayın son gününe kırp: 29/30/31'ine ayarlı bir görev, o günü olmayan aylarda (Şubat,
            // 30 günlük aylar) ayın SON gününde görünür — böylece kısa aylarda kaybolmaz. Bu,
            // tamamlama tarafındaki plusMonths kırpmasıyla (Task.nextRecurrenceDue) tutarlıdır.
            val targetDay = minOf(anchor.dayOfMonth, date.lengthOfMonth())
            date.dayOfMonth == targetDay
        }
        RecurrenceType.INTERVAL, null -> {
            val step = intervalDays ?: return false
            if (step <= 0) false else ChronoUnit.DAYS.between(anchor, date) % step == 0L
        }
    }
}
