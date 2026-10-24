package com.moltrax.personalnoteapp.widget

import androidx.datastore.preferences.core.MutablePreferences

/** Clears only the undo strip; the caller owns the widget-state transaction and refresh. */
internal fun MutablePreferences.clearTaskUndo() {
    remove(TaskWidget.UNDO_TASK_TITLE)
    remove(TaskWidget.UNDO_TASK_JSON)
}
