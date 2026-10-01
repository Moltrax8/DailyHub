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
    // Id of the sport task that produced this session (if any). Used to find the summary/result
    // page from the completed task. Null for sessions started from a live workout (not tied to a task).
    val taskId: String? = null,
    // Tombstone: the deleted session record is kept so Drive sync does not resurrect the
    // deleted session from remote. Visible lists are filtered at the DAO layer.
    val isDeleted: Boolean = false,
)

@Serializable
data class LoggedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSet> = emptyList(),
    /** The move's type — the EXP algorithm picks the weight/cardio branch based on it. */
    val type: ExerciseType = ExerciseType.WEIGHTLIFTING,
    /** Raw form of unknown (future-version) type names; preserved on the write path. */
    val typeRaw: String? = null,
)

/**
 * Performed (logged) set. For weight [reps]/[weightKg] is filled; for cardio [durationSeconds] +
 * [steps]/[distanceMeters] is filled.
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
    /** Does it carry at least one meaningful measurement? Used to filter fully empty sets from the log. */
    fun isMeaningful(): Boolean =
        reps > 0 || (weightKg ?: 0.0) > 0.0 || (durationSeconds ?: 0) > 0 ||
            (steps ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0
}
