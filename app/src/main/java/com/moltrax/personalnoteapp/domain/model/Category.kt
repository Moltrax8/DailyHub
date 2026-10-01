package com.moltrax.personalnoteapp.domain.model

/**
 * Task category. [name] is also the primary key (tasks reference the category by name —
 * see [Task.category]).
 *
 * - [isPermanent] = true: user-defined permanent category. Always visible in the filter menu
 *   even with no tasks inside, and never caught by automatic cleanup.
 * - [isPermanent] = false: temporary category. Created automatically when a category is written
 *   to a task; deleted automatically once no linked tasks remain.
 */
data class Category(
    val name: String,
    val isPermanent: Boolean = false,
    // Tombstone: the deleted category record is kept so Drive sync does not resurrect the
    // deleted category from remote. Visible lists are filtered at the DAO layer.
    val isDeleted: Boolean = false,
)
