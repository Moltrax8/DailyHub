package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WorkoutGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val workouts: List<Workout> = emptyList(),
    val currentIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    // Senkronizasyon çakışma çözümü (LWW) için son değişiklik zamanı. Grup eklendiğinde,
    // düzenlendiğinde, içine antrenman eklenip silindiğinde veya grup silindiğinde güncellenir.
    val updatedAt: Long = System.currentTimeMillis(),
    // Mezar taşı: grup silindiğinde true olur (kayıt saklanır). Böylece Drive senkronizasyonu
    // silinen grubu/antrenmanı uzaktan geri DİRİLTMEZ. Görünür listelerde gizlenir.
    val isDeleted: Boolean = false,
)

@Serializable
data class Workout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exercises: List<WorkoutExercise> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class WorkoutExercise(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val exerciseName: String,
    val plannedSets: List<PlannedSet> = emptyList(),
    val orderIndex: Int = 0,
    /** Hareketin giriş tipi (ağırlık/kardiyo); canlı ekrandaki alanları belirler. */
    val type: ExerciseType = ExerciseType.WEIGHTLIFTING,
    /** Bilinmeyen (gelecek sürümdeki) tip adlarının ham karşılığı; yazma yolunda korunur. */
    val typeRaw: String? = null,
)

/**
 * Planlanan (hedef) set. Ağırlık hareketlerinde [reps]/[weightKg]; kardiyo hareketlerinde
 * [durationSeconds] + [steps]/[distanceMeters] anlamlıdır.
 */
@Serializable
data class PlannedSet(
    val reps: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
)

data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String? = null,
    val isUnilateral: Boolean = false,
    val description: String? = null,
    val mediaUrl: String? = null,
    val localMediaPath: String? = null,
)
