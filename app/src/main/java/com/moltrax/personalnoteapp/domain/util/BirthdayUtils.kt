package com.moltrax.personalnoteapp.domain.util

import java.time.LocalDate
import java.time.Period

/**
 * Doğum tarihi ile ilgili saf (yan etkisiz) yardımcı fonksiyonlar.
 * UI ve ViewModel katmanları arasında paylaşılır; LocalDate kullanır (minSdk 26 → java.time mevcut).
 */
object BirthdayUtils {

    /** DataStore'da saklanan ISO biçimi: "yyyy-MM-dd". */
    fun parse(iso: String?): LocalDate? =
        iso?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** LocalDate → DataStore biçimi ("yyyy-MM-dd"). */
    fun format(date: LocalDate): String = date.toString()

    /** Verilen doğum tarihine göre [today] gününde tamamlanmış yıl sayısını döndürür. */
    fun calculateAge(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
        Period.between(birthDate, today).years

    /**
     * [today] kullanıcının doğum günü mü? Yalnızca gün ve ay karşılaştırılır.
     * 29 Şubat doğumlular için artık olmayan yıllarda 28 Şubat doğum günü sayılır.
     */
    fun isBirthday(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        if (birthDate.monthValue == today.monthValue && birthDate.dayOfMonth == today.dayOfMonth) return true
        // 29 Şubat → artık olmayan yılda 28 Şubat'ta kutla
        val isLeapBirthday = birthDate.monthValue == 2 && birthDate.dayOfMonth == 29
        return isLeapBirthday && !today.isLeapYear && today.monthValue == 2 && today.dayOfMonth == 28
    }
}
