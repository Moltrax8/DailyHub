package com.moltrax.personalnoteapp.domain.util

import java.time.LocalDate
import java.time.Period

/**
 * Pure (side-effect-free) helpers for birth dates.
 * Shared between UI and ViewModel layers; uses LocalDate (minSdk 26 → java.time available).
 */
object BirthdayUtils {

    /** ISO format stored in DataStore: "yyyy-MM-dd". */
    fun parse(iso: String?): LocalDate? =
        iso?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** LocalDate → DataStore format ("yyyy-MM-dd"). */
    fun format(date: LocalDate): String = date.toString()

    /** Returns the number of completed years on [today] for the given birth date. */
    fun calculateAge(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
        Period.between(birthDate, today).years

    /**
     * Is [today] the user's birthday? Only day and month are compared.
     * For Feb 29 birthdays, Feb 28 counts as the birthday in non-leap years.
     */
    fun isBirthday(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        if (birthDate.monthValue == today.monthValue && birthDate.dayOfMonth == today.dayOfMonth) return true
        // Feb 29 → celebrate on Feb 28 in non-leap years
        val isLeapBirthday = birthDate.monthValue == 2 && birthDate.dayOfMonth == 29
        return isLeapBirthday && !today.isLeapYear && today.monthValue == 2 && today.dayOfMonth == 28
    }
}
