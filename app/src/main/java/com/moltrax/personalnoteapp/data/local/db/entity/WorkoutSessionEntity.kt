package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "workout_sessions",
    indices = [Index("workoutId"), Index("taskId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val loggedExercisesJson: String,
    val taskId: String? = null,
    // Mezar taşı (v18): silinen seans saklanır, görünür sorgular filtreler, sync dahil eder.
    val isDeleted: Boolean = false,
)

@Serializable
private data class LoggedSetJson(
    val reps: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
    val completedAt: Long = 0L,
)

@Serializable
private data class LoggedExerciseJson(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSetJson>,
    val type: String = ExerciseType.WEIGHTLIFTING.name,
)

private val json = Json { ignoreUnknownKeys = true }

fun WorkoutSessionEntity.toDomain(): WorkoutSession {
    // Bozuk seans JSON'u tüm okumayı öldürmesin: çözülemezse boş egzersiz listesi.
    val exercises = runCatching { json.decodeFromString<List<LoggedExerciseJson>>(loggedExercisesJson) }
        .getOrDefault(emptyList())
        .map { ex ->
            LoggedExercise(
                exerciseId = ex.exerciseId,
                exerciseName = ex.exerciseName,
                sets = ex.sets.map {
                    LoggedSet(it.reps, it.weightKg, it.durationSeconds, it.steps, it.distanceMeters, it.completedAt)
                },
                type = ExerciseType.fromName(ex.type),
                typeRaw = if (runCatching { ExerciseType.valueOf(ex.type) }.isSuccess) null else ex.type,
            )
        }
    return WorkoutSession(id, workoutId, workoutName, startedAt, completedAt, exercises, taskId, isDeleted)
}

fun WorkoutSession.toEntity() = WorkoutSessionEntity(
    id = id,
    workoutId = workoutId,
    workoutName = workoutName,
    startedAt = startedAt,
    completedAt = completedAt,
    loggedExercisesJson = json.encodeToString(loggedExercises.map { ex ->
        LoggedExerciseJson(
            exerciseId = ex.exerciseId,
            exerciseName = ex.exerciseName,
            sets = ex.sets.map {
                LoggedSetJson(it.reps, it.weightKg, it.durationSeconds, it.steps, it.distanceMeters, it.completedAt)
            },
            type = ex.typeRaw ?: ex.type.name,
        )
    }),
    taskId = taskId,
    isDeleted = isDeleted,
)
