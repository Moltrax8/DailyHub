package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import android.content.Context
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Exercise
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.domain.model.WorkoutExercise
import com.moltrax.personalnoteapp.ui.components.ExerciseMediaPlayer
import com.moltrax.personalnoteapp.ui.components.ExerciseThumb
import com.moltrax.personalnoteapp.ui.i18n.label
import com.moltrax.personalnoteapp.ui.navigation.LiveWorkout
import com.moltrax.personalnoteapp.ui.screen.home.BottomNavBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(nav: NavController, groupId: String, vm: WorkoutViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group = groups.find { it.id == groupId }
    // ExerciseDB anahtarı ViewModel üzerinden (AppPreferences → StateFlow); boşsa demo kapalı.
    val exerciseDbKey by vm.exerciseDbKey.collectAsStateWithLifecycle()

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var newWorkoutName by remember { mutableStateOf("") }

    var showAddExerciseForWorkoutId by remember { mutableStateOf<String?>(null) }
    // Düzenlenen hareket: (workoutId, hareket). Null = düzenleme açık değil.
    var editExercise by remember { mutableStateOf<Pair<String, WorkoutExercise>?>(null) }

    // Antrenman ekleme dialogu
    if (showAddWorkoutDialog) {
        AlertDialog(
            onDismissRequest = { showAddWorkoutDialog = false; newWorkoutName = "" },
            title = { Text(stringResource(R.string.workout_new_workout)) },
            text = {
                OutlinedTextField(
                    value = newWorkoutName,
                    onValueChange = { newWorkoutName = it },
                    label = { Text(stringResource(R.string.workout_workout_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newWorkoutName.isNotBlank() && group != null) {
                        vm.addWorkout(group, newWorkoutName)
                        newWorkoutName = ""
                        showAddWorkoutDialog = false
                    }
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddWorkoutDialog = false; newWorkoutName = "" }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Hareket ekleme dialogu — egzersiz seçilince hedef (set/tekrar/kg veya süre/adım) değerleri sorulur
    val addForWorkoutId = showAddExerciseForWorkoutId
    if (addForWorkoutId != null && group != null) {
        val exerciseResults by vm.exerciseResults.collectAsStateWithLifecycle()
        AddExerciseDialog(
            exerciseDbKey = exerciseDbKey.orEmpty(),
            exerciseResults = exerciseResults,
            onSearch = { vm.searchExercises(it) },
            onDismiss = { showAddExerciseForWorkoutId = null },
            onConfirm = { picked, name, type, sets, reps, weight, durMin, steps, durSec ->
                val planned = vm.buildPlannedSets(type, sets, reps, weight, durMin, steps, durSec)
                if (picked != null) {
                    vm.addExerciseFromSearch(group, addForWorkoutId, picked, planned)
                } else {
                    vm.addExerciseToWorkout(group, addForWorkoutId, name, type = type, plannedSets = planned)
                }
                showAddExerciseForWorkoutId = null
            },
        )
    }

    // Eklenmiş bir hareketi düzenleme diyaloğu (hedef set/tekrar/ağırlık/süre güncelleme)
    val editing = editExercise
    if (editing != null && group != null) {
        val (editWorkoutId, ex) = editing
        val exercisesById by vm.exercisesById.collectAsStateWithLifecycle()
        // Çevrimdışı indirilmiş lokal demo; yoksa uzak URL'ye düş.
        val cached = exercisesById[ex.exerciseId]
        EditExerciseDialog(
            exercise = ex,
            mediaSource = cached?.let { it.localMediaPath ?: it.mediaUrl },
            exerciseDbKey = exerciseDbKey.orEmpty(),
            onDismiss = { editExercise = null },
            onDelete = {
                vm.deleteExercise(group, editWorkoutId, ex.id)
                editExercise = null
            },
            onSave = { type, sets, reps, weight, durMin, steps, durSec ->
                val planned = vm.buildPlannedSets(type, sets, reps, weight, durMin, steps, durSec)
                vm.updateExercise(group, editWorkoutId, ex.id, ex.exerciseName, type, planned)
                editExercise = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: stringResource(R.string.workout_program_fallback)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddWorkoutDialog = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.workout_add_workout))
            }
        },
        bottomBar = { BottomNavBar(nav) },
    ) { padding ->
        if (group == null) {
            // Geçersiz/silinmiş groupId'de sonsuz spinner yerine bulunamadı + geri dönüş.
            // (res'e dokunmamak için metin sabit; owned-files: ui/** only.)
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Program not found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.popBackStack() }) {
                    Text(stringResource(R.string.action_back))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (group.workouts.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.workout_no_workouts),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(group.workouts, key = { it.id }) { workout ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(workout.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { showAddExerciseForWorkoutId = workout.id }) {
                                Icon(Icons.Default.AddCircle, stringResource(R.string.workout_add_exercise),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { vm.deleteWorkout(group, workout.id) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (workout.exercises.isEmpty()) {
                            Text(stringResource(R.string.workout_no_exercises_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        } else {
                            workout.exercises.forEach { ex ->
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp)
                                        // Hareketin üzerine tıklayınca düzenleme açılır.
                                        .clickable { editExercise = workout.id to ex },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.FitnessCenter, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.exerciseName, style = MaterialTheme.typography.bodyMedium)
                                        val planText = plannedSummary(LocalContext.current, ex.type, ex.plannedSets)
                                        if (planText != null) {
                                            Text(planText, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(
                                        onClick = { editExercise = workout.id to ex },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Default.Edit, stringResource(R.string.workout_edit_exercise),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vm.startSession(workout)
                                nav.navigate(LiveWorkout(workout.id, groupId))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = workout.exercises.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.workout_start))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hareket ekleme diyaloğu. Önce tip + arama/manuel ad ile egzersiz seçilir; egzersiz seçilir
 * seçilmez hedef (başlangıç/hedef) değerleri sorulur: ağırlıkta set/tekrar/kg, kardiyoda süre +
 * adım/mesafe. Boş alanlarla kayıt yapılamaz; en azından bir hedef girilmelidir.
 *
 * onConfirm: (seçilen egzersiz veya null, ad, tip, setSayısı, tekrar, kg, süreDk, adım)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseDialog(
    exerciseDbKey: String,
    exerciseResults: List<Exercise>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        picked: Exercise?, name: String, type: ExerciseType,
        sets: Int, reps: Int, weightKg: Double?, durationMin: Int?, steps: Int?, durationSec: Int?,
    ) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manualType by remember { mutableStateOf(ExerciseType.WEIGHTLIFTING) }
    // Seçilen egzersiz: aramadan gelen gerçek kayıt ya da manuel (id = null). Seçilince hedef alanı açılır.
    var picked by remember { mutableStateOf<Exercise?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(ExerciseType.WEIGHTLIFTING) }

    // Hedef alanları
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }
    var durationMin by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var durationSec by remember { mutableStateOf("") }

    val hasSelection = selectedName != null

    fun select(name: String, type: ExerciseType, ex: Exercise?) {
        selectedName = name
        selectedType = type
        picked = ex
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (hasSelection) R.string.workout_plan_values else R.string.workout_add_exercise_title)) },
        text = {
            // Uzun egzersiz açıklaması + 180dp önizleme, diyalogun yüksekliğini aşınca set/tekrar/kg
            // alanları alta itilip erişilemez hale geliyordu. İçeriği dikey kaydırılabilir yaparak
            // her boyda tüm alanlara ulaşılmasını sağlıyoruz.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasSelection) {
                    // 1. Aşama: tip + arama / manuel ad
                    Text(stringResource(R.string.workout_type_manual), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExerciseType.entries.forEach { type ->
                            FilterChip(
                                selected = manualType == type,
                                onClick = { manualType = type },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; if (it.length >= 2) onSearch(it) },
                        label = { Text(stringResource(R.string.workout_search_exercise)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
if (exerciseResults.isNotEmpty()) {
                        Text(stringResource(R.string.workout_results), style = MaterialTheme.typography.labelSmall)
                        exerciseResults.take(5).forEach { ex ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        select(ex.name, ExerciseType.classify(ex.bodyPart, ex.equipment, ex.name), ex)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Aramada henüz indirme olmadığından demo uzak GIF URL'sinden gösterilir.
                                ExerciseThumb(ex.localMediaPath ?: ex.mediaUrl, sizeDp = 44, exerciseDbKey = exerciseDbKey)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, style = MaterialTheme.typography.bodyMedium)
                                    // Hareketin ek detayları: bölge · ekipman (varsa) — sadece isim değil.
                                    val detail = listOfNotNull(
                                        ex.bodyPart.takeIf { it.isNotBlank() },
                                        ex.equipment?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Text(detail, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (query.isNotBlank()) {
                        TextButton(
                            onClick = { select(query.trim(), manualType, null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.workout_add_manual, query.trim())) }
                    }
                } else {
                    // 2. Aşama: plan değer girişi (seçilen egzersiz + tip)
                    Text(selectedName!!, style = MaterialTheme.typography.titleSmall)
                    AssistChip(onClick = {}, enabled = false,
                        label = { Text(selectedType.label()) })
                    // Aramadan gelen hareketin demo medyası (önizleme — henüz uzak URL'den oynatılır).
                    picked?.let { ex ->
                        val source = ex.localMediaPath ?: ex.mediaUrl
                        if (!source.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            ExerciseMediaPlayer(source = source, heightDp = 180, exerciseDbKey = exerciseDbKey)
                        }
                        val detail = listOfNotNull(
                            ex.bodyPart.takeIf { it.isNotBlank() },
                            ex.equipment?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (detail.isNotBlank()) {
                            Text(detail, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        // Nasıl yapılır adımları (ExerciseDB instructions → açıklama).
                        ex.description?.takeIf { it.isNotBlank() }?.let { steps ->
                            Text(steps, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    when (selectedType) {
                        ExerciseType.WEIGHTLIFTING -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                                NumField(reps, { reps = it }, stringResource(R.string.field_reps), Modifier.weight(1f))
                                NumField(weight, { weight = it }, stringResource(R.string.field_kg), Modifier.weight(1f), decimal = true)
                            }
                            Text(stringResource(R.string.workout_hint_weight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExerciseType.BODYWEIGHT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                                NumField(reps, { reps = it }, stringResource(R.string.field_reps), Modifier.weight(1f))
                                NumField(weight, { weight = it }, stringResource(R.string.field_added_kg), Modifier.weight(1f), decimal = true)
                            }
                            Text(stringResource(R.string.workout_hint_bodyweight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExerciseType.DURATION -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                                NumField(durationSec, { durationSec = it }, stringResource(R.string.field_duration_sec), Modifier.weight(1f))
                            }
                            Text(stringResource(R.string.workout_hint_duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExerciseType.CARDIO -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField(durationMin, { durationMin = it }, stringResource(R.string.field_duration_min), Modifier.weight(1f))
                                NumField(steps, { steps = it }, stringResource(R.string.field_steps_distance), Modifier.weight(1f))
                            }
                        }
                    }
                    TextButton(onClick = { selectedName = null; picked = null }) {
                        Text(stringResource(R.string.workout_pick_another))
                    }
                }
            }
        },
        confirmButton = {
            val canConfirm = hasSelection && when (selectedType) {
                ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT -> (reps.toIntOrNull() ?: 0) > 0
                ExerciseType.DURATION -> (durationSec.toIntOrNull() ?: 0) > 0
                ExerciseType.CARDIO -> (durationMin.toIntOrNull() ?: 0) > 0 || (steps.toIntOrNull() ?: 0) > 0
            }
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        picked, selectedName!!, selectedType,
                        sets.toIntOrNull() ?: 1,
                        reps.toIntOrNull() ?: 0,
                        weight.replace(',', '.').toDoubleOrNull(),
                        durationMin.toIntOrNull(),
                        steps.toIntOrNull(),
                        durationSec.toIntOrNull(),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Eklenmiş bir hareketin hedef değerlerini düzenleme diyaloğu. Mevcut plan setlerinden ön-doldurulur;
 * tip değiştirilebilir, set/tekrar/ağırlık veya süre/adım güncellenebilir. "Sil" ile hareket kaldırılır.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditExerciseDialog(
    exercise: WorkoutExercise,
    mediaSource: String?,
    exerciseDbKey: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (
        type: ExerciseType, sets: Int, reps: Int, weightKg: Double?,
        durationMin: Int?, steps: Int?, durationSec: Int?,
    ) -> Unit,
) {
    val first = exercise.plannedSets.firstOrNull()
    var type by remember { mutableStateOf(exercise.type) }
    var sets by remember { mutableStateOf(exercise.plannedSets.size.takeIf { it > 0 }?.toString() ?: "3") }
    var reps by remember { mutableStateOf(first?.reps?.takeIf { it > 0 }?.toString() ?: "") }
    var weight by remember { mutableStateOf(first?.weightKg?.takeIf { it > 0 }?.let { trimKg(it) } ?: "") }
    var durationSec by remember { mutableStateOf(first?.durationSeconds?.takeIf { it > 0 }?.toString() ?: "") }
    var durationMin by remember {
        mutableStateOf(
            if (exercise.type == ExerciseType.CARDIO)
                first?.durationSeconds?.takeIf { it > 0 }?.let { (it / 60).toString() } ?: ""
            else "",
        )
    }
    var steps by remember { mutableStateOf(first?.steps?.takeIf { it > 0 }?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_edit_exercise_title)) },
        text = {
            // 180dp önizleme + alanlar diyalog yüksekliğini aşabildiğinden içerik kaydırılabilir.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall)
                // Çevrimdışı demo videosu/GIF'i (indirilmişse lokal dosyadan, yoksa uzak URL'den).
                if (!mediaSource.isNullOrBlank()) {
                    ExerciseMediaPlayer(source = mediaSource, heightDp = 180, exerciseDbKey = exerciseDbKey)
                }
                Text(stringResource(R.string.workout_type), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label()) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                when (type) {
                    ExerciseType.WEIGHTLIFTING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                        NumField(reps, { reps = it }, stringResource(R.string.field_reps), Modifier.weight(1f))
                        NumField(weight, { weight = it }, stringResource(R.string.field_kg), Modifier.weight(1f), decimal = true)
                    }
                    ExerciseType.BODYWEIGHT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                        NumField(reps, { reps = it }, stringResource(R.string.field_reps), Modifier.weight(1f))
                        NumField(weight, { weight = it }, stringResource(R.string.field_added_kg), Modifier.weight(1f), decimal = true)
                    }
                    ExerciseType.DURATION -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(sets, { sets = it }, stringResource(R.string.field_set), Modifier.weight(1f))
                        NumField(durationSec, { durationSec = it }, stringResource(R.string.field_duration_sec), Modifier.weight(1f))
                    }
                    ExerciseType.CARDIO -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(durationMin, { durationMin = it }, stringResource(R.string.field_duration_min), Modifier.weight(1f))
                        NumField(steps, { steps = it }, stringResource(R.string.field_steps_distance), Modifier.weight(1f))
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.workout_delete_exercise))
                }
            }
        },
        confirmButton = {
            val canSave = when (type) {
                ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT -> (reps.toIntOrNull() ?: 0) > 0
                ExerciseType.DURATION -> (durationSec.toIntOrNull() ?: 0) > 0
                ExerciseType.CARDIO -> (durationMin.toIntOrNull() ?: 0) > 0 || (steps.toIntOrNull() ?: 0) > 0
            }
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        type,
                        sets.toIntOrNull() ?: 1,
                        reps.toIntOrNull() ?: 0,
                        weight.replace(',', '.').toDoubleOrNull(),
                        durationMin.toIntOrNull(),
                        steps.toIntOrNull(),
                        durationSec.toIntOrNull(),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

/** Hareket listesinde gösterilecek kısa plan özeti (kullanıcının girdiği set/tekrar). Plan yoksa null. */
private fun plannedSummary(ctx: Context, type: ExerciseType, planned: List<PlannedSet>): String? {
    if (planned.isEmpty()) return null
    val first = planned.first()
    val plan = ctx.getString(R.string.plan_prefix)
    val kg = ctx.getString(R.string.unit_kg)
    return when (type) {
        ExerciseType.WEIGHTLIFTING -> buildString {
            append("$plan ${ctx.getString(R.string.plan_sets_reps, planned.size, first.reps)}")
            first.weightKg?.takeIf { it > 0 }?.let { append(" @ ${trimKg(it)} $kg") }
        }
        ExerciseType.BODYWEIGHT -> buildString {
            append("$plan ${ctx.getString(R.string.plan_sets_reps_bw, planned.size, first.reps)}")
            first.weightKg?.takeIf { it > 0 }?.let { append(" +${trimKg(it)} $kg") }
        }
        ExerciseType.DURATION -> buildString {
            append("$plan ${ctx.getString(R.string.plan_sets_x, planned.size)} ")
            append(first.durationSeconds?.takeIf { it > 0 }?.let { ctx.getString(R.string.logged_seconds, it) }
                ?: ctx.getString(R.string.plan_duration))
        }
        ExerciseType.CARDIO -> buildString {
            append("$plan ")
            first.durationSeconds?.takeIf { it > 0 }?.let { append(ctx.getString(R.string.logged_minutes, it / 60)) }
            first.steps?.takeIf { it > 0 }?.let { append(" · " + ctx.getString(R.string.logged_steps, it)) }
        }.takeIf { it.length > plan.length + 1 }
    }
}

private fun trimKg(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
