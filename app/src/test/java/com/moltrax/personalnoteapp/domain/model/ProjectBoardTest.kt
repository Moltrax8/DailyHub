package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Board grouping (Phase 6): columns keep their sort order.
 */
class ProjectBoardTest {

    private fun item(id: String, status: ProjectStatus, sort: Long) = ProjectItem(
        id = id, spaceId = "s", title = id, status = status, sortOrder = sort,
    )

    @Test
    fun `items land in their columns sorted`() {
        val grouped = groupBoardItems(
            listOf(
                item("b", ProjectStatus.PLANNED, 1L),
                item("a", ProjectStatus.IDEA, 0L),
                item("c", ProjectStatus.IDEA, 2L),
            )
        )
        assertEquals(listOf("a", "c"), grouped["Idea"]!!.map { it.id })
        assertEquals(listOf("b"), grouped["Planned"]!!.map { it.id })
        assertEquals(0, grouped["Finished"]!!.size)
    }

    @Test
    fun `unknown raw status falls back to idea`() {
        assertEquals(ProjectStatus.IDEA, projectStatusOf("Backlog"))
        assertEquals(ProjectStatus.IDEA, projectStatusOf(null))
        assertEquals(ProjectStatus.FINISHED, projectStatusOf("FINISHED"))
    }
}
