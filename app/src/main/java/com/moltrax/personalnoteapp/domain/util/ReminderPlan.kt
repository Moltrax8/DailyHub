package com.moltrax.personalnoteapp.domain.util

/**
 * Pure reminder-planning math — no Android dependencies, JVM-testable.
 *
 * Single source of truth for WHEN a task reminder fires:
 * [NotificationService] only handles the AlarmManager mechanics and
 * [NotificationScheduler] the stage/cancel policy; both follow this file.
 */
data class ReminderPlan(
    /** Epoch-ms when the alarm must fire. */
    val fireAt: Long,
    /** Whole minutes between [fireAt] and the deadline (0 = fires at the deadline). */
    val minutesLeftAtFire: Int,
)

/**
 * Computes when a reminder alarm must fire.
 * Mirrors the historical [NotificationService.scheduleReminder] behavior:
 * fire `reminderMinutes` before the deadline; when the lead window already
 * passed, fire at the deadline itself (with 0 minutes left, never a lie).
 * @return null when no alarm must be staged (no deadline or deadline passed).
 */
fun planReminder(dueDate: Long?, now: Long, reminderMinutes: Int): ReminderPlan? {
    if (dueDate == null || dueDate <= now) return null
    val triggerAt = dueDate - reminderMinutes * 60_000L
    val fireAt = if (triggerAt > now) triggerAt else dueDate
    if (fireAt <= now) return null
    return ReminderPlan(fireAt, ((dueDate - fireAt) / 60_000L).toInt())
}

/**
 * Whether an alarm may exist for the task at all: alerts on, task alive,
 * open, and carrying a deadline. Time-window validity is [planReminder]'s job.
 */
fun shouldStageReminder(
    isDeleted: Boolean,
    isDone: Boolean,
    dueDate: Long?,
    alertsEnabled: Boolean,
): Boolean = alertsEnabled && !isDeleted && !isDone && dueDate != null
