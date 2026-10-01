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
    // Last-change time for sync conflict resolution (LWW). Updated when the group is added,
    // edited, when a workout is added/removed inside it, or when the group is deleted.
    val updatedAt: Long = System.currentTimeMillis(),
    // Tombstone: true when the group is deleted (record is kept). This way Drive sync does NOT
    // resurrect the deleted group/workout from remote. Hidden from visible lists.
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
    /** The move's input type (weight/cardio); determines the fields on the live screen. */
    val type: ExerciseType = ExerciseType.WEIGHTLIFTING,
    /** Raw form of unknown (future-version) type names; preserved on the write path. */
    val typeRaw: String? = null,
)

/**
 * Planned (target) set. For weight moves [reps]/[weightKg] are meaningful; for cardio moves
 * [durationSeconds] + [steps]/[distanceMeters] are meaningful.
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
