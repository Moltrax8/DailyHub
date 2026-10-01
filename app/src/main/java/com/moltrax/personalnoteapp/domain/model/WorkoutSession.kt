package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val loggedExercises: List<LoggedExercise> = emptyList(),
    // Bu seansı üreten spor görevinin id'si (varsa). Tamamlanan görevden özet/sonuç sayfasını
    // bulmak için kullanılır. Canlı antrenmandan (göreve bağlı olmayan) başlatılan seanslarda null.
    val taskId: String? = null,
    // Mezar taşı: silinen seans kaydı saklanır ki Drive senkronizasyonu silinmiş seansı
    // uzaktan geri diriltmesin. Görünür listeler DAO katmanında filtrelenir.
    val isDeleted: Boolean = false,
)

@Serializable
data class LoggedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSet> = emptyList(),
    /** Hareketin tipi — EXP algoritması ağırlık/kardiyo dalını buna göre seçer. */
    val type: ExerciseType = ExerciseType.WEIGHTLIFTING,
    /** Bilinmeyen (gelecek sürümdeki) tip adlarının ham karşılığı; yazma yolunda korunur. */
    val typeRaw: String? = null,
)

/**
 * Gerçekleşen (kaydedilen) set. Ağırlıkta [reps]/[weightKg]; kardiyoda [durationSeconds] +
 * [steps]/[distanceMeters] doldurulur.
 */
@Serializable
data class LoggedSet(
    val reps: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
    val completedAt: Long = System.currentTimeMillis(),
) {
    /** En az bir anlamlı ölçü taşıyor mu? Tamamen boş setleri kayıttan ayıklamak için kullanılır. */
    fun isMeaningful(): Boolean =
        reps > 0 || (weightKg ?: 0.0) > 0.0 || (durationSeconds ?: 0) > 0 ||
            (steps ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0
}
