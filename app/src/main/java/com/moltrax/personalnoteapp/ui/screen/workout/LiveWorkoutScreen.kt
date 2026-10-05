package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.ui.components.ExerciseMediaPlayer
import com.moltrax.personalnoteapp.ui.i18n.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWorkoutScreen(nav: NavController, workoutId: String, groupId: String, vm: WorkoutViewModel = hiltViewModel()) {
    // LiveWorkoutScreen takes a different ViewModel instance → load via initSession
    LaunchedEffect(workoutId) { vm.initSession(workoutId) }

    val session by vm.liveSession.collectAsStateWithLifecycle()
    // Exercise demo media (downloaded offline local path; otherwise the remote GIF/video URL).
    val exercisesById by vm.exercisesById.collectAsStateWithLifecycle()
    // ExerciseDB key via the ViewModel (AppPreferences → StateFlow); empty means demos are off.
    val exerciseDbKey by vm.exerciseDbKey.collectAsStateWithLifecycle()

    var finishedSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.workoutName ?: stringResource(R.string.live_workout_title),
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = {
                        vm.finishSession { sessionId -> finishedSessionId = sessionId }
                    }) { Text(stringResource(R.string.live_workout_finish)) }
                }
            )
        }
    ) { padding ->
        val exercises = session?.loggedExercises ?: emptyList()
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (exercises.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.workout_no_exercises),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(exercises) { _, ex ->
                val cached = exercisesById[ex.exerciseId]
                ExerciseLogCard(
                    exercise = ex,
                    mediaSource = cached?.let { it.localMediaPath ?: it.mediaUrl },
                    exerciseDbKey = exerciseDbKey.orEmpty(),
                    onLog = { set -> vm.logSet(ex.exerciseId, set) },
                )
            }
        }
    }

    // Once the workout finishes, a summary bottom sheet: shows the completion info and closes.
    finishedSessionId?.let {
        WorkoutSummarySheet(
            onClose = { finishedSessionId = null; nav.popBackStack() },
        )
    }
}

@Composable
private fun ExerciseLogCard(
    exercise: LoggedExercise,
    mediaSource: String?,
    exerciseDbKey: String,
    onLog: (LoggedSet) -> Unit,
) {
    // Whether the demo (GIF/video) dialog is open — triggered by the "how to" button.
    var showDemo by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                // Opens the demo showing how the exercise is done (only visible when media exists).
                if (!mediaSource.isNullOrBlank()) {
                    IconButton(onClick = { showDemo = true }) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.cd_how_to),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(exercise.type.label()) },
                )
            }
            Text(stringResource(R.string.live_sets_logged, exercise.sets.size), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            when (exercise.type) {
                ExerciseType.WEIGHTLIFTING -> WeightliftingInput(onLog)
                ExerciseType.BODYWEIGHT -> BodyweightInput(onLog)
                ExerciseType.DURATION -> DurationInput(onLog)
                ExerciseType.CARDIO -> CardioInput(onLog)
            }

            val ctx = LocalContext.current
            exercise.sets.forEachIndexed { i, set ->
                Text(
                    stringResource(R.string.set_index, i + 1, formatLoggedSet(ctx, exercise.type, set)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    // Demo GIF/video dialog: opens when the "how to" button is pressed.
    // (exerciseDbKey is a function parameter so it is directly visible inside the lambda.)
    if (showDemo && !mediaSource.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showDemo = false },
            confirmButton = {
                TextButton(onClick = { showDemo = false }) { Text(stringResource(R.string.action_close)) }
            },
            title = { Text(exercise.exerciseName) },
            text = { ExerciseMediaPlayer(source = mediaSource, heightDp = 240, exerciseDbKey = exerciseDbKey) },
        )
    }
}

@Composable
private fun WeightliftingInput(onLog: (LoggedSet) -> Unit) {
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            reps, { reps = it }, label = { Text(stringResource(R.string.field_reps)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            weight, { weight = it }, label = { Text(stringResource(R.string.field_kg)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        IconButton(onClick = {
            val r = reps.toIntOrNull() ?: return@IconButton
            onLog(LoggedSet(reps = r, weightKg = weight.replace(',', '.').toDoubleOrNull()))
            reps = ""; weight = ""
        }) { Icon(Icons.Default.Add, stringResource(R.string.add_set)) }
    }
}

@Composable
private fun BodyweightInput(onLog: (LoggedSet) -> Unit) {
    var reps by remember { mutableStateOf("") }
    var addedWeight by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            reps, { reps = it }, label = { Text(stringResource(R.string.field_reps)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            addedWeight, { addedWeight = it }, label = { Text(stringResource(R.string.field_added_kg)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        IconButton(onClick = {
            val r = reps.toIntOrNull() ?: return@IconButton
            // Bodyweight: null when no added weight is entered (pure bodyweight).
            onLog(LoggedSet(reps = r, weightKg = addedWeight.replace(',', '.').toDoubleOrNull()))
            reps = ""; addedWeight = ""
        }) { Icon(Icons.Default.Add, stringResource(R.string.add_set)) }
    }
}

@Composable
private fun DurationInput(onLog: (LoggedSet) -> Unit) {
    var seconds by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            seconds, { seconds = it }, label = { Text(stringResource(R.string.field_duration_sec)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        IconButton(onClick = {
            val s = seconds.toIntOrNull() ?: return@IconButton
            onLog(LoggedSet(reps = 0, durationSeconds = s))
            seconds = ""
        }) { Icon(Icons.Default.Add, stringResource(R.string.add_set)) }
    }
}

@Composable
private fun CardioInput(onLog: (LoggedSet) -> Unit) {
    var minutes by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            minutes, { minutes = it }, label = { Text(stringResource(R.string.field_duration_min)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            steps, { steps = it }, label = { Text(stringResource(R.string.field_steps_distance)) },
            modifier = Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        IconButton(onClick = {
            val min = minutes.toIntOrNull() ?: return@IconButton
            onLog(
                LoggedSet(
                    reps = 0,
                    durationSeconds = min * 60,
                    steps = steps.toIntOrNull(),
                )
            )
            minutes = ""; steps = ""
        }) { Icon(Icons.Default.Add, stringResource(R.string.add_entry)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSummarySheet(
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.live_workout_done_title), style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                stringResource(R.string.live_workout_done_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

private fun formatLoggedSet(ctx: Context, type: ExerciseType, set: LoggedSet): String {
    val kg = ctx.getString(R.string.unit_kg)
    return when (type) {
        ExerciseType.WEIGHTLIFTING ->
            ctx.getString(R.string.logged_reps, set.reps) + (set.weightKg?.let { " – ${trimKg(it)}$kg" } ?: "")
        ExerciseType.BODYWEIGHT ->
            ctx.getString(R.string.logged_reps, set.reps) +
                (set.weightKg?.takeIf { it > 0 }?.let { " (+${trimKg(it)}$kg)" }
                    ?: " (${ctx.getString(R.string.logged_bodyweight)})")
        ExerciseType.DURATION -> set.durationSeconds?.let { ctx.getString(R.string.logged_seconds, it) }
            ?: ctx.getString(R.string.logged_entry)
        ExerciseType.CARDIO -> buildString {
            set.durationSeconds?.let { append(ctx.getString(R.string.logged_minutes, it / 60)) }
            set.steps?.let { append(" · " + ctx.getString(R.string.logged_steps, it)) }
            set.distanceMeters?.let { append(" · ${trimKg(it)} ${ctx.getString(R.string.unit_m)}") }
        }.ifBlank { ctx.getString(R.string.logged_entry) }
    }
}

private fun trimKg(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
