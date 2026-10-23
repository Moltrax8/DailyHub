package com.moltrax.personalnoteapp.ui.screen.focus

import com.moltrax.personalnoteapp.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FocusState] guards — the Start button is only enabled after a real task
 * finishes loading (review-ui #7: no Start on missing tasks).
 */
class FocusStateTest {

    @Test
    fun `fresh state is loading and cannot start`() {
        val s = FocusState()
        assertTrue(s.isLoading)
        assertFalse(s.canStart)
        assertNull(s.task)
    }

    @Test
    fun `start enabled only after a task loads`() {
        val loading = FocusState(task = Task(title = "T"), isLoading = true)
        assertFalse(loading.canStart)
        assertTrue(loading.copy(isLoading = false).canStart)
    }

    @Test
    fun `missing task never enables start`() {
        assertFalse(FocusState(task = null, isLoading = false).canStart)
    }

    @Test
    fun `progress minutes and seconds math`() {
        val full = FocusState(totalSeconds = 1500, remainingSeconds = 1500)
        assertEquals(0f, full.progress)
        assertEquals(25, full.minutesLeft)
        assertEquals(0, full.secondsLeft)

        val half = full.copy(remainingSeconds = 750)
        assertEquals(0.5f, half.progress)
        assertEquals(12, half.minutesLeft)
        assertEquals(30, half.secondsLeft)

        val zero = full.copy(totalSeconds = 0, remainingSeconds = 0)
        assertEquals(0f, zero.progress)
    }
}
