package com.moltrax.personalnoteapp.ui.screen.workout

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.json.ProgramImportError
import com.moltrax.personalnoteapp.data.json.ProgramImportResult
import com.moltrax.personalnoteapp.data.json.WorkoutJsonCodec
import com.moltrax.personalnoteapp.data.local.storage.WorkoutFileManager
import com.moltrax.personalnoteapp.data.remote.exercisedb.ExerciseDbApi
import com.moltrax.personalnoteapp.data.remote.exercisedb.ExerciseDbItem
import com.moltrax.personalnoteapp.domain.model.Exercise
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.domain.model.Workout
import com.moltrax.personalnoteapp.domain.model.WorkoutExercise
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import com.moltrax.personalnoteapp.data.local.storage.ExerciseVideoStorage
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One-shot file-operation notice (consumed via Snackbar). */
data class FileNotice(val messageRes: Int, val success: Boolean)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseDbApi: ExerciseDbApi,
    private val videoStorage: ExerciseVideoStorage,
    private val fileManager: WorkoutFileManager,
    prefs: AppPreferences,
) : ViewModel() {

    /** The user's own ExerciseDB key (empty means demo videos are disabled). */
    val exerciseDbKey: StateFlow<String?> = prefs.exerciseDbKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<WorkoutGroup>> = workoutRepo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cached exercises (exerciseId → Exercise) — finds the local demo path in the edit dialog. */
    val exercisesById: StateFlow<Map<String, Exercise>> = workoutRepo.observeExercises()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Search box text; the actual API request is debounced inside [exerciseResults]. */
    private val searchQuery = MutableStateFlow("")

    /**
     * Exercise search results: the query text is observed with [debounce] (500 ms); a SINGLE ExerciseDB
     * request fires half a second after the user stops typing. [mapLatest] cancels the pending/running
     * request when a new letter arrives (protects the RapidAPI rate limit).
     */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val exerciseResults: StateFlow<List<Exercise>> = searchQuery
        .debounce(500L)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.length < 2) emptyList()
            else runCatching { exerciseDbApi.searchByName(q).map { it.toExercise() } }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _liveSession = MutableStateFlow<WorkoutSession?>(null)
    val liveSession: StateFlow<WorkoutSession?> = _liveSession.asStateFlow()

    init { fetchBodyParts() }

    fun addGroup(name: String) {
        viewModelScope.launch {
            workoutRepo.upsertGroup(WorkoutGroup(name = name))
        }
    }

    fun deleteGroup(id: String) { viewModelScope.launch { workoutRepo.deleteGroup(id) } }

    // ------------------------------------------------------- JSON import/export ---

    /** One-shot file notice (consumed via Snackbar). */
    private val _fileNotice = MutableStateFlow<FileNotice?>(null)
    val fileNotice: StateFlow<FileNotice?> = _fileNotice.asStateFlow()
    fun consumeFileNotice() { _fileNotice.value = null }

    /**
     * Imports the JSON file picked via SAF. The file is fully validated first;
     * nothing is written unless validation passes. A successful import creates a NEW program
     * (it never overwrites existing programs).
     */
    fun importProgramFile(uri: Uri) {
        viewModelScope.launch {
            val text = fileManager.readText(uri).getOrElse {
                _fileNotice.value = FileNotice(R.string.workout_import_failed, false)
                return@launch
            }
            when (val result = WorkoutJsonCodec.decodeProgram(text)) {
                is ProgramImportResult.Ok -> {
                    workoutRepo.upsertGroup(result.group)
                    _fileNotice.value = FileNotice(R.string.workout_program_imported, true)
                }
                is ProgramImportResult.Err ->
                    _fileNotice.value = FileNotice(result.error.messageRes(), false)
            }
        }
    }

    /** Writes the program as JSON to the user-picked document. The program is unchanged. */
    fun exportProgramToUri(group: WorkoutGroup, uri: Uri) {
        viewModelScope.launch {
            val ok = fileManager
                .writeText(uri, WorkoutJsonCodec.encodeProgram(group))
                .isSuccess
            _fileNotice.value = FileNotice(
                if (ok) R.string.workout_program_exported else R.string.workout_export_failed,
                ok,
            )
        }
    }

    private fun ProgramImportError.messageRes(): Int = when (this) {
        ProgramImportError.Malformed,
        ProgramImportError.BlankName,
        ProgramImportError.BlankWorkoutName,
        ProgramImportError.BlankExercise,
        ProgramImportError.InvalidExerciseType,
        ProgramImportError.InvalidValues,
        ProgramImportError.WrongFormat,
        -> R.string.workout_import_failed
        is ProgramImportError.UnsupportedVersion -> R.string.workout_import_unsupported
    }

    fun addWorkout(group: WorkoutGroup, workoutName: String) {
        viewModelScope.launch {
            workoutRepo.upsertGroup(group.copy(workouts = group.workouts + Workout(name = workoutName)))
        }
    }

    fun deleteWorkout(group: WorkoutGroup, workoutId: String) {
        viewModelScope.launch {
            workoutRepo.upsertGroup(group.copy(workouts = group.workouts.filter { it.id != workoutId }))
            // Demo files of exercises removed with this workout are deleted when unused elsewhere.
            workoutRepo.cleanupOrphanedExerciseMedia()
        }
    }

    /**
     * Updates the target values (name/type/planned sets) of an added exercise. The exercise is found
     * by [exerciseId] (WorkoutExercise.id); the group is rewritten (LWW updatedAt is bumped in the repo).
     */
    fun updateExercise(
        group: WorkoutGroup,
        workoutId: String,
        exerciseId: String,
        name: String,
        type: ExerciseType,
        plannedSets: List<PlannedSet>,
    ) {
        val updated = group.copy(
            workouts = group.workouts.map { w ->
                if (w.id == workoutId) w.copy(
                    exercises = w.exercises.map { ex ->
                        if (ex.id == exerciseId) ex.copy(exerciseName = name, type = type, plannedSets = plannedSets)
                        else ex
                    },
                ) else w
            },
        )
        viewModelScope.launch { workoutRepo.upsertGroup(updated) }
    }

    /** Removes an added exercise from the workout. */
    fun deleteExercise(group: WorkoutGroup, workoutId: String, exerciseId: String) {
        val updated = group.copy(
            workouts = group.workouts.map { w ->
                if (w.id == workoutId) w.copy(exercises = w.exercises.filter { it.id != exerciseId }) else w
            },
        )
        viewModelScope.launch {
            workoutRepo.upsertGroup(updated)
            // The exercise was removed; clean up its demo file when unused by any other workout.
            workoutRepo.cleanupOrphanedExerciseMedia()
        }
    }

    fun addExerciseToWorkout(
        group: WorkoutGroup,
        workoutId: String,
        exerciseName: String,
        exerciseId: String = UUID.randomUUID().toString(),
        type: ExerciseType = ExerciseType.WEIGHTLIFTING,
        plannedSets: List<PlannedSet> = emptyList(),
    ) {
        val updatedGroup = group.copy(
            workouts = group.workouts.map { w ->
                if (w.id == workoutId) w.copy(
                    exercises = w.exercises + WorkoutExercise(
                        id = UUID.randomUUID().toString(),
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        type = type,
                        plannedSets = plannedSets,
                    )
                ) else w
            }
        )
        viewModelScope.launch { workoutRepo.upsertGroup(updatedGroup) }
    }

    /**
     * Adds an exercise from a search result: keeps the real ExerciseDB id, derives the type from bodyPart
     * (e.g. "cardio" → CARDIO), saves it with the target values ([plannedSets]),
     * and writes the exercise to the local cache.
     */
    fun addExerciseFromSearch(
        group: WorkoutGroup,
        workoutId: String,
        exercise: Exercise,
        plannedSets: List<PlannedSet> = emptyList(),
    ) {
        viewModelScope.launch {
            // Write the exercise to the cache first (with its demo URL), then download the demo media
            // in the background and save the local path for offline use.
            workoutRepo.upsertExercise(exercise)
            downloadMediaIfNeeded(exercise)
        }
        addExerciseToWorkout(
            group, workoutId, exercise.name, exercise.id,
            type = ExerciseType.classify(exercise.bodyPart, exercise.equipment, exercise.name),
            plannedSets = plannedSets,
        )
    }

    /** Downloads the exercise demo media (if not yet downloaded) to local storage and saves the path. */
    private suspend fun downloadMediaIfNeeded(exercise: Exercise) {
        val url = exercise.mediaUrl
        if (url.isNullOrBlank() || !exercise.localMediaPath.isNullOrBlank()) return
        val path = videoStorage.download(exercise.id, url) ?: return
        workoutRepo.upsertExercise(exercise.copy(localMediaPath = path))
    }

    /**
     * Builds planned sets from the target inputs. For weights, [sets] identical (reps+kg) sets;
     * for cardio, a single target (duration + steps/distance). EXP/target math uses the [PlannedSet] list.
     */
    fun buildPlannedSets(
        type: ExerciseType,
        sets: Int,
        reps: Int,
        weightKg: Double?,
        durationMinutes: Int?,
        steps: Int?,
        durationSeconds: Int? = null,
    ): List<PlannedSet> = when (type) {
        // Weights: reps + kg. Bodyweight: reps + (optional) added load (weightKg = extra load).
        ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT ->
            List(sets.coerceAtLeast(1)) { PlannedSet(reps = reps, weightKg = weightKg) }
        // Duration-based (plank): target duration (seconds) per set, no reps.
        ExerciseType.DURATION ->
            List(sets.coerceAtLeast(1)) { PlannedSet(durationSeconds = durationSeconds) }
        ExerciseType.CARDIO ->
            listOf(PlannedSet(durationSeconds = durationMinutes?.times(60), steps = steps))
    }

    /** Updates the search text; the request itself is debounced inside [exerciseResults]. */
    fun searchExercises(query: String) {
        searchQuery.value = query
    }

    private fun fetchBodyParts() {
        viewModelScope.launch {
            runCatching { exerciseDbApi.getBodyParts() }
        }
    }

    // LiveWorkoutScreen takes a different ViewModel instance, so it starts the session from here
    fun initSession(workoutId: String) {
        if (_liveSession.value?.workoutId == workoutId) return
        viewModelScope.launch {
            val allGroups = workoutRepo.observeGroups().first()
            val workout = allGroups.flatMap { it.workouts }.find { it.id == workoutId } ?: return@launch
            startSession(workout)
        }
    }

    fun startSession(workout: Workout) {
        _liveSession.update {
            WorkoutSession(
                workoutId = workout.id,
                workoutName = workout.name,
                loggedExercises = workout.exercises.map { ex ->
                    LoggedExercise(ex.exerciseId, ex.exerciseName, type = ex.type)
                },
            )
        }
    }

    fun logSet(exerciseId: String, set: LoggedSet) {
        _liveSession.update { session ->
            session?.copy(
                loggedExercises = session.loggedExercises.map { ex ->
                    if (ex.exerciseId == exerciseId) ex.copy(sets = ex.sets + set) else ex
                }
            )
        }
    }

    /** Completes the session: persists the actual sets and opens the summary screen. */
    fun finishSession(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val session = _liveSession.value?.copy(completedAt = System.currentTimeMillis()) ?: return@launch
            workoutRepo.saveSession(session)
            _liveSession.update { null }
            onDone(session.id)
        }
    }

    private fun ExerciseDbItem.toExercise() = Exercise(
        id = id, name = name, bodyPart = bodyPart,
        equipment = equipment,
        // Converts ExerciseDB "instructions" steps into a bulleted description (shown on the
        // exercise pick/detail screen). Stays null when empty.
        description = instructions.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• ${it.trim()}" },
        // ExerciseDB no longer returns gifUrl in the response body; the demo GIF comes from a separate
        // image endpoint by id and requires the X-RapidAPI-Key header (the Coil/downloader adds it).
        mediaUrl = exerciseGifUrl(id),
    )

    companion object {
        /** URL of the ExerciseDB demo GIF (from id). Loaded with the RapidAPI key header. */
        fun exerciseGifUrl(exerciseId: String): String =
            "https://exercisedb.p.rapidapi.com/image?exerciseId=$exerciseId&resolution=360"
    }
}
