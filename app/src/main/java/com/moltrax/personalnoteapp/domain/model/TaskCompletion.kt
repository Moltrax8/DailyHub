package com.moltrax.personalnoteapp.domain.model

/** Completion is a toggle only in the list/widget; complete-only callers keep using [withCompletion]. */
internal fun Task.toggledCompletion(now: Long): Task =
    if (isDone) copy(isDone = false, completedAt = null, updatedAt = now)
    else withCompletion(now)

/** Restore only completion-related fields, retaining edits made since the undo snapshot. */
internal fun Task.restoringCompletionFrom(before: Task, now: Long): Task = copy(
    isDone = before.isDone,
    completedAt = before.completedAt,
    dueDate = before.dueDate,
    updatedAt = now,
)
