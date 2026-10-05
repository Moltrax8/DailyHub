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
import androidx.compose.ui.text.style.TextOverflow
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
    // ExerciseDB key via the ViewModel (AppPreferences → StateFlow); empty means demos are off.
    val exerciseDbKey by vm.exerciseDbKey.collectAsStateWithLifecycle()

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var newWorkoutName by remember { mutableStateOf("") }

    var showAddExerciseForWorkoutId by remember { mutableStateOf<String?>(null) }
    // Edited exercise: (workoutId, exercise). Null = edit dialog is not open.
    var editExercise by remember { mutableStateOf<Pair<String, WorkoutExercise>?>(null) }

    // Add-workout dialog
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

    // Add-exercise dialog — once an exercise is picked, target (sets/reps/kg or duration/steps) values are asked
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

    // Dialog for editing an added exercise (updating target sets/reps/weight/duration)
    val editing = editExercise
    if (editing != null && group != null) {
        val (editWorkoutId, ex) = editing
        val exercisesById by vm.exercisesById.collectAsStateWithLifecycle()
        // Downloaded offline local demo; otherwise fall back to the remote URL.
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
                title = { Text(group?.name ?: stringResource(R.string.workout_program_fallback),
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddWorkoutDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.workout_add_workout))
            }
        },
        bottomBar = { BottomNavBar(nav) },
    ) { padding ->
        if (group == null) {
            // Show a not-found + back affordance instead of an endless spinner for an invalid/deleted groupId.
            // (Fixed text to avoid touching res; owned-files: ui/** only.)
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(workout.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                        // Tapping the exercise opens the editor.
                                        .clickable { editExercise = workout.id to ex },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.FitnessCenter, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.exerciseName, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val planText = plannedSummary(LocalContext.current, ex.type, ex.plannedSets)
                                        if (planText != null) {
                                            Text(planText, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(
                                        onClick = { editExercise = workout.id to ex },
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
 * Add-exercise dialog. First an exercise is picked via type + search/manual name; once picked,
 * target (start/goal) values are asked: sets/reps/kg for weights, duration + steps/distance for
 * cardio. Cannot save with empty fields; at least one target must be entered.
 *
 * onConfirm: (picked exercise or null, name, type, set count, reps, kg, minutes, steps)
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
    // Picked exercise: real record from search or manual (id = null). The target form opens once picked.
    var picked by remember { mutableStateOf<Exercise?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(ExerciseType.WEIGHTLIFTING) }

    // Target fields
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
            // Long exercise description + 180dp preview used to push the set/reps/kg fields below the
            // fold once the dialog height was exceeded. Making the content vertically scrollable keeps
            // all fields reachable at any size.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasSelection) {
                    // Stage 1: type + search / manual name
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
                                // No download has happened yet in search, so the demo is shown from the remote GIF URL.
                                ExerciseThumb(ex.localMediaPath ?: ex.mediaUrl, sizeDp = 44, exerciseDbKey = exerciseDbKey)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, style = MaterialTheme.typography.bodyMedium)
                                    // Extra exercise details: body part · equipment (if any) — not just the name.
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
                    // Stage 2: plan value entry (picked exercise + type)
                    Text(selectedName!!, style = MaterialTheme.typography.titleSmall)
                    AssistChip(onClick = {}, enabled = false,
                        label = { Text(selectedType.label()) })
                    // Demo media of the search-picked exercise (preview — still played from the remote URL).
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
                        // How-to steps (ExerciseDB instructions → description).
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
 * Dialog for editing an added exercise's target values. Pre-filled from the current planned sets;
 * the type can be changed, sets/reps/weight or duration/steps updated. "Delete" removes the exercise.
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
            // Content is scrollable since the 180dp preview + fields can exceed the dialog height.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall)
                // Offline demo video/GIF (from the downloaded local file if present, else the remote URL).
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

/** Short plan summary shown in the exercise list (the user's entered sets/reps). Null when there is no plan. */
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
