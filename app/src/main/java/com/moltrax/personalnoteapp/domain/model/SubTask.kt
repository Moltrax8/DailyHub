package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A single checklist item under a parent task (Quest). Stored as an embedded JSON list with
 * the task (no separate table); so it travels with the task during Drive sync and the
 * existing LWW merge keeps working as-is.
 */
@Serializable
data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false,
)
