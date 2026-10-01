package com.moltrax.personalnoteapp.domain.model

/**
 * A recurring task's repetition format. NULL (absence of the field) preserves the legacy
 * "day interval" behavior: when [Task.isRecurring] is true and [recurrenceType] is NULL or
 * [INTERVAL], [Task.intervalDays] is used.
 *
 *  - [DAILY]   : every day
 *  - [WEEKLY]  : specific days of the week ([Task.recurrenceDaysOfWeek])
 *  - [MONTHLY] : once a month (same day)
 *  - [INTERVAL]: every N days ([Task.intervalDays]) — legacy behavior
 */
enum class RecurrenceType { DAILY, WEEKLY, MONTHLY, INTERVAL }
