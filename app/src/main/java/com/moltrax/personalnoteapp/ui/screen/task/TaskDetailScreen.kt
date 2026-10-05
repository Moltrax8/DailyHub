package com.moltrax.personalnoteapp.ui.screen.task

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(nav: NavController, taskId: String, vm: TaskDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val workoutGroups by vm.workoutGroups.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    // Due-date label depends on the composition locale; recreated when the language changes.
    val locale = LocalConfiguration.current.locales[0]
    val deadlineFmt = remember(locale) { SimpleDateFormat("d MMM yyyy, HH:mm", locale) }

    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var deadlineError by rememberSaveable { mutableStateOf<String?>(null) }

    // Applies the selected due time; rejects a moment in the past with a warning
    // (1 min tolerance: "today, current minute" counts as valid).
    fun applyDeadline(candidate: Long) {
        if (candidate + 60_000L < System.currentTimeMillis()) {
            deadlineError = context.getString(R.string.task_past_date_error)
        } else {
            deadlineError = null
            vm.update { copy(dueDate = candidate) }
        }
    }

    LaunchedEffect(taskId) { vm.load(taskId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.task_new else R.string.task_edit)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save { nav.popBackStack() } },
                        enabled = state.title.isNotBlank() && !state.isSaving,
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { vm.update { copy(title = it) } },
                label = { Text(stringResource(R.string.task_title_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { vm.update { copy(notes = it) } },
                label = { Text(stringResource(R.string.task_notes_field)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 5,
            )

            // Subtasks (Checklist)
            SubtaskSection(
                subtasks = state.subtasks,
                onAdd = vm::addSubtask,
                onToggle = vm::toggleSubtask,
                onRemove = vm::removeSubtask,
            )

            // Due Date & Time (Deadline) — empty means no reminder/penalty kicks in
            Text(stringResource(R.string.task_deadline), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val deadlineText = state.dueDate?.let { deadlineFmt.format(Date(it)) }
                ?: stringResource(R.string.task_deadline_unset)
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(deadlineText, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.dueDate != null) {
                        IconButton(onClick = { vm.update { copy(dueDate = null) }; deadlineError = null }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.task_remove_deadline))
                        }
                    }
                }
            }
            deadlineError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.task_date))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                    // A date is needed first for the time (otherwise we set the date at the same time).
                ) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.task_time))
                }
            }

            // Categories — multi-select tags (Phase 2); zero tags allowed (untracked bucket).
            Text(stringResource(R.string.task_category), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.categories.isEmpty(),
                    onClick = { vm.clearCategories() },
                    label = { Text(stringResource(R.string.task_none)) },
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = state.categories.any { it.equals(cat.name, ignoreCase = true) },
                        onClick = { vm.toggleCategory(cat.name) },
                        label = { Text(cat.name) },
                        leadingIcon = if (cat.isPermanent) {
                            { Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.task_permanent),
                                modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }

            var newCategory by rememberSaveable { mutableStateOf("") }
            var newCategoryPermanent by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                label = { Text(stringResource(R.string.task_new_category)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            vm.createCategory(newCategory, newCategoryPermanent)
                            newCategory = ""
                            newCategoryPermanent = false
                        },
                        enabled = newCategory.isNotBlank(),
                    ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.task_add_category)) }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = newCategoryPermanent, onCheckedChange = { newCategoryPermanent = it })
                Text(stringResource(R.string.task_permanent_category_hint),
                    style = MaterialTheme.typography.bodyMedium)
            }

            // Priority editor removed in v1.1 (Phase 1.2): manual sortOrder is the
            // explicit prioritization mechanism. Field kept in ViewModel/Room/Drive for compat.

            // Recurrence (Recurring / Habit)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.task_recurring), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.isRecurring, onCheckedChange = { vm.update { copy(isRecurring = it) } })
            }
            if (state.isRecurring) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RecurrenceType.DAILY to stringResource(R.string.recurrence_daily),
                        RecurrenceType.WEEKLY to stringResource(R.string.recurrence_weekly),
                        RecurrenceType.MONTHLY to stringResource(R.string.recurrence_monthly),
                        RecurrenceType.INTERVAL to stringResource(R.string.recurrence_interval),
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = state.recurrenceType == type,
                            onClick = { vm.update { copy(recurrenceType = type) } },
                            label = { Text(label) },
                        )
                    }
                }
                when (state.recurrenceType) {
                    RecurrenceType.WEEKLY -> {
                        Text(stringResource(R.string.task_which_days), style = MaterialTheme.typography.labelMedium,
                            color = if (state.weeklyError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // ISO: 1=Monday .. 7=Sunday
                            listOf(
                                1 to R.string.weekday_mon, 2 to R.string.weekday_tue, 3 to R.string.weekday_wed,
                                4 to R.string.weekday_thu, 5 to R.string.weekday_fri, 6 to R.string.weekday_sat,
                                7 to R.string.weekday_sun,
                            ).forEach { (iso, labelRes) ->
                                FilterChip(
                                    selected = iso in state.recurrenceDaysOfWeek,
                                    onClick = { vm.toggleRecurrenceDay(iso) },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                        if (state.weeklyError) {
                            Text(
                                stringResource(R.string.task_weekly_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                stringResource(R.string.task_no_days_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    RecurrenceType.INTERVAL -> {
                        OutlinedTextField(
                            value = state.intervalDays?.toString() ?: "",
                            onValueChange = { vm.update { copy(intervalDays = it.toIntOrNull()) } },
                            label = { Text(stringResource(R.string.task_every_n_days)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.intervalError,
                            supportingText = if (state.intervalError) {
                                { Text(stringResource(R.string.task_interval_error), color = MaterialTheme.colorScheme.error) }
                            } else null,
                        )
                    }
                    else -> Unit
                }
                Text(
                    stringResource(R.string.task_recurring_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Focus duration editor removed in v1.1 (Phase 1.2). Field kept in
            // ViewModel/Room/Drive for compat; existing values still fire reminders.

            // Workout / Program link
            if (workoutGroups.isNotEmpty()) {
                Text(stringResource(R.string.task_link_workout), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Link type: None / Single workout (Day A) / Whole program (cycle)
                val linkMode = when {
                    state.linkedProgramId != null -> LinkMode.PROGRAM
                    state.linkedWorkoutId != null -> LinkMode.WORKOUT
                    else -> LinkMode.NONE
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = linkMode == LinkMode.NONE,
                        onClick = { vm.update { copy(linkedWorkoutId = null, linkedProgramId = null) } },
                        label = { Text(stringResource(R.string.task_none)) },
                    )
                    FilterChip(
                        selected = linkMode == LinkMode.WORKOUT,
                        onClick = {
                            // When "Single Workout" is picked and nothing is selected yet, pre-select the first workout.
                            // Otherwise no workout would stay selected, linkMode would fall back to NONE and
                            // "None" would wrongly appear checked (fixed bug).
                            val firstWorkoutId = workoutGroups
                                .firstOrNull { it.workouts.isNotEmpty() }?.workouts?.first()?.id
                            vm.update {
                                copy(linkedProgramId = null, linkedWorkoutId = linkedWorkoutId ?: firstWorkoutId)
                            }
                        },
                        label = { Text(stringResource(R.string.task_single_workout)) },
                    )
                    FilterChip(
                        selected = linkMode == LinkMode.PROGRAM,
                        onClick = { vm.update { copy(linkedWorkoutId = null, linkedProgramId = linkedProgramId ?: workoutGroups.first().id, programStartIndex = 0) } },
                        label = { Text(stringResource(R.string.task_whole_program)) },
                    )
                }

                when (linkMode) {
                    LinkMode.NONE -> Unit

                    LinkMode.WORKOUT -> {
                        val allWorkouts = workoutGroups.flatMap { g -> g.workouts.map { w -> g to w } }
                        val linkedName = allWorkouts.find { (_, w) -> w.id == state.linkedWorkoutId }
                            ?.let { (g, w) -> "${g.name} — ${w.name}" } ?: stringResource(R.string.task_pick_workout)
                        LabeledDropdown(label = stringResource(R.string.nav_workout_label), value = linkedName) { dismiss ->
                            allWorkouts.forEach { (g, w) ->
                                DropdownMenuItem(
                                    text = { Text("${g.name} — ${w.name}") },
                                    onClick = { vm.update { copy(linkedWorkoutId = w.id) }; dismiss() },
                                    leadingIcon = { Icon(Icons.Default.FitnessCenter, null) },
                                )
                            }
                        }
                    }

                    LinkMode.PROGRAM -> {
                        val program = workoutGroups.find { it.id == state.linkedProgramId }
                        val programName = program?.name ?: stringResource(R.string.task_pick_program)
                        LabeledDropdown(label = stringResource(R.string.task_program_label), value = programName) { dismiss ->
                            workoutGroups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.task_program_days, g.name, g.workouts.size)) },
                                    onClick = {
                                        vm.update { copy(linkedProgramId = g.id, programStartIndex = 0) }
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.FitnessCenter, null) },
                                )
                            }
                        }
                        // Which day the cycle starts from
                        if (program != null && program.workouts.isNotEmpty()) {
                            val startName = program.workouts.getOrNull(state.programStartIndex)?.name
                                ?: program.workouts.first().name
                            LabeledDropdown(label = stringResource(R.string.task_start_day), value = startName) { dismiss ->
                                program.workouts.forEachIndexed { idx, w ->
                                    DropdownMenuItem(
                                        text = { Text("${idx + 1}. ${w.name}") },
                                        onClick = { vm.update { copy(programStartIndex = idx) }; dismiss() },
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.task_program_advance_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Date picker (Material3). The picked date is combined with the current time component.
        if (showDatePicker) {
            // Today's UTC start of day: makes past days unselectable in the DatePicker.
            val utcTodayMidnight = remember {
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = state.dueDate ?: System.currentTimeMillis(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= utcTodayMidnight
                    override fun isSelectableYear(year: Int) =
                        year >= Calendar.getInstance().get(Calendar.YEAR)
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let { picked ->
                            applyDeadline(mergeDate(state.dueDate, picked))
                        }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_dismiss)) }
                },
            ) { DatePicker(state = dateState) }
        }

        // Time picker. If no date exists yet, it is combined with today's date.
        if (showTimePicker) {
            val base = Calendar.getInstance().apply { state.dueDate?.let { timeInMillis = it } }
            val timeState = rememberTimePickerState(
                initialHour = base.get(Calendar.HOUR_OF_DAY),
                initialMinute = base.get(Calendar.MINUTE),
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        applyDeadline(mergeTime(state.dueDate, timeState.hour, timeState.minute))
                        showTimePicker = false
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_dismiss)) }
                },
                text = { TimePicker(state = timeState) },
            )
        }
    }
}

/**
 * Merges the UTC start-of-day millis from the DatePicker with the current time component. If no
 * time has been picked yet, the current hour-minute is used (so picking "today" is not immediately
 * in the past because of a fixed default).
 */
private fun mergeDate(current: Long?, pickedUtcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMillis }
    val cal = Calendar.getInstance()
    if (current != null) cal.timeInMillis = current
    cal.set(Calendar.YEAR, utc.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Merges the picked hour/minute with the current date (today if none). */
private fun mergeTime(current: Long?, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    if (current != null) cal.timeInMillis = current
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Subtask (checklist) editing section: progress bar + counter, existing items (check/delete) and a
 * new-item input. Item states are kept only in this state; "Save" persists them.
 */
@Composable
private fun SubtaskSection(
    subtasks: List<com.moltrax.personalnoteapp.domain.model.SubTask>,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val done = subtasks.count { it.isDone }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.task_subtasks), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (subtasks.isNotEmpty()) {
                Text("$done/${subtasks.size}", style = MaterialTheme.typography.labelMedium,
                    color = AppColors.Accent)
            }
        }
        if (subtasks.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { if (subtasks.isEmpty()) 0f else done.toFloat() / subtasks.size },
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.Accent,
            )
        }
        subtasks.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sub.isDone, onCheckedChange = { onToggle(sub.id) })
                Text(
                    sub.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None,
                )
                IconButton(onClick = { onRemove(sub.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.task_remove_subtask),
                        modifier = Modifier.size(18.dp), tint = AppColors.PriorityHigh)
                }
            }
        }
        var newSub by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = newSub,
            onValueChange = { newSub = it },
            label = { Text(stringResource(R.string.task_new_subtask)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { onAdd(newSub); newSub = "" },
                    enabled = newSub.isNotBlank(),
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.task_add_subtask)) }
            },
        )
    }
}

private enum class LinkMode { NONE, WORKOUT, PROGRAM }

/** Simple dropdown with a read-only trigger; [content] produces the menu items and gives a dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}
