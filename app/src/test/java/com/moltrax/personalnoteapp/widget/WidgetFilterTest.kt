package com.moltrax.personalnoteapp.widget

import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.SubTask
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import androidx.datastore.preferences.core.mutablePreferencesOf

class WidgetFilterTest {
    private val tasks = listOf(
        Task(id = "a", title = "A", categoryNames = setOf("Work", "Home")),
        Task(id = "b", title = "B", categoryNames = setOf("Work")),
        Task(id = "c", title = "C", isDone = true),
    )

    @Test fun defaultShowsOpenTasks() {
        assertEquals(listOf("a", "b"), WidgetFilter().select(tasks).map { it.id })
    }
    @Test fun pinnedTaskIgnoresListFiltersAndStaysAfterCompletion() {
        assertEquals(listOf(tasks[2]), WidgetFilter(taskId = "c", categoryNames = setOf("missing")).select(tasks))
        assertTrue(WidgetFilter(taskId = "deleted").select(tasks).isEmpty())
    }
    @Test fun anyAllTagsAreCaseInsensitiveAndLimitApplies() {
        assertEquals(listOf(tasks[0]), WidgetFilter(categoryNames = setOf("work", "home"), matchAll = true).select(tasks))
        assertEquals(2, WidgetFilter(categoryNames = setOf("work", "missing")).select(tasks).size)
        assertEquals(1, WidgetFilter(limit = 1).select(tasks).size)
        assertEquals(3, WidgetFilter(showDone = true).select(tasks).size)
    }
    @Test fun localUndoSnapshotRoundTripsAllTaskContent() {
        val task = tasks[0].copy(notes = "notes", dueDate = 123L, sortOrder = -9,
            subtasks = listOf(SubTask(title = "one", isDone = true), SubTask(title = "two")),
            categoryOrders = mapOf("Work" to 9L))
        val snapshot = Json.decodeFromString<WidgetUndoSnapshot>(TaskWidget.encodeUndo(task))
        assertEquals(task, snapshot.task)
        assertEquals(1, snapshot.task.subtaskCount - snapshot.task.doneSubtaskCount)
    }
    @Test fun customizationRoundTripsAndWidgetsRemainIndependent() {
        val first = mutablePreferencesOf()
        val second = mutablePreferencesOf()
        val config = WidgetFilter(title = "Work", showNotes = false, compact = true, expandSubtasks = true,
            showDoneSubtasks = false, theme = WidgetTheme.LIGHT, sort = WidgetSort.DUE_DATE, limit = 3)
        WidgetFilter.save(first, config)
        assertEquals(config, WidgetFilter.load(first))
        assertEquals(WidgetFilter(), WidgetFilter.load(second))
    }
    @Test fun invalidStoredOptionsFallBackAndLimitsClamp() {
        val prefs = mutablePreferencesOf(WidgetFilter.SORT to "future", WidgetFilter.THEME to "future", WidgetFilter.LIMIT to 999)
        assertEquals(WidgetSort.MANUAL, WidgetFilter.load(prefs).sort)
        assertEquals(WidgetTheme.DARK, WidgetFilter.load(prefs).theme)
        assertEquals(WidgetFilter.MAX_LIMIT, WidgetFilter.load(prefs).limit)
    }
    @Test fun dueDateAndTitleSortingRunBeforeLimit() {
        val list = listOf(Task(id = "z", title = "Zulu"), Task(id = "a", title = "alpha", dueDate = 20),
            Task(id = "b", title = "Beta", dueDate = 10))
        assertEquals(listOf("b", "a"), WidgetFilter(sort = WidgetSort.DUE_DATE, limit = 2).select(list).map { it.id })
        assertEquals(listOf("a", "b"), WidgetFilter(sort = WidgetSort.TITLE, limit = 2).select(list).map { it.id })
    }
    @Test fun singleTagUsesItsOwnManualOrder() {
        val list = listOf(tasks[0].copy(sortOrder = 0, categoryOrders = mapOf("Work" to 9)),
            tasks[1].copy(sortOrder = 1, categoryOrders = mapOf("Work" to 2)))
        assertEquals(listOf("b", "a"), WidgetFilter(categoryNames = setOf("work")).select(list).map { it.id })
    }
}
