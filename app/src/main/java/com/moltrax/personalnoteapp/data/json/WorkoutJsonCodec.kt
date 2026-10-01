package com.moltrax.personalnoteapp.data.json

import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.domain.model.Workout
import com.moltrax.personalnoteapp.domain.model.WorkoutExercise
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Portable, versioned JSON transfer format for workout programs and completed workout
 * results. This is the PUBLIC file format — it is deliberately decoupled from the Room
 * entities and the Drive sync payload, so exported files stay usable when internal
 * models evolve.
 *
 * Pure Kotlin: no Android dependencies, unit-testable on the JVM.
 */
object WorkoutJsonCodec {

    const val PROGRAM_FORMAT = "dailyhub-workout-program"
    const val RESULT_FORMAT = "dailyhub-workout-result"

    /** Current schema version for both formats. Bump when the file shape changes. */
    const val FILE_FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Explicitly emit nulls + defaults: exported files must match the documented
        // schema examples exactly (and stay diff-friendly), independent of library defaults.
        explicitNulls = true
        encodeDefaults = true
    }

    // ------------------------------------------------------------------ program ---

    fun encodeProgram(group: WorkoutGroup, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            ProgramFile(
                format = PROGRAM_FORMAT,
                version = FILE_FORMAT_VERSION,
                exportedAt = now,
                program = ProgramPayload(
                    name = group.name,
                    workouts = group.workouts.map { w ->
                        ProgramWorkout(
                            name = w.name,
                            exercises = w.exercises.map { ex ->
                                ProgramExercise(
                                    exerciseId = ex.exerciseId,
                                    exerciseName = ex.exerciseName,
                                    type = ex.typeRaw ?: ex.type.name,
                                    plannedSets = ex.plannedSets.map { s ->
                                        ProgramPlannedSet(
                                            reps = s.reps,
                                            weightKg = s.weightKg,
                                            durationSeconds = s.durationSeconds,
                                            steps = s.steps,
                                            distanceMeters = s.distanceMeters,
                                        )
                                    },
                                )
                            },
                        )
                    },
                ),
            ),
        )

    /**
     * Parses + validates an imported program file. NEVER writes anything: on any failure
     * returns [Err] and the caller must not persist partial data.
     */
    fun decodeProgram(text: String): ProgramImportResult {
        val file = try {
            json.decodeFromString<ProgramFile>(text)
        } catch (_: Exception) {
            return ProgramImportResult.Err(ProgramImportError.Malformed)
        }
        if (file.format != PROGRAM_FORMAT) return ProgramImportResult.Err(ProgramImportError.WrongFormat)
        if (file.version > FILE_FORMAT_VERSION) {
            return ProgramImportResult.Err(ProgramImportError.UnsupportedVersion(file.version))
        }
        if (file.version < 1) return ProgramImportResult.Err(ProgramImportError.Malformed)

        val name = file.program.name.trim()
        if (name.isEmpty()) return ProgramImportResult.Err(ProgramImportError.BlankName)

        val now = System.currentTimeMillis()
        val workouts = file.program.workouts.mapIndexed { workoutIdx, w ->
            if (w.name.trim().isEmpty()) return ProgramImportResult.Err(ProgramImportError.BlankWorkoutName)
            val exercises = w.exercises.mapIndexed { exIdx, ex ->
                if (ex.exerciseId.trim().isEmpty() || ex.exerciseName.trim().isEmpty()) {
                    return ProgramImportResult.Err(ProgramImportError.BlankExercise)
                }
                val type = runCatching { ExerciseType.valueOf(ex.type) }.getOrNull()
                    ?: return ProgramImportResult.Err(ProgramImportError.InvalidExerciseType)
                val sets = ex.plannedSets.map { s ->
                    if (s.reps < 0 || (s.weightKg ?: 0.0) < 0.0 || (s.durationSeconds ?: 0) < 0 ||
                        (s.steps ?: 0) < 0 || (s.distanceMeters ?: 0.0) < 0.0
                    ) {
                        return ProgramImportResult.Err(ProgramImportError.InvalidValues)
                    }
                    PlannedSet(s.reps, s.weightKg, s.durationSeconds, s.steps, s.distanceMeters)
                }
                WorkoutExercise(
                    id = UUID.randomUUID().toString(),
                    exerciseId = ex.exerciseId.trim(),
                    exerciseName = ex.exerciseName.trim(),
                    plannedSets = sets,
                    orderIndex = exIdx,
                    type = type,
                )
            }
            Workout(
                id = UUID.randomUUID().toString(),
                name = w.name.trim(),
                exercises = exercises,
                createdAt = now,
                updatedAt = now,
            )
        }

        // Imported program always starts clean: fresh local IDs, first workout active,
        // no sync bookkeeping (timestamps fresh → normal LWW/sync path on save).
        return ProgramImportResult.Ok(
            WorkoutGroup(
                id = UUID.randomUUID().toString(),
                name = name,
                workouts = workouts,
                currentIndex = 0,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
            ),
        )
    }

    // ------------------------------------------------------------------- result ---

    /** Encodes the EXACT persisted session being displayed. Pure read — never mutates. */
    fun encodeResult(session: WorkoutSession, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            ResultFile(
                format = RESULT_FORMAT,
                version = FILE_FORMAT_VERSION,
                exportedAt = now,
                session = ResultSession(
                    id = session.id,
                    workoutId = session.workoutId,
                    workoutName = session.workoutName,
                    taskId = session.taskId,
                    startedAt = session.startedAt,
                    completedAt = session.completedAt,
                    exercises = session.loggedExercises.map { ex ->
                        ResultExercise(
                            exerciseId = ex.exerciseId,
                            exerciseName = ex.exerciseName,
                            type = ex.typeRaw ?: ex.type.name,
                            sets = ex.sets.map { s ->
                                ResultSet(
                                    reps = s.reps,
                                    weightKg = s.weightKg,
                                    durationSeconds = s.durationSeconds,
                                    steps = s.steps,
                                    distanceMeters = s.distanceMeters,
                                    completedAt = s.completedAt,
                                )
                            },
                        )
                    },
                ),
            ),
        )

    // ---------------------------------------------------------------- filenames ---

    /** `dailyhub-program-push-pull-legs.json`. Invalid chars → `-`, capped, never blank. */
    fun programFilename(programName: String): String {
        val slug = slugify(programName).ifBlank { "program" }
        return "dailyhub-program-$slug.json"
    }

    /** `dailyhub-workout-push-2026-10-01.json` (UTC date of completion, else start). */
    fun workoutResultFilename(workoutName: String, completedAt: Long?, startedAt: Long): String {
        val slug = slugify(workoutName).ifBlank { "workout" }
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(completedAt ?: startedAt))
        return "dailyhub-workout-$slug-$date.json"
    }

    private fun slugify(s: String): String {
        val folded = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return folded.replace("[^a-z0-9]+".toRegex(), "-").trim { it == '-' }.take(40)
    }
}

