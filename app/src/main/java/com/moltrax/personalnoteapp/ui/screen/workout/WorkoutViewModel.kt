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

/** Tek seferlik dosya işlemi bildirimi (Snackbar ile tüketilir). */
data class FileNotice(val messageRes: Int, val success: Boolean)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseDbApi: ExerciseDbApi,
    private val videoStorage: ExerciseVideoStorage,
    private val fileManager: WorkoutFileManager,
    prefs: AppPreferences,
) : ViewModel() {

    /** Kullanıcının kendi ExerciseDB anahtarı (boşsa demo videoları devre dışı). */
    val exerciseDbKey: StateFlow<String?> = prefs.exerciseDbKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<WorkoutGroup>> = workoutRepo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Önbellekteki hareketler (exerciseId → Exercise) — düzenleme diyaloğunda lokal demo yolunu bulur. */
    val exercisesById: StateFlow<Map<String, Exercise>> = workoutRepo.observeExercises()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Arama kutusunun metni; gerçek API isteği [exerciseResults] içinde debounce'lanır. */
    private val searchQuery = MutableStateFlow("")

    /**
     * Hareket arama sonuçları: arama metni [debounce] (500 ms) ile dinlenir; kullanıcı yazmayı
     * bıraktıktan yarım saniye sonra TEK bir ExerciseDB isteği atılır. [mapLatest] yeni harf
     * gelince bekleyen/çalışan isteği iptal eder (RapidAPI hız limitini korur).
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

    // ------------------------------------------------------- JSON içe/dışa aktarma ---

    /** Tek seferlik dosya bildirimi (Snackbar ile tüketilir). */
    private val _fileNotice = MutableStateFlow<FileNotice?>(null)
    val fileNotice: StateFlow<FileNotice?> = _fileNotice.asStateFlow()
    fun consumeFileNotice() { _fileNotice.value = null }

    /**
     * SAF ile seçilen JSON dosyasını içe aktarır. Dosya önce tamamen doğrulanır;
     * doğrulama geçmeden hiçbir şey yazılmaz. Başarılı içe aktarma YENİ bir program
     * oluşturur (mevcut programların üzerine yazmaz).
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

    /** Programı kullanıcının seçtiği belgeye JSON olarak yazar. Program değişmez. */
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
            // Bu antrenmanla giden hareketlerin demo dosyaları başka yerde kullanılmıyorsa silinir.
            workoutRepo.cleanupOrphanedExerciseMedia()
        }
    }

    /**
     * Eklenmiş bir hareketin hedef değerlerini (ad/tip/plan setleri) günceller. Hareket [exerciseId]
     * (WorkoutExercise.id) ile bulunur; grup yeniden yazılır (LWW updatedAt repo'da bump'lanır).
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

    /** Eklenmiş bir hareketi antrenmandan kaldırır. */
    fun deleteExercise(group: WorkoutGroup, workoutId: String, exerciseId: String) {
        val updated = group.copy(
            workouts = group.workouts.map { w ->
                if (w.id == workoutId) w.copy(exercises = w.exercises.filter { it.id != exerciseId }) else w
            },
        )
        viewModelScope.launch {
            workoutRepo.upsertGroup(updated)
            // Hareket kaldırıldı; başka antrenmanda kullanılmıyorsa demo dosyası temizlenir.
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
     * Arama sonucundan hareket ekler: gerçek ExerciseDB id'sini korur, tipini bodyPart'tan
     * (ör. "cardio" → CARDIO) çıkarır, hedef değerleri ([plannedSets]) ile birlikte kaydeder
     * ve hareketi yerel önbelleğe yazar.
     */
    fun addExerciseFromSearch(
        group: WorkoutGroup,
        workoutId: String,
        exercise: Exercise,
        plannedSets: List<PlannedSet> = emptyList(),
    ) {
        viewModelScope.launch {
            // Önce hareketi önbelleğe yaz (demo URL'siyle), sonra demo medyasını arka planda
            // çevrimdışı kullanım için indirip lokal yolu kaydet.
            workoutRepo.upsertExercise(exercise)
            downloadMediaIfNeeded(exercise)
        }
        addExerciseToWorkout(
            group, workoutId, exercise.name, exercise.id,
            type = ExerciseType.classify(exercise.bodyPart, exercise.equipment, exercise.name),
            plannedSets = plannedSets,
        )
    }

    /** Hareketin demo medyasını (henüz inmemişse) lokal depolamaya indirir ve yolu kaydeder. */
    private suspend fun downloadMediaIfNeeded(exercise: Exercise) {
        val url = exercise.mediaUrl
        if (url.isNullOrBlank() || !exercise.localMediaPath.isNullOrBlank()) return
        val path = videoStorage.download(exercise.id, url) ?: return
        workoutRepo.upsertExercise(exercise.copy(localMediaPath = path))
    }

    /**
     * Hedef girişlerinden plan setleri üretir. Ağırlıkta [sets] adet aynı (tekrar+kg) set;
     * kardiyoda tek bir hedef (süre + adım/mesafe). EXP/hedef hesabı [PlannedSet] listesini kullanır.
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
        // Ağırlık: tekrar + kg. Vücut ağırlığı: tekrar + (varsa) ek ağırlık (weightKg = ek yük).
        ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT ->
            List(sets.coerceAtLeast(1)) { PlannedSet(reps = reps, weightKg = weightKg) }
        // Süre bazlı (plank): set başına hedef süre (saniye), tekrar yok.
        ExerciseType.DURATION ->
            List(sets.coerceAtLeast(1)) { PlannedSet(durationSeconds = durationSeconds) }
        ExerciseType.CARDIO ->
            listOf(PlannedSet(durationSeconds = durationMinutes?.times(60), steps = steps))
    }

    /** Arama metnini günceller; isteğin kendisi [exerciseResults] içinde debounce'lanır. */
    fun searchExercises(query: String) {
        searchQuery.value = query
    }

    private fun fetchBodyParts() {
        viewModelScope.launch {
            runCatching { exerciseDbApi.getBodyParts() }
        }
    }

    // LiveWorkoutScreen farklı ViewModel örneği aldığı için buradan session başlatır
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

    /** Seansı tamamlar: gerçekleşen setleri kalıcılaştırır ve özet ekranını açar. */
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
        // ExerciseDB "instructions" adımlarını madde-madde açıklamaya çevir (hareket seçim/detay
        // ekranında gösterilir). Boşsa null kalır.
        description = instructions.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• ${it.trim()}" },
        // ExerciseDB artık yanıt gövdesinde gifUrl DÖNDÜRMÜYOR; demo GIF'i id üzerinden ayrı
        // resim ucundan gelir ve X-RapidAPI-Key header'ı ister (Coil/indirici header'ı ekler).
        mediaUrl = exerciseGifUrl(id),
    )

    companion object {
        /** ExerciseDB demo GIF'inin URL'si (id'den). RapidAPI anahtarı header'ı ile yüklenir. */
        fun exerciseGifUrl(exerciseId: String): String =
            "https://exercisedb.p.rapidapi.com/image?exerciseId=$exerciseId&resolution=360"
    }
}
