package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskCompletionTest {
    private val now = LocalDateTime.of(2026, 6, 25, 9, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun ordinaryTaskClosesWithoutChangingItsContent() {
        val task = Task(id = "task", title = "Title", notes = "Notes", dueDate = now + 1000,
            subtasks = listOf(SubTask("sub", "Pending")), categoryNames = setOf("Work"), sortOrder = -4)
        assertEquals(task.copy(isDone = true, completedAt = now, updatedAt = now), task.toggledCompletion(now))
        assertFalse(task.isDone)
    }

    @Test fun completedTaskReopensWithoutAdvancingEvenIfItIsRecurring() {
        val task = Task(title = "Done", isDone = true, completedAt = now - 1000, dueDate = now,
            isRecurring = true, recurrenceType = RecurrenceType.DAILY)
        assertEquals(task.copy(isDone = false, completedAt = null, updatedAt = now), task.toggledCompletion(now))
    }

    @Test fun recurringTaskAdvancesUsingTheExistingCompletionRules() {
        val task = Task(title = "Recurring", dueDate = now, isRecurring = true, recurrenceType = RecurrenceType.DAILY,
            subtasks = listOf(SubTask("done", "Done", true)))
        val result = task.toggledCompletion(now)
        val tomorrow = LocalDateTime.of(2026, 6, 26, 9, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(task.copy(dueDate = tomorrow, isDone = false, completedAt = null, updatedAt = now), result)
    }

    @Test fun invalidLegacyRecurrenceStillClosesNormally() {
        val task = Task(title = "Invalid recurrence", isRecurring = true, intervalDays = 0)
        assertEquals(task.copy(isDone = true, completedAt = now, updatedAt = now), task.toggledCompletion(now))
    }

    @Test fun undoPreservesAllInterveningEditsOutsideCompletionFields() {
        val before = Task(id = "task", title = "Before", dueDate = now, updatedAt = now - 1000)
        val current = before.copy(title = "Edited", notes = "New notes", isDone = true, completedAt = now,
            dueDate = now + 1000, sortOrder = 19, categoryNames = setOf("New"), categoryOrders = mapOf("New" to 7L),
            subtasks = listOf(SubTask("sub", "Edited checklist", true)), isRecurring = true,
            recurrenceType = RecurrenceType.WEEKLY, recurrenceDaysOfWeek = listOf(1, 5))
        assertEquals(current.copy(isDone = false, completedAt = null, dueDate = before.dueDate, updatedAt = now + 2000),
            current.restoringCompletionFrom(before, now + 2000))
        assertTrue(current.isDone)
        assertEquals("Before", before.title)
    }

    @Test fun undoOfReopenRestoresCompletedStateIncludingNullDeadline() {
        val before = Task(title = "Before", isDone = true, completedAt = now - 1000, dueDate = null)
        val current = before.copy(isDone = false, completedAt = null, dueDate = now)
        assertEquals(current.copy(isDone = true, completedAt = before.completedAt, dueDate = null, updatedAt = now),
            current.restoringCompletionFrom(before, now))
    }

    @Test fun undoOfRecurringCompletionRestoresThePreviousCycle() {
        val before = Task(title = "Recurring", dueDate = now, isRecurring = true, recurrenceType = RecurrenceType.DAILY)
        val advanced = before.toggledCompletion(now)
        assertEquals(before.copy(updatedAt = now + 1), advanced.restoringCompletionFrom(before, now + 1))
    }
}