// ------------------------------------------------------------------- envelopes ---

@Serializable
data class ProgramFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val program: ProgramPayload,
)

@Serializable
data class ProgramPayload(
    val name: String,
    val workouts: List<ProgramWorkout> = emptyList(),
)

@Serializable
data class ProgramWorkout(
    val name: String,
    val exercises: List<ProgramExercise> = emptyList(),
)

@Serializable
data class ProgramExercise(
    val exerciseId: String,
    val exerciseName: String,
    val type: String = ExerciseType.WEIGHTLIFTING.name,
    val plannedSets: List<ProgramPlannedSet> = emptyList(),
)

@Serializable
data class ProgramPlannedSet(
    val reps: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
)

@Serializable
data class ResultFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val session: ResultSession,
)

@Serializable
data class ResultSession(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val taskId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val exercises: List<ResultExercise> = emptyList(),
)

@Serializable
data class ResultExercise(
    val exerciseId: String,
    val exerciseName: String,
    val type: String = ExerciseType.WEIGHTLIFTING.name,
    val sets: List<ResultSet> = emptyList(),
)

@Serializable
data class ResultSet(
    val reps: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
    val completedAt: Long = 0L,
)

// --------------------------------------------------------------------- errors ---

sealed interface ProgramImportResult {
    data class Ok(val group: WorkoutGroup) : ProgramImportResult
    data class Err(val error: ProgramImportError) : ProgramImportResult
}

sealed interface ProgramImportError {
    /** Not JSON, or JSON that doesn't match the envelope shape. */
    data object Malformed : ProgramImportError

    /** Valid JSON but a different `format` — not a DailyHub workout program. */
    data object WrongFormat : ProgramImportError

    /** `version` newer than this app understands. */
    data class UnsupportedVersion(val found: Int) : ProgramImportError

    /** Program name missing/blank. */
    data object BlankName : ProgramImportError

    /** A workout/day name missing/blank. */
    data object BlankWorkoutName : ProgramImportError

    /** An exercise id/name missing/blank. */
    data object BlankExercise : ProgramImportError

    /** `type` is not a known ExerciseType. */
    data object InvalidExerciseType : ProgramImportError

    /** Negative reps/weight/duration/steps/distance. */
    data object InvalidValues : ProgramImportError
}
