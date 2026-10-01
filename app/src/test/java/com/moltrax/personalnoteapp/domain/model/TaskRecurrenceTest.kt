package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [Task.withCompletion] davranış testleri — tekrarlayan görevlerin gerçekten yinelendiğini doğrular.
 */
class TaskRecurrenceTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun task(
        isRecurring: Boolean = false,
        intervalDays: Int? = null,
        dueDate: Long? = null,
    ) = Task(
        title = "Test",
        dueDate = dueDate,
        isRecurring = isRecurring,
        intervalDays = intervalDays,
    )

    @Test
    fun `normal gorev tamamlaninca kapanir`() {
        val now = 1_000_000L
        val result = task().withCompletion(now)

        assertTrue(result.isDone)
        assertEquals(now, result.completedAt)
        assertEquals(now, result.updatedAt)
    }

    @Test
    fun `tekrarlayan gorev tamamlaninca bir sonraki tarihe tasinir ve acik kalir`() {
        val now = 5_000_000L
        val due = 5_000_000L
        val result = task(isRecurring = true, intervalDays = 3, dueDate = due).withCompletion(now)

        assertFalse("Tekrarlayan görev kapanmamalı", result.isDone)
        assertNull(result.completedAt)
        assertEquals(due + 3 * dayMs, result.dueDate)
        assertEquals(now, result.updatedAt)
    }

    @Test
    fun `tekrarlayan ama intervalDays null ise normal kapanir`() {
        val now = 2_000_000L
        val result = task(isRecurring = true, intervalDays = null, dueDate = 2_000_000L).withCompletion(now)

        assertTrue(result.isDone)
        assertEquals(now, result.completedAt)
    }

    @Test
    fun `intervalDays sifir ise normal kapanir`() {
        val result = task(isRecurring = true, intervalDays = 0, dueDate = 1L).withCompletion(10L)
        assertTrue(result.isDone)
    }

    @Test
    fun `dueDate null ise simdiki zamandan ileri sarilir`() {
        val now = 9_000_000L
        val result = task(isRecurring = true, intervalDays = 7, dueDate = null).withCompletion(now)

        assertFalse(result.isDone)
        assertEquals(now + 7 * dayMs, result.dueDate)
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun millis(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `gunluk tekrar ertesi gune tasinir`() {
        val due = LocalDateTime.of(2026, 6, 25, 9, 0)
        val result = Task(
            title = "Daily", dueDate = millis(due),
            isRecurring = true, recurrenceType = RecurrenceType.DAILY,
        ).withCompletion(millis(due))

        assertFalse(result.isDone)
        assertEquals(millis(due.plusDays(1)), result.dueDate)
    }

    @Test
    fun `aylik tekrar gelecek aya tasinir`() {
        val due = LocalDateTime.of(2026, 6, 25, 9, 0)
        val result = Task(
            title = "Monthly", dueDate = millis(due),
            isRecurring = true, recurrenceType = RecurrenceType.MONTHLY,
        ).withCompletion(millis(due))

        assertFalse(result.isDone)
        assertEquals(millis(due.plusMonths(1)), result.dueDate)
    }

    @Test
    fun `haftalik tekrar secili sonraki gune tasinir`() {
        // 25 Haziran 2026 = Perşembe (ISO 4). Seçili günler: Pazartesi(1) ve Cuma(5) → sonraki Cuma.
        val due = LocalDateTime.of(2026, 6, 25, 9, 0)
        val result = Task(
            title = "Weekly", dueDate = millis(due),
            isRecurring = true, recurrenceType = RecurrenceType.WEEKLY,
            recurrenceDaysOfWeek = listOf(1, 5),
        ).withCompletion(millis(due))

        assertFalse(result.isDone)
        assertEquals(millis(LocalDateTime.of(2026, 6, 26, 9, 0)), result.dueDate) // ertesi gün Cuma
    }

    @Test
    fun `haftalik gun secilmezse bir hafta sonraya tasinir`() {
        val due = LocalDateTime.of(2026, 6, 25, 9, 0) // Perşembe
        val result = Task(
            title = "Weekly", dueDate = millis(due),
            isRecurring = true, recurrenceType = RecurrenceType.WEEKLY,
        ).withCompletion(millis(due))

        assertEquals(millis(due.plusWeeks(1)), result.dueDate)
    }
}
