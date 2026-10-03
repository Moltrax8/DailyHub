package com.moltrax.personalnoteapp.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [planReminder] / [shouldStageReminder] behavior tests — the single source of
 * truth for reminder timing (task due 18:00 → notify 17:00).
 */
class ReminderPlanTest {

    private val hourMs = 60L * 60 * 1000

    @Test
    fun `due date null means no alarm`() {
        assertNull(planReminder(null, 1_000L, 60))
    }

    @Test
    fun `past deadline means no alarm`() {
        assertNull(planReminder(1_000L, 2_000L, 60))
        assertNull(planReminder(2_000L, 2_000L, 60))
    }

    @Test
    fun `normal case fires lead minutes before deadline`() {
        val now = 0L
        val due = 5 * hourMs
        val plan = planReminder(due, now, 60)

        assertNotNull(plan)
        assertEquals(due - hourMs, plan!!.fireAt)
        assertEquals(60, plan.minutesLeftAtFire)
    }

    @Test
    fun `passed lead window fires at deadline with zero minutes left`() {
        val now = 4 * hourMs + 30 * 60_000L
        val due = 5 * hourMs
        val plan = planReminder(due, now, 60)

        assertNotNull(plan)
        assertEquals(due, plan!!.fireAt)
        assertEquals(0, plan.minutesLeftAtFire)
    }

    @Test
    fun `lead exactly at now fires at deadline`() {
        val now = 4 * hourMs
        val due = 5 * hourMs
        val plan = planReminder(due, now, 60)

        assertNotNull(plan)
        assertEquals(due, plan!!.fireAt)
    }

    @Test
    fun `deleted task never stages`() {
        assertFalse(shouldStageReminder(isDeleted = true, isDone = false, dueDate = 9_999L, alertsEnabled = true))
    }

    @Test
    fun `done task never stages`() {
        assertFalse(shouldStageReminder(isDeleted = false, isDone = true, dueDate = 9_999L, alertsEnabled = true))
    }

    @Test
    fun `dateless task never stages`() {
        assertFalse(shouldStageReminder(isDeleted = false, isDone = false, dueDate = null, alertsEnabled = true))
    }

    @Test
    fun `alerts off never stages`() {
        assertFalse(shouldStageReminder(isDeleted = false, isDone = false, dueDate = 9_999L, alertsEnabled = false))
    }

    @Test
    fun `open dated task with alerts on stages`() {
        assertTrue(shouldStageReminder(isDeleted = false, isDone = false, dueDate = 9_999L, alertsEnabled = true))
    }
}
