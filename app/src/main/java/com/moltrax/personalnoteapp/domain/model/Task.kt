package com.moltrax.personalnoteapp.domain.model

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val isDone: Boolean = false,
    val isRecurring: Boolean = false,
    val intervalDays: Int? = null,
    // Zengin tekrar biçimi. NULL = eski "gün aralığı" davranışı (intervalDays kullanılır).
    val recurrenceType: RecurrenceType? = null,
    // WEEKLY için seçili haftanın günleri (ISO: 1=Pazartesi .. 7=Pazar). Boşsa görevin kendi
    // gününe göre haftalık (her 7 günde bir) yinelenir.
    val recurrenceDaysOfWeek: List<Int> = emptyList(),
    val focusDurationSeconds: Int = 1500,
    val category: String? = null,
    // Ana görevin altındaki kontrol-listesi (checklist) maddeleri. Görevle birlikte gömülü saklanır.
    val subtasks: List<SubTask> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    // Tek bir antrenmana (Day A vb.) bağ. linkedProgramId ile aynı anda kullanılmaz.
    val linkedWorkoutId: String? = null,
    // Tüm bir antrenman programına (WorkoutGroup) bağ; döngü hangi günden başlayacağını
    // [programStartIndex] belirler. Bağlıysa görev tamamlanırken o günkü antrenman çözümlenir.
    val linkedProgramId: String? = null,
    val programStartIndex: Int = 0,
    // Manuel sıralama anahtarı: liste sortOrder ARTAN sırada gösterilir (küçük = üstte).
    // Migration eski görevlere -createdAt atar (en yeni üstte kalır). Yeni görevler için
    // mevcut en küçük değerden bir eksiği verilerek listenin başına eklenir.
    val sortOrder: Long = 0L,
    // Mezar taşı: silinen görev kaydı saklanır ki Drive senkronizasyonu silinmiş görevi
    // uzaktan geri diriltmesin. Görünür listeler DAO katmanında filtrelenir (isDeleted = 0).
    val isDeleted: Boolean = false,
    // Bilinmeyen (gelecek sürümdeki) priority adlarının ham karşılığı. null ise [priority]
    // geçerlidir; doluysa okuma/yazma yolunda aynen korunur (ileriye uyumluluk).
    val priorityRaw: String? = null,
) {
    /** Tamamlanan alt görev sayısı. */
    val doneSubtaskCount: Int get() = subtasks.count { it.isDone }

    /** Toplam alt görev sayısı. */
    val subtaskCount: Int get() = subtasks.size

    /** Tamamlanması beklenen alt görev kaldı mı (en az bir alt görev var ve hepsi bitmemiş). */
    val hasIncompleteSubtasks: Boolean get() = subtasks.any { !it.isDone }

    /** 0f..1f arası alt görev ilerlemesi (alt görev yoksa 0). */
    val subtaskProgress: Float
        get() = if (subtasks.isEmpty()) 0f else doneSubtaskCount.toFloat() / subtasks.size
}

/**
 * Tekrarlayan bir görev tamamlandığında taşınacağı BİR SONRAKİ bitiş zamanını hesaplar; görev
 * tekrarlamıyorsa veya geçerli bir biçim yoksa null döner. Daima en az bir döngü ileri gider ve
 * gerekirse [now]'ı geçene kadar ilerler (gecikmiş görevlerde zinciri tazeler). [withCompletion]
 * aynı mantığı paylaşır.
 */
fun Task.nextRecurrenceDue(now: Long = System.currentTimeMillis()): Long? {
    if (!isRecurring) return null
    val base = dueDate ?: now
    return when (recurrenceType) {
        RecurrenceType.DAILY -> advance(base, now) { it.plusDays(1) }
        RecurrenceType.MONTHLY -> advance(base, now) { it.plusMonths(1) }
        RecurrenceType.WEEKLY -> nextWeekly(base, now)
        RecurrenceType.INTERVAL, null -> {
            val step = intervalDays ?: 0
            if (step <= 0) return null
            // Takvim günü olarak ilerlet (sabit 24s katı DEĞİL): DST geçişlerinde bitiş saati
            // kaymaz; DAILY/MONTHLY/WEEKLY ile aynı LocalDateTime tabanlı davranışı paylaşır.
            advance(base, now) { it.plusDays(step.toLong()) }
        }
    }
}

/** Verilen [step] (gün/ay ekleme) ile en az bir kez, ardından [now]'ı geçene kadar ilerler. */
private inline fun advance(base: Long, now: Long, step: (java.time.LocalDateTime) -> java.time.LocalDateTime): Long {
    val zone = ZoneId.systemDefault()
    var dt = Instant.ofEpochMilli(base).atZone(zone).toLocalDateTime()
    do { dt = step(dt) } while (dt.atZone(zone).toInstant().toEpochMilli() <= now)
    return dt.atZone(zone).toInstant().toEpochMilli()
}

/** Haftalık yinelemede [recurrenceDaysOfWeek] (boşsa görevin kendi günü) için sıradaki gün. */
private fun Task.nextWeekly(base: Long, now: Long): Long {
    val zone = ZoneId.systemDefault()
    val baseDt = Instant.ofEpochMilli(base).atZone(zone).toLocalDateTime()
    val days = recurrenceDaysOfWeek.takeIf { it.isNotEmpty() }?.toSet()
        ?: setOf(baseDt.dayOfWeek.value)
    var dt = baseDt.plusDays(1) // bugünkü tamamlamadan sonra en erken yarın
    while (dt.dayOfWeek.value !in days || dt.atZone(zone).toInstant().toEpochMilli() <= now) {
        dt = dt.plusDays(1)
    }
    return dt.atZone(zone).toInstant().toEpochMilli()
}

/**
 * Bir görevi "tamamlandı" olarak işaretler. Tekrarlayan görevlerde (isRecurring + geçerli yineleme)
 * görev kapatılmaz; bunun yerine bir sonraki döngüye taşınır ve açık kalır. Böylece tekrarlayan
 * görevler gerçekten yinelenir. Hem ana liste hem odak zamanlayıcısı kullanır.
 */
fun Task.withCompletion(now: Long = System.currentTimeMillis()): Task {
    val next = nextRecurrenceDue(now)
    return if (next != null) {
        copy(dueDate = next, isDone = false, completedAt = null, updatedAt = now)
    } else {
        copy(isDone = true, completedAt = now, updatedAt = now)
    }
}
