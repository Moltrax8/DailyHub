package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A checklist item stored as embedded JSON in its parent task.
 */
@Serializable
data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false,
)

/** Toggles every matching id without changing order or mutating the original checklist. */
internal fun List<SubTask>.toggledById(id: String): List<SubTask> =
    map { if (it.id == id) it.copy(isDone = !it.isDone) else it }
