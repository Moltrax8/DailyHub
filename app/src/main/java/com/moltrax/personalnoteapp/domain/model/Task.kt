package com.moltrax.personalnoteapp.domain.model

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val isDone: Boolean = false,
    val isRecurring: Boolean = false,
    val intervalDays: Int? = null,
    // Rich recurrence format. NULL = legacy "day interval" behavior (intervalDays is used).
    val recurrenceType: RecurrenceType? = null,
    // For WEEKLY, the selected days of week (ISO: 1=Monday .. 7=Sunday). When empty the task
    // repeats weekly on its own day (every 7 days).
    val recurrenceDaysOfWeek: List<Int> = emptyList(),
    val focusDurationSeconds: Int = 1500,
    val category: String? = null,
    // Checklist items under the parent task. Stored embedded with the task.
    val subtasks: List<SubTask> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    // Link to a single workout (Day A etc.). Never used together with linkedProgramId.
    val linkedWorkoutId: String? = null,
    // Link to a whole workout program (WorkoutGroup); [programStartIndex] determines which day
    // of the cycle to start from. When linked, completing the task resolves that day's workout.
    val linkedProgramId: String? = null,
    val programStartIndex: Int = 0,
    // Manual ordering key: the list is shown in sortOrder ASCENDING (small = on top).
    // Migration assigns -createdAt to old tasks (newest stays on top). New tasks get one less
    // than the current minimum, so they are prepended to the list.
    val sortOrder: Long = 0L,
    // Tombstone: the deleted task record is kept so Drive sync does not resurrect the deleted
    // task from remote. Visible lists are filtered at the DAO layer (isDeleted = 0).
    val isDeleted: Boolean = false,
    // Raw form of unknown (future-version) priority names. When null, [priority] is valid;
    // when set it is preserved as-is on the read/write path (forward compatibility).
    val priorityRaw: String? = null,
) {
    /** Number of completed subtasks. */
    val doneSubtaskCount: Int get() = subtasks.count { it.isDone }

    /** Total number of subtasks. */
    val subtaskCount: Int get() = subtasks.size

    /** Whether there are still pending subtasks (at least one exists and not all are done). */
    val hasIncompleteSubtasks: Boolean get() = subtasks.any { !it.isDone }

    /** Subtask progress between 0f..1f (0 when there are no subtasks). */
    val subtaskProgress: Float
        get() = if (subtasks.isEmpty()) 0f else doneSubtaskCount.toFloat() / subtasks.size
}

/**
 * Computes the NEXT due time a recurring task moves to when completed; returns null when the
 * task does not repeat or has no valid format. Always advances at least one cycle and keeps
 * advancing past [now] if needed (refreshes the chain for overdue tasks). [withCompletion]
 * shares the same logic.
 */
fun Task.nextRecurrenceDue(now: Long = System.currentTimeMillis()): Long? {
    if (!isRecurring) return null
    val base = dueDate ?: now
    return when (recurrenceType) {
        RecurrenceType.DAILY -> advance(base, now) { it.plusDays(1) }
        RecurrenceType.MONTHLY -> advance(base, now) { it.plusMonths(1) }
        RecurrenceType.WEEKLY -> nextWeekly(base, now)
        RecurrenceType.INTERVAL, null -> {
            val step = intervalDays ?: 0
            if (step <= 0) return null
            // Advance by calendar day (NOT a fixed 24s multiple): the due time does not drift
            // across DST transitions; shares the same LocalDateTime-based behavior as DAILY/MONTHLY/WEEKLY.
            advance(base, now) { it.plusDays(step.toLong()) }
        }
    }
}

/** Advances with the given [step] (day/month addition) at least once, then until past [now]. */
private inline fun advance(base: Long, now: Long, step: (java.time.LocalDateTime) -> java.time.LocalDateTime): Long {
    val zone = ZoneId.systemDefault()
    var dt = Instant.ofEpochMilli(base).atZone(zone).toLocalDateTime()
    do { dt = step(dt) } while (dt.atZone(zone).toInstant().toEpochMilli() <= now)
    return dt.atZone(zone).toInstant().toEpochMilli()
}

/** Next day for weekly repetition for [recurrenceDaysOfWeek] (the task's own day when empty). */
private fun Task.nextWeekly(base: Long, now: Long): Long {
    val zone = ZoneId.systemDefault()
    val baseDt = Instant.ofEpochMilli(base).atZone(zone).toLocalDateTime()
    val days = recurrenceDaysOfWeek.takeIf { it.isNotEmpty() }?.toSet()
        ?: setOf(baseDt.dayOfWeek.value)
    var dt = baseDt.plusDays(1) // after today's completion, tomorrow at the earliest
    while (dt.dayOfWeek.value !in days || dt.atZone(zone).toInstant().toEpochMilli() <= now) {
        dt = dt.plusDays(1)
    }
    return dt.atZone(zone).toInstant().toEpochMilli()
}

/**
 * Marks a task as "completed". For recurring tasks (isRecurring + valid recurrence) the task
 * is not closed; instead it moves to the next cycle and stays open. This way recurring tasks
 * truly repeat. Used by both the main list and the focus timer.
 */
fun Task.withCompletion(now: Long = System.currentTimeMillis()): Task {
    val next = nextRecurrenceDue(now)
    return if (next != null) {
        copy(dueDate = next, isDone = false, completedAt = null, updatedAt = now)
    } else {
        copy(isDone = true, completedAt = now, updatedAt = now)
    }
}
