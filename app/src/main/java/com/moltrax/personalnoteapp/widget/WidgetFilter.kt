package com.moltrax.personalnoteapp.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

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
    /** Space (hub/project) id whose shared todos this widget shows. Blank = personal tasks. */
    val spaceId: String = "",
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50

        val TITLE = stringPreferencesKey("widget_filter_title")
        val TAGS = stringSetPreferencesKey("widget_filter_tags")
        val SHOW_DONE = booleanPreferencesKey("widget_filter_show_done")
        val LIMIT = intPreferencesKey("widget_filter_limit")
        val MATCH_ALL = booleanPreferencesKey("widget_filter_match_all")
        val SPACE_ID = stringPreferencesKey("widget_filter_space_id")

        fun load(prefs: Preferences): WidgetFilter = WidgetFilter(
            title = prefs[TITLE].orEmpty(),
            categoryNames = prefs[TAGS].orEmpty(),
            showDone = prefs[SHOW_DONE] ?: false,
            limit = (prefs[LIMIT] ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT),
            matchAll = prefs[MATCH_ALL] ?: false,
            spaceId = prefs[SPACE_ID].orEmpty(),
        )

        fun save(mutable: MutablePreferences, filter: WidgetFilter) {
            if (filter.title.isBlank()) mutable.remove(TITLE) else mutable[TITLE] = filter.title
            if (filter.categoryNames.isEmpty()) mutable.remove(TAGS) else mutable[TAGS] = filter.categoryNames
            mutable[SHOW_DONE] = filter.showDone
            mutable[LIMIT] = filter.limit.coerceIn(1, MAX_LIMIT)
            mutable[MATCH_ALL] = filter.matchAll
            if (filter.spaceId.isBlank()) mutable.remove(SPACE_ID) else mutable[SPACE_ID] = filter.spaceId
        }
    }
}
