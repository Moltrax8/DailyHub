package com.moltrax.personalnoteapp.data.json

import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.domain.model.Workout
import com.moltrax.personalnoteapp.domain.model.WorkoutExercise
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Portable workout JSON conversion tests. All fixtures use fixed ids/timestamps —
 * nothing here depends on UUID randomness or wall-clock time.
 */
class WorkoutJsonCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun programFixture() = WorkoutGroup(
        id = "group-1",
        name = "Push Pull Legs",
        workouts = listOf(
            Workout(
                id = "workout-1",
                name = "Push",
                exercises = listOf(
                    WorkoutExercise(
                        id = "we-1",
                        exerciseId = "0025",
                        exerciseName = "bench press",
                        plannedSets = listOf(
                            PlannedSet(reps = 8, weightKg = 80.0),
                            PlannedSet(reps = 6, weightKg = 85.0, durationSeconds = null),
                        ),
                        orderIndex = 0,
                        type = ExerciseType.WEIGHTLIFTING,
                    ),
                    WorkoutExercise(
                        id = "we-2",
                        exerciseId = "cardio-9",
                        exerciseName = "running",
                        plannedSets = listOf(
                            PlannedSet(durationSeconds = 600, steps = 1000, distanceMeters = 800.0),
                        ),
                        orderIndex = 1,
                        type = ExerciseType.CARDIO,
                    ),
                ),
                createdAt = 1_000L,
                updatedAt = 2_000L,
            ),
        ),
        currentIndex = 1,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    private fun sessionFixture() = WorkoutSession(
        id = "session-1",
        workoutId = "workout-1",
        workoutName = "Push",
        startedAt = 1_780_000_000_000L,
        completedAt = 1_780_000_360_000L,
        loggedExercises = listOf(
            LoggedExercise(
                exerciseId = "0025",
                exerciseName = "bench press",
                sets = listOf(
                    LoggedSet(reps = 8, weightKg = 80.0, completedAt = 1_780_000_050_000L),
                    LoggedSet(reps = 8, weightKg = 82.5, completedAt = 1_780_000_100_000L),
                ),
                type = ExerciseType.WEIGHTLIFTING,
            ),
        ),
        taskId = "task-7",
    )

    @Test
    fun `program export import round trip preserves content`() {
        val original = programFixture()
        val text = WorkoutJsonCodec.encodeProgram(original, now = 9_999L)
        val result = WorkoutJsonCodec.decodeProgram(text)
        assertTrue(result is ProgramImportResult.Ok)
        val imported = (result as ProgramImportResult.Ok).group

        assertEquals("Push Pull Legs", imported.name)
        assertEquals(1, imported.workouts.size)
        assertEquals("Push", imported.workouts[0].name)
        assertEquals(2, imported.workouts[0].exercises.size)

        val bench = imported.workouts[0].exercises[0]
        assertEquals("0025", bench.exerciseId)
        assertEquals("bench press", bench.exerciseName)
        assertEquals(ExerciseType.WEIGHTLIFTING, bench.type)
        assertEquals(0, bench.orderIndex)
        assertEquals(
            listOf(PlannedSet(reps = 8, weightKg = 80.0), PlannedSet(reps = 6, weightKg = 85.0)),
            bench.plannedSets,
        )

        val cardio = imported.workouts[0].exercises[1]
        assertEquals(ExerciseType.CARDIO, cardio.type)
        assertEquals(1, cardio.orderIndex)
        assertEquals(
            listOf(PlannedSet(durationSeconds = 600, steps = 1000, distanceMeters = 800.0)),
            cardio.plannedSets,
        )
    }

    @Test
    fun `imported program gets fresh internal ids and clean state`() {
        val text = WorkoutJsonCodec.encodeProgram(programFixture())
        val first = (WorkoutJsonCodec.decodeProgram(text) as ProgramImportResult.Ok).group
        val second = (WorkoutJsonCodec.decodeProgram(text) as ProgramImportResult.Ok).group

        // Internal hierarchy ids are regenerated…
        assertNotEquals("group-1", first.id)
        assertNotEquals("workout-1", first.workouts[0].id)
        assertNotEquals("we-1", first.workouts[0].exercises[0].id)
        // …differently on every import (no shared identity between imports)…
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.workouts[0].id, second.workouts[0].id)
        // …while logical exercise ids survive.
        assertEquals("0025", first.workouts[0].exercises[0].exerciseId)
        assertEquals("0025", second.workouts[0].exercises[0].exerciseId)
        // Clean execution state: rotation restarts from the first workout.
        assertEquals(0, first.currentIndex)
        assertEquals(false, first.isDeleted)
    }

    @Test
    fun `malformed json fails cleanly`() {
        assertTrue(WorkoutJsonCodec.decodeProgram("not json {{{") is ProgramImportResult.Err)
        assertTrue(WorkoutJsonCodec.decodeProgram("") is ProgramImportResult.Err)
        assertTrue(WorkoutJsonCodec.decodeProgram("""{"format":"dailyhub-workout-program"}""") is ProgramImportResult.Err)
    }

    @Test
    fun `wrong format fails`() {
        val text = """{"format":"dailyhub-workout-result","version":1,"exportedAt":0,
            "program":{"name":"X","workouts":[]}}"""
        val result = WorkoutJsonCodec.decodeProgram(text)
        assertTrue("expected Err but was $result", result is ProgramImportResult.Err)
        assertEquals(
            ProgramImportError.WrongFormat,
            (result as ProgramImportResult.Err).error,
        )
    }

    @Test
    fun `unsupported version fails`() {
        val text = WorkoutJsonCodec.encodeProgram(programFixture())
            .replace("\"version\": 1", "\"version\": 99")
        val result = WorkoutJsonCodec.decodeProgram(text)
        assertTrue(result is ProgramImportResult.Err)
        assertEquals(
            ProgramImportError.UnsupportedVersion(99),
            (result as ProgramImportResult.Err).error,
        )
    }

    @Test
    fun `unknown extra fields are tolerated`() {
        val text = WorkoutJsonCodec.encodeProgram(programFixture())
            .replace("\"name\": \"Push Pull Legs\"", "\"name\": \"Push Pull Legs\", \"future\": {\"x\": [1, 2]}")
        val result = WorkoutJsonCodec.decodeProgram(text)
        assertTrue(result is ProgramImportResult.Ok)
        assertEquals("Push Pull Legs", (result as ProgramImportResult.Ok).group.name)
    }

    @Test
    fun `blank names and bad values fail`() {
        val base = WorkoutJsonCodec.encodeProgram(programFixture())
        // Blank program name.
        assertTrue(
            WorkoutJsonCodec.decodeProgram(base.replace("Push Pull Legs", "   "))
                is ProgramImportResult.Err,
        )
        // Unknown exercise type.
        val badText = base.replace("\"type\": \"WEIGHTLIFTING\"", "\"type\": \"FLYING\"")
        assertTrue("replace missed its target", badText.contains("FLYING"))
        val badTypeResult = WorkoutJsonCodec.decodeProgram(badText)
        assertTrue(
            "expected Err but was $badTypeResult",
            badTypeResult is ProgramImportResult.Err,
        )
        // Negative reps.
        assertTrue(
            WorkoutJsonCodec.decodeProgram(base.replace("\"reps\": 8", "\"reps\": -8"))
                is ProgramImportResult.Err,
        )
    }

    @Test
    fun `result export contains all logged sets and measurements`() {
        val session = sessionFixture()
        val text = WorkoutJsonCodec.encodeResult(session, now = 9_999L)
        val file = json.decodeFromString<ResultFile>(text)

        assertEquals("dailyhub-workout-result", file.format)
        assertEquals(1, file.version)
        assertEquals("session-1", file.session.id)
        assertEquals("workout-1", file.session.workoutId)
        assertEquals("Push", file.session.workoutName)
        assertEquals("task-7", file.session.taskId)
        assertEquals(1_780_000_000_000L, file.session.startedAt)
        assertEquals(1_780_000_360_000L, file.session.completedAt)
        assertEquals(1, file.session.exercises.size)
        val ex = file.session.exercises[0]
        assertEquals("0025", ex.exerciseId)
        assertEquals("bench press", ex.exerciseName)
        assertEquals("WEIGHTLIFTING", ex.type)
        assertEquals(2, ex.sets.size)
        assertEquals(8, ex.sets[0].reps)
        assertEquals(80.0, ex.sets[0].weightKg)
        assertEquals(1_780_000_050_000L, ex.sets[0].completedAt)
        assertEquals(82.5, ex.sets[1].weightKg)
    }

    @Test
    fun `filenames are sanitized and deterministic`() {
        assertEquals(
            "dailyhub-program-push-pull-legs.json",
            WorkoutJsonCodec.programFilename("Push Pull Legs!"),
        )
        assertEquals(
            "dailyhub-program-program.json",
            WorkoutJsonCodec.programFilename("   "),
        )
        assertEquals(
            "dailyhub-workout-push-2026-10-01.json",
            WorkoutJsonCodec.workoutResultFilename("Push", 1_790_812_800_000L, 0L),
        )
    }
}
