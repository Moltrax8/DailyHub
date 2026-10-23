package com.moltrax.personalnoteapp.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

enum class WidgetSort { MANUAL, DUE_DATE, TITLE }
enum class WidgetTheme { DARK, LIGHT, SYSTEM }

/**
 * Per-widget-instance filter (Phase 2). Stored in the instance's Glance state
 * ([PreferencesGlanceStateDefinition]), so multiple widgets show different slices.
 * Empty [categoryNames] = all tags (untagged tasks included).
 */
data class WidgetFilter(
    val title: String = "",
    val categoryNames: Set<String> = emptySet(),
    val showDone: Boolean = false,
    val limit: Int = DEFAULT_LIMIT,
    val matchAll: Boolean = false,
    /** Blank keeps the existing list/tag filter; otherwise pin this task. */
    val taskId: String = "",
    val showNotes: Boolean = true,
    val expandSubtasks: Boolean = false,
    val showDoneSubtasks: Boolean = true,
    val compact: Boolean = false,
    val sort: WidgetSort = WidgetSort.MANUAL,
    val theme: WidgetTheme = WidgetTheme.DARK,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50

        val TITLE = stringPreferencesKey("widget_filter_title")
        val TAGS = stringSetPreferencesKey("widget_filter_tags")
        val SHOW_DONE = booleanPreferencesKey("widget_filter_show_done")
        val LIMIT = intPreferencesKey("widget_filter_limit")
        val MATCH_ALL = booleanPreferencesKey("widget_filter_match_all")
        val TASK_ID = stringPreferencesKey("widget_filter_task_id")
        val SHOW_NOTES = booleanPreferencesKey("widget_show_notes")
        val EXPAND_SUBTASKS = booleanPreferencesKey("widget_expand_subtasks")
        val SHOW_DONE_SUBTASKS = booleanPreferencesKey("widget_show_done_subtasks")
        val COMPACT = booleanPreferencesKey("widget_compact")
        val SORT = stringPreferencesKey("widget_sort")
        val THEME = stringPreferencesKey("widget_theme")

        fun load(prefs: Preferences): WidgetFilter = WidgetFilter(
            title = prefs[TITLE].orEmpty(),
            categoryNames = prefs[TAGS].orEmpty(),
            showDone = prefs[SHOW_DONE] ?: false,
            limit = (prefs[LIMIT] ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT),
            matchAll = prefs[MATCH_ALL] ?: false,
            taskId = prefs[TASK_ID].orEmpty(),
            showNotes = prefs[SHOW_NOTES] ?: true,
            expandSubtasks = prefs[EXPAND_SUBTASKS] ?: false,
            showDoneSubtasks = prefs[SHOW_DONE_SUBTASKS] ?: true,
            compact = prefs[COMPACT] ?: false,
            sort = WidgetSort.entries.firstOrNull { it.name == prefs[SORT] } ?: WidgetSort.MANUAL,
            theme = WidgetTheme.entries.firstOrNull { it.name == prefs[THEME] } ?: WidgetTheme.DARK,
        )

        fun save(mutable: MutablePreferences, filter: WidgetFilter) {
            if (filter.title.isBlank()) mutable.remove(TITLE) else mutable[TITLE] = filter.title.trim().take(80)
            if (filter.categoryNames.isEmpty()) mutable.remove(TAGS) else mutable[TAGS] = filter.categoryNames
            mutable[SHOW_DONE] = filter.showDone
            mutable[LIMIT] = filter.limit.coerceIn(1, MAX_LIMIT)
            mutable[MATCH_ALL] = filter.matchAll
            if (filter.taskId.isBlank()) mutable.remove(TASK_ID) else mutable[TASK_ID] = filter.taskId
            mutable[SHOW_NOTES] = filter.showNotes
            mutable[EXPAND_SUBTASKS] = filter.expandSubtasks
            mutable[SHOW_DONE_SUBTASKS] = filter.showDoneSubtasks
            mutable[COMPACT] = filter.compact
            mutable[SORT] = filter.sort.name
            mutable[THEME] = filter.theme.name
        }
    }

    fun select(tasks: List<com.moltrax.personalnoteapp.domain.model.Task>): List<com.moltrax.personalnoteapp.domain.model.Task> {
        // Pinned tasks stay visible when completed so they can be unchecked again.
        if (taskId.isNotBlank()) return tasks.filter { it.id == taskId }
        return tasks.filter { task ->
            (showDone || !task.isDone) && (categoryNames.isEmpty() ||
                categoryNames.map { name -> task.categoryNames.any { it.equals(name, ignoreCase = true) } }
                    .let { hits -> if (matchAll) hits.all { it } else hits.any { it } })
        }.let { filtered ->
            when (sort) {
                WidgetSort.MANUAL -> if (categoryNames.size == 1) filtered.sortedBy { task ->
                    task.categoryOrders.entries.firstOrNull { it.key.equals(categoryNames.single(), ignoreCase = true) }?.value ?: task.sortOrder
                } else filtered.sortedBy { it.sortOrder }
                WidgetSort.DUE_DATE -> filtered.sortedWith(compareBy<com.moltrax.personalnoteapp.domain.model.Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.sortOrder })
                WidgetSort.TITLE -> filtered.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
            }
        }.take(limit.coerceIn(1, MAX_LIMIT))
    }
}
