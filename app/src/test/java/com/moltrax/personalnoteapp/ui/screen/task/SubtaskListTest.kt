package com.moltrax.personalnoteapp.ui.screen.task

import com.moltrax.personalnoteapp.domain.model.SubTask
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Subtask delete-confirm list op (review-ui #17: the screen confirms, then
 * [TaskDetailViewModel.removeSubtask] drops the id via [withoutSubtask]).
 */
class SubtaskListTest {

    @Test
    fun `withoutSubtask removes only the matching id and keeps order`() {
        val a = SubTask(id = "a", title = "A")
        val b = SubTask(id = "b", title = "B")
        val c = SubTask(id = "c", title = "C")
        assertEquals(listOf(a, c), listOf(a, b, c).withoutSubtask("b"))
    }

    @Test
    fun `withoutSubtask is a no-op for unknown ids`() {
        val list = listOf(SubTask(id = "a", title = "A"))
        assertEquals(list, list.withoutSubtask("zzz"))
        assertEquals(emptyList<SubTask>(), emptyList<SubTask>().withoutSubtask("a"))
    }
}
