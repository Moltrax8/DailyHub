package com.moltrax.personalnoteapp.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [mergeCategoryTags] behavior tests — union of tags, winner's positions win.
 */
class CategoryTagsTest {

    @Test
    fun `union keeps tags added on either side`() {
        val (names, _) = mergeCategoryTags(
            winnerNames = setOf("Work"),
            winnerOrders = mapOf("Work" to 0L),
            otherNames = setOf("Home"),
            otherOrders = mapOf("Home" to 2L),
        )
        assertEquals(setOf("Work", "Home"), names)
    }

    @Test
    fun `winner positions win, missing fall back to loser`() {
        val (_, orders) = mergeCategoryTags(
            winnerNames = setOf("Work", "Home"),
            winnerOrders = mapOf("Work" to 5L),
            otherNames = setOf("Work", "Home"),
            otherOrders = mapOf("Work" to 0L, "Home" to 2L),
        )
        assertEquals(mapOf("Work" to 5L, "Home" to 2L), orders)
    }

    @Test
    fun `identical rows merge identically`() {
        val first = mergeCategoryTags(setOf("A"), mapOf("A" to 1L), setOf("A"), mapOf("A" to 1L))
        val flipped = mergeCategoryTags(setOf("A"), mapOf("A" to 1L), setOf("A"), mapOf("A" to 1L))
        assertEquals(first, flipped)
    }
}
