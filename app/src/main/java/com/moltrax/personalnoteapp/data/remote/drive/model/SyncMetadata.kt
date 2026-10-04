package com.moltrax.personalnoteapp.data.remote.drive.model

import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.Priority
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.domain.model.SubTask
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import kotlinx.serialization.Serializable

@Serializable
data class SyncMetadata(
    val version: Int = 4,
    val lastModifiedUtc: String,
    // All fields default to empty lists: old (v1/v2/v3) backup files still decode cleanly
    val tasks: List<TaskJson> = emptyList(),
    val categories: List<CategoryJson> = emptyList(),
    val workoutGroups: List<WorkoutGroup> = emptyList(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    // List-order vector (task ids, sortOrder ASCENDING) + LWW clock. In v3 backups it arrives
    // empty/0 → local order is kept. On concurrent reorderings the larger clock wins.
    val taskOrder: List<String> = emptyList(),
    val taskOrderUpdatedAt: Long = 0L,
)

@Serializable
data class CategoryJson(
    val name: String,
    val isPermanent: Boolean = false,
    // Tombstone (v18): when true this category is deleted — the tombstone wins the merge.
    val isDeleted: Boolean = false,
)

fun Category.toJson() = CategoryJson(name = name, isPermanent = isPermanent, isDeleted = isDeleted)

fun CategoryJson.toDomain() = Category(name = name, isPermanent = isPermanent, isDeleted = isDeleted)

@Serializable
data class TaskJson(
    val id: String,
    val title: String,
    val notes: String? = null,
    val dueDate: Long? = null,
    // Deprecated since v1.1 (Phase 1.2, hidden from UI): retained so old backups decode.
    val priority: String = "MEDIUM",
    val isDone: Boolean = false,
    val isRecurring: Boolean = false,
    val intervalDays: Int? = null,
    // Rich recurrence (in old backups the field is missing: null / empty → intervalDays behavior is kept)
    val recurrenceType: String? = null,
    val recurrenceDaysOfWeek: List<Int> = emptyList(),
    // Deprecated since v1.1 (Phase 1.2, hidden from UI): retained so old backups decode.
    val focusDurationSeconds: Int = 1500,
    // Legacy single tag (deprecated since v1.1, kept for old backups).
    val category: String? = null,
    // Live tag set (Phase 2): union-merged, per-link LWW positions in categoryOrders.
    // Old backups carry only `category` → decoded as a single tag.
    val categories: List<String> = emptyList(),
    val categoryOrders: Map<String, Long> = emptyMap(),
    // Subtasks (checklist) — travel with the task during sync (empty in old backups)
    val subtasks: List<SubTask> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    // Default 0: decodes cleanly when the field is missing in old backups (backward compatible)
    val sortOrder: Long = 0L,
    // Workout/program links — preserved across sync (null/0 in old backups)
    val linkedWorkoutId: String? = null,
    val linkedProgramId: String? = null,
    val programStartIndex: Int = 0,
    // Deprecated: field is kept for compatibility with old backups, no longer read.
    val isPenalty: Boolean = false,
    // Tombstone (v18): when true this task is deleted — newer updatedAt wins the LWW merge.
    val isDeleted: Boolean = false,
)

fun Task.toJson() = TaskJson(
    id = id, title = title, notes = notes, dueDate = dueDate, priority = priorityRaw ?: priority.name,
    isDone = isDone, isRecurring = isRecurring, intervalDays = intervalDays,
    recurrenceType = recurrenceType?.name, recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    focusDurationSeconds = focusDurationSeconds, category = categoryNames.sorted().firstOrNull(),
    categories = categoryNames.sorted(), categoryOrders = categoryOrders,
    subtasks = subtasks,
    createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt, sortOrder = sortOrder,
    linkedWorkoutId = linkedWorkoutId, linkedProgramId = linkedProgramId,
    programStartIndex = programStartIndex,
    isDeleted = isDeleted,
)

fun TaskJson.toDomain() = Task(
    id = id, title = title, notes = notes, dueDate = dueDate,
    priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
    priorityRaw = if (runCatching { Priority.valueOf(priority) }.isSuccess) null else priority,
    isDone = isDone, isRecurring = isRecurring, intervalDays = intervalDays,
    recurrenceType = recurrenceType?.let { runCatching { RecurrenceType.valueOf(it) }.getOrNull() },
    recurrenceDaysOfWeek = recurrenceDaysOfWeek,
    focusDurationSeconds = focusDurationSeconds,
    category = category,
    categoryNames = if (categories.isNotEmpty()) categories.toSet()
        else category?.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty(),
    categoryOrders = categoryOrders,
    subtasks = subtasks,
    createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt,
    sortOrder = sortOrder,
    linkedWorkoutId = linkedWorkoutId, linkedProgramId = linkedProgramId,
    programStartIndex = programStartIndex,
    isDeleted = isDeleted,
)
