package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Priority
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.domain.model.SubTask
import com.moltrax.personalnoteapp.domain.model.Task

@Entity(
    tableName = "tasks",
    indices = [Index("category")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val dueDate: Long?,
    val priority: String,
    val isDone: Boolean,
    val isRecurring: Boolean,
    val intervalDays: Int?,
    val recurrenceType: String? = null,
    val recurrenceDaysOfWeek: List<Int> = emptyList(),
    val focusDurationSeconds: Int,
    val category: String?,
    val subtasks: List<SubTask> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val sortOrder: Long = 0L,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate,
    priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
    isDone = isDone,
    isRecurring = isRecurring,
    intervalDays = intervalDays,
    recurrenceType = recurrenceType?.let { runCatching { RecurrenceType.valueOf(it) }.getOrNull() },
    recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    focusDurationSeconds = focusDurationSeconds,
    category = category,
    subtasks = subtasks,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    sortOrder = sortOrder,
    priorityRaw = if (runCatching { Priority.valueOf(priority) }.isSuccess) null else priority,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate,
    priority = priorityRaw ?: priority.name,
    isDone = isDone,
    isRecurring = isRecurring,
    intervalDays = intervalDays,
    recurrenceType = recurrenceType?.name,
    recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    focusDurationSeconds = focusDurationSeconds,
    // Legacy column write: deterministic primary tag for older readers. The live set travels via xref.
    category = categoryNames.sorted().firstOrNull(),
    subtasks = subtasks,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    sortOrder = sortOrder,
)
