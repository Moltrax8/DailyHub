package com.moltrax.personalnoteapp.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class WidgetUndoStateTest {
    @Test fun clearsBothUndoKeysWithoutChangingOtherWidgetState() {
        val prefs = mutablePreferencesOf(
            TaskWidget.UNDO_TASK_TITLE to "Before",
            TaskWidget.UNDO_TASK_JSON to "snapshot",
            TaskWidget.EXPANDED_TASK_IDS to setOf("expanded"),
            TaskWidget.SELECTED_TASK_IDS to setOf("selected"),
            WidgetFilter.TITLE to "Work",
        )
        prefs.clearTaskUndo()
        assertNull(prefs[TaskWidget.UNDO_TASK_TITLE])
        assertNull(prefs[TaskWidget.UNDO_TASK_JSON])
        assertEquals(setOf("expanded"), prefs[TaskWidget.EXPANDED_TASK_IDS])
        assertEquals(setOf("selected"), prefs[TaskWidget.SELECTED_TASK_IDS])
        assertEquals("Work", prefs[WidgetFilter.TITLE])
    }

    @Test fun clearingOneInstanceLeavesOtherInstancesUntouched() {
        val first = mutablePreferencesOf(TaskWidget.UNDO_TASK_TITLE to "First", TaskWidget.UNDO_TASK_JSON to "one")
        val second = mutablePreferencesOf(TaskWidget.UNDO_TASK_TITLE to "Second", TaskWidget.UNDO_TASK_JSON to "two")
        first.clearTaskUndo()
        assertEquals("Second", second[TaskWidget.UNDO_TASK_TITLE])
        assertEquals("two", second[TaskWidget.UNDO_TASK_JSON])
    }

    @Test fun absentOrPartiallyPresentUndoKeysCanBeClearedRepeatedly() {
        val prefs = mutablePreferencesOf(TaskWidget.UNDO_TASK_JSON to "snapshot")
        prefs.clearTaskUndo()
        prefs.clearTaskUndo()
        assertTrue(prefs.asMap().isEmpty())
    }
}
