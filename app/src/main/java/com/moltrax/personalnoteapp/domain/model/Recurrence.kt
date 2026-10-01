package com.moltrax.personalnoteapp.domain.model

/**
 * Tekrarlayan görevin yineleme biçimi. NULL (alanın yokluğu) eski "gün aralığı" davranışını korur:
 * [Task.isRecurring] true iken [recurrenceType] NULL veya [INTERVAL] ise [Task.intervalDays] kullanılır.
 *
 *  - [DAILY]   : her gün
 *  - [WEEKLY]  : haftanın belirli günleri ([Task.recurrenceDaysOfWeek])
 *  - [MONTHLY] : ayda bir (aynı gün)
 *  - [INTERVAL]: her N günde bir ([Task.intervalDays]) — eski davranış
 */
enum class RecurrenceType { DAILY, WEEKLY, MONTHLY, INTERVAL }
