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
    indices = [Index("category"), Index("linkedWorkoutId"), Index("linkedProgramId")],
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
    val linkedWorkoutId: String? = null,
    val linkedProgramId: String? = null,
    val programStartIndex: Int = 0,
    val sortOrder: Long = 0L,
    // Deprecated (gamification/"Penalty Zone" removed). Column kept in the schema for backward
    // compatibility; always written as false.
    val isPenalty: Boolean = false,
    // Tombstone (v18): deleted tasks are kept, visible queries filter them, sync includes them.
    val isDeleted: Boolean = false,
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
    linkedWorkoutId = linkedWorkoutId,
    linkedProgramId = linkedProgramId,
    programStartIndex = programStartIndex,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
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
    category = category,
    subtasks = subtasks,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    linkedWorkoutId = linkedWorkoutId,
    linkedProgramId = linkedProgramId,
    programStartIndex = programStartIndex,
    sortOrder = sortOrder,
    isPenalty = false,
    isDeleted = isDeleted,
)
