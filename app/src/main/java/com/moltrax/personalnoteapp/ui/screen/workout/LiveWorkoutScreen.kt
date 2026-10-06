package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhLogInputRow
import com.moltrax.personalnoteapp.ui.components.DhRestTimerPill
import com.moltrax.personalnoteapp.ui.components.DhSetRow
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.components.ExerciseMediaPlayer
import com.moltrax.personalnoteapp.ui.i18n.label
import kotlinx.coroutines.delay

private const val REST_DEFAULT_SECONDS = 90

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
    // Planned targets for the "previous" column (read-only lookup; session logging is unchanged).
    val groups by vm.groups.collectAsStateWithLifecycle()
    val plannedByExercise: Map<String, List<PlannedSet>> = remember(groups, workoutId) {
        groups.flatMap { it.workouts }.find { it.id == workoutId }
            ?.exercises?.associate { it.exerciseId to it.plannedSets }
            .orEmpty()
    }

    var finishedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var restSeconds by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(restSeconds) {
        val current = restSeconds ?: return@LaunchedEffect
        if (current > 0) {
            delay(1_000)
            restSeconds = current - 1
        } else {
            restSeconds = null
        }
    }
    val onSetLogged: () -> Unit = { restSeconds = REST_DEFAULT_SECONDS }

    Scaffold(
        topBar = {
            DhTopBar(
                title = session?.workoutName ?: stringResource(R.string.live_workout_title),
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = { vm.finishSession { sessionId -> finishedSessionId = sessionId } },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.live_workout_finish),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { padding ->
        val exercises = session?.loggedExercises ?: emptyList()
        if (exercises.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                DhEmptyState(
                    icon = Icons.Filled.FitnessCenter,
                    title = stringResource(R.string.live_workout_title),
                    description = stringResource(R.string.workout_no_exercises),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = { nav.popBackStack() },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (restSeconds != null) {
                    item(key = "rest") {
                        DhRestTimerPill(
                            secondsLeft = restSeconds ?: 0,
                            onAdd30 = { restSeconds = (restSeconds ?: 0) + 30 },
                            onSkip = { restSeconds = null },
                            addLabel = stringResource(R.string.live_rest_add),
                            skipLabel = stringResource(R.string.live_rest_skip),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                itemsIndexed(exercises, key = { _, ex -> ex.exerciseId }) { _, ex ->
                    val cached = exercisesById[ex.exerciseId]
                    ExerciseLogCard(
                        exercise = ex,
                        planned = plannedByExercise[ex.exerciseId].orEmpty(),
                        mediaSource = cached?.let { it.localMediaPath ?: it.mediaUrl },
                        exerciseDbKey = exerciseDbKey.orEmpty(),
                        onLog = { set ->
                            vm.logSet(ex.exerciseId, set)
                            onSetLogged()
                        },
                    )
                }
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
    planned: List<PlannedSet>,
    mediaSource: String?,
    exerciseDbKey: String,
    onLog: (LoggedSet) -> Unit,
) {
    // Whether the demo (GIF/video) dialog is open — triggered by the "how to" button.
    var showDemo by remember { mutableStateOf(false) }

    DhCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Opens the demo showing how the exercise is done (only visible when media exists).
            if (!mediaSource.isNullOrBlank()) {
                IconButton(onClick = { showDemo = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.cd_how_to),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DhStatusChip(label = exercise.type.label())
            Text(
                stringResource(R.string.live_sets_logged, exercise.sets.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (exercise.type) {
            ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT -> WeightSetRows(
                exercise = exercise,
                planned = planned,
                onLog = onLog,
            )
            ExerciseType.DURATION -> DurationLogRows(exercise = exercise, planned = planned, onLog = onLog)
            ExerciseType.CARDIO -> CardioLogInput(exercise = exercise, onLog = onLog)
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
            title = {
                Text(exercise.exerciseName, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = { ExerciseMediaPlayer(source = mediaSource, heightDp = 240, exerciseDbKey = exerciseDbKey) },
            shape = MaterialTheme.shapes.large,
        )
    }
}

/** Hevy-style compact set rows: [set #] [previous] [kg] [reps] [check]. */
@Composable
private fun WeightSetRows(
    exercise: LoggedExercise,
    planned: List<PlannedSet>,
    onLog: (LoggedSet) -> Unit,
) {
    val ctx = LocalContext.current
    val logged = exercise.sets
    // One row per planned set; always one extra blank row so free sets stay loggable.
    val rowCount = maxOf(planned.size + 1, logged.size + 1)
    val kgLabel = stringResource(R.string.field_kg)
    val repsLabel = stringResource(R.string.field_reps)
    val checkDescription = stringResource(R.string.add_set)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(24.dp))
        Text(
            stringResource(R.string.live_col_previous),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(76.dp))
        Spacer(Modifier.width(76.dp))
        Spacer(Modifier.size(48.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rowCount) { i ->
            val loggedSet = logged.getOrNull(i)
            val target = planned.getOrNull(i)
            val previous = when {
                loggedSet != null -> formatLoggedSet(ctx, exercise.type, loggedSet)
                target != null -> formatPlanned(ctx, target)
                else -> null
            }
            var kg by remember(exercise.exerciseId, i) {
                mutableStateOf(
                    loggedSet?.weightKg?.let { trimKg(it) }
                        ?: target?.weightKg?.let { trimKg(it) }
                        ?: "",
                )
            }
            var reps by remember(exercise.exerciseId, i) {
                mutableStateOf(
                    loggedSet?.reps?.takeIf { it > 0 }?.toString()
                        ?: target?.reps?.takeIf { it > 0 }?.toString()
                        ?: "",
                )
            }
            DhSetRow(
                index = i + 1,
                previous = previous,
                kg = if (loggedSet != null) loggedSet.weightKg?.let { trimKg(it) } ?: "" else kg,
                onKgChange = { kg = it },
                reps = if (loggedSet != null) loggedSet.reps.takeIf { it > 0 }?.toString() ?: "" else reps,
                onRepsChange = { reps = it },
                checked = loggedSet != null,
                onCheck = {
                    val r = reps.toIntOrNull() ?: 0
                    if (r <= 0) return@DhSetRow
                    onLog(LoggedSet(reps = r, weightKg = kg.replace(',', '.').toDoubleOrNull()))
                },
                kgLabel = kgLabel,
                repsLabel = repsLabel,
                checkDescription = checkDescription,
            )
        }
    }
    if (logged.isEmpty()) {
        Text(
            stringResource(R.string.live_no_sets_yet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DurationLogRows(
    exercise: LoggedExercise,
    planned: List<PlannedSet>,
    onLog: (LoggedSet) -> Unit,
) {
    var seconds by remember(exercise.exerciseId) {
        mutableStateOf(planned.firstOrNull()?.durationSeconds?.takeIf { it > 0 }?.toString() ?: "")
    }
    DhLogInputRow(
        value = seconds,
        onValueChange = { seconds = it },
        onLog = {
            val s = seconds.toIntOrNull() ?: return@DhLogInputRow
            if (s <= 0) return@DhLogInputRow
            onLog(LoggedSet(reps = 0, durationSeconds = s))
            seconds = ""
        },
        label = stringResource(R.string.field_duration_sec),
        checkDescription = stringResource(R.string.add_set),
    )
    LoggedSetList(exercise = exercise)
}

@Composable
private fun CardioLogInput(
    exercise: LoggedExercise,
    onLog: (LoggedSet) -> Unit,
) {
    var minutes by remember(exercise.exerciseId) { mutableStateOf("") }
    var steps by remember(exercise.exerciseId) { mutableStateOf("") }
    DhLogInputRow(
        value = minutes,
        onValueChange = { minutes = it },
        onLog = {
            val min = minutes.toIntOrNull() ?: return@DhLogInputRow
            if (min <= 0) return@DhLogInputRow
            onLog(
                LoggedSet(
                    reps = 0,
                    durationSeconds = min * 60,
                    steps = steps.toIntOrNull(),
                ),
            )
            minutes = ""
            steps = ""
        },
        label = stringResource(R.string.field_duration_min),
        checkDescription = stringResource(R.string.add_entry),
        secondValue = steps,
        onSecondValueChange = { steps = it },
        secondLabel = stringResource(R.string.field_steps_distance),
    )
    LoggedSetList(exercise = exercise)
}

/** Logged sets as checked rows (visible checked state for duration/cardio). */
@Composable
private fun LoggedSetList(exercise: LoggedExercise) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        exercise.sets.forEachIndexed { i, set ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (i + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                    maxLines = 1,
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatLoggedSet(ctx, exercise.type, set),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (exercise.sets.isEmpty()) {
            Text(
                stringResource(R.string.live_no_sets_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.live_workout_done_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.live_workout_done_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

private fun formatPlanned(ctx: Context, planned: PlannedSet): String {
    val kg = ctx.getString(R.string.unit_kg)
    val reps = planned.reps
    val weight = planned.weightKg?.takeIf { it > 0 }?.let { trimKg(it) }
    return if (reps > 0 && weight != null) {
        "$reps × $weight $kg"
    } else if (reps > 0) {
        ctx.getString(R.string.logged_reps, reps)
    } else {
        planned.durationSeconds?.takeIf { it > 0 }?.let { ctx.getString(R.string.logged_seconds, it) }
            ?: ctx.getString(R.string.logged_entry)
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
