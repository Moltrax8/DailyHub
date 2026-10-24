package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.*
import org.junit.Test

class SubTaskToggleTest {
    private val checklist = listOf(
        SubTask("a", "First"), SubTask("b", "Second", true), SubTask("c", "Third"),
    )

    @Test fun togglesOnlyMatchingItemsAndPreservesOrderTitlesAndOriginalList() {
        val result = checklist.toggledById("b")
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(listOf("First", "Second", "Third"), result.map { it.title })
        assertFalse(result[1].isDone)
        assertTrue(checklist[1].isDone)
        assertSame(checklist[0], result[0])
        assertSame(checklist[2], result[2])
    }

    @Test fun togglingTwiceRestoresTheChecklist() {
        assertEquals(checklist, checklist.toggledById("a").toggledById("a"))
    }

    @Test fun unknownIdsAndEmptyChecklistsKeepTheirValues() {
        assertEquals(checklist, checklist.toggledById("missing"))
        assertTrue(emptyList<SubTask>().toggledById("missing").isEmpty())
    }

    @Test fun duplicateIdsRetainTheExistingToggleEveryMatchBehavior() {
        val duplicateIds = listOf(SubTask("a", "Open"), SubTask("a", "Done", true))
        assertEquals(listOf(true, false), duplicateIds.toggledById("a").map { it.isDone })
    }
}
