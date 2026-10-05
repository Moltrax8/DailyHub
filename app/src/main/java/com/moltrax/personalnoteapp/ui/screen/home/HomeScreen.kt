package com.moltrax.personalnoteapp.ui.screen.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.ui.SyncViewModel
import com.moltrax.personalnoteapp.ui.i18n.label
import com.moltrax.personalnoteapp.ui.navigation.*
import com.moltrax.personalnoteapp.ui.theme.AppColors
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
// Explicit import instead of java.util.*: java.util.Calendar would clash with the
// CalendarContent sub-tab composable (there is no Calendar navigation route).
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    nav: NavController,
    vm: HomeViewModel = hiltViewModel(),
    syncVm: SyncViewModel = hiltViewModel(),
    // Redirection for "complete workout task" coming from the widget (consumed once it reaches Home).
    pendingWidgetAction: String? = null,
    pendingWidgetTaskId: String? = null,
    onWidgetActionConsumed: () -> Unit = {},
) {
    // When a workout-linked task is checked from the widget: open the set/reps/weight entry screen here.
    LaunchedEffect(pendingWidgetAction, pendingWidgetTaskId) {
        if (pendingWidgetAction == com.moltrax.personalnoteapp.MainActivity.ACTION_COMPLETE_WORKOUT &&
            !pendingWidgetTaskId.isNullOrBlank()
        ) {
            vm.openWorkoutCompletion(pendingWidgetTaskId)
            onWidgetActionConsumed()
        }
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val syncStatus by syncVm.syncStatus.collectAsStateWithLifecycle()
    val showBirthday by vm.showBirthday.collectAsStateWithLifecycle()
    val birthdayAge by vm.birthdayAge.collectAsStateWithLifecycle()
    val workoutCompletion by vm.workoutCompletion.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val taskNotFoundTick by vm.taskNotFoundTick.collectAsStateWithLifecycle()
    val summarySessionId by vm.openSummarySessionId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // If the id coming from the widget was deleted/unknown, show a Snackbar instead of silently swallowing it.
    val taskNotFoundMsg = stringResource(R.string.task_not_found)
    LaunchedEffect(taskNotFoundTick) {
        if (taskNotFoundTick > 0) {
            snackbarHostState.showSnackbar(taskNotFoundMsg)
        }
    }

    // When a workout task is completed (or a completed task is tapped), open the workout result page.
    LaunchedEffect(summarySessionId) {
        summarySessionId?.let {
            nav.navigate(WorkoutSummary(it))
            vm.consumeSummary()
        }
    }

    // Undo Snackbar: shown for both normal tasks and workout-linked completions.
    // Text is resolved in composition per locale, so it also adapts to language changes.
    val undoMessage = undo?.messageRes?.let { stringResource(it) }
    val undoActionLabel = stringResource(R.string.undo)
    LaunchedEffect(undo?.token) {
        val u = undo
        if (u != null) {
            val res = snackbarHostState.showSnackbar(
                message = undoMessage.orEmpty(),
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) vm.undoLastCompletion() else vm.clearUndo()
        }
    }
    // Refresh animation is only visible during user-triggered (manual) sync; silent
    // background sync never sets the state to Syncing, so no spinner appears.
    val isRefreshing = syncStatus is SyncStatus.Syncing
    var showManageCategories by rememberSaveable { mutableStateOf(false) }
    // If the parent task is about to be completed before all subtasks are done, ask for confirmation.
    // (Task is not Parcelable; the transient confirmation dialog is lost on process death — accepted behavior.)
    var confirmComplete by remember { mutableStateOf<Task?>(null) }
    // 0 = Tasks list, 1 = Calendar (the old separate tab now lives here).
    var homeTab by rememberSaveable { mutableStateOf(0) }

    // Request the Android 13+ notification permission once (needed for reminders). If denied,
    // show the rationale in a Snackbar and route to the system settings.
    val context = LocalContext.current
    val notifScope = rememberCoroutineScope()
    val notifRationale = stringResource(R.string.notif_permission_rationale)
    val notifSettingsLabel = stringResource(R.string.notif_permission_open_settings)
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            notifScope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = notifRationale,
                    actionLabel = notifSettingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (res == SnackbarResult.ActionPerformed) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    }
                }
            }
        }
    }
    // Request the Android 13+ notification permission (needed for reminders). Re-checked on
    // every foreground return, not just first composition: a single denial still allows a
    // second system prompt, while a permanent denial falls through to the Snackbar rationale
    // below, which routes to system settings.
    fun maybeAskNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val notifLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(notifLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) maybeAskNotifPermission()
        }
        notifLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { notifLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // FAB only on the Tasks tab (add new task); hidden on the Calendar tab.
            if (homeTab == 0) {
                FloatingActionButton(onClick = { nav.navigate(TaskDetail("new")) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_new_task))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The sync banner is now global (in AppNavHost, above all tabs).

            // Title + pending badge (now outside the list, so draggable items
            // map one-to-one to LazyColumn indices).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val pending = state.allTasks.count { !it.isDone }
                    Text(
                        if (pending > 0) stringResource(R.string.home_pending_badge, pending)
                        else stringResource(R.string.empty_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (homeTab == 0) {
                    IconButton(onClick = { showManageCategories = true }) {
                        Icon(Icons.Default.Category, contentDescription = stringResource(R.string.home_manage_categories),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Tasks / Calendar sibling views as a compact segmented control.
            com.moltrax.personalnoteapp.ui.components.DhSegmentedControl(
                options = listOf(stringResource(R.string.tab_tasks), stringResource(R.string.tab_calendar)),
                selectedIndex = homeTab,
                onSelect = { homeTab = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (homeTab == 0) {
                TaskFilterBar(
                    filter = state.filter,
                    categories = state.categories,
                    onFilterChange = vm::updateFilter,
                )

                // Pull to refresh: the gesture triggers a manual sync, the spinner runs while the state is Syncing.
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { syncVm.sync() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    TaskList(
                        tasks = state.filteredTasks,
                        onReorder = vm::reorderTasks,
                        onToggle = { task ->
                            // If completing + there are unfinished subtasks, confirm first; otherwise proceed directly.
                            if (!task.isDone && task.hasIncompleteSubtasks) confirmComplete = task
                            else vm.toggleDone(task)
                        },
                        onTap = { task ->
                            // Completed, workout-linked task → open the workout result/summary page.
                            // In every other case go to the task edit screen.
                            if (task.isDone && (task.linkedWorkoutId != null || task.linkedProgramId != null))
                                vm.requestSummaryForTask(task.id)
                            else nav.navigate(TaskDetail(task.id))
                        },
                        onDelete = { vm.deleteTask(it.id) },
                        emptyText = when (state.filter.status) {
                            TaskStatus.DONE   -> stringResource(R.string.empty_done)
                            TaskStatus.ALL    -> stringResource(R.string.empty_all)
                            TaskStatus.ACTIVE -> stringResource(R.string.empty_active)
                        },
                    )
                }
            } else {
                // Calendar sub-view
                com.moltrax.personalnoteapp.ui.screen.calendar.CalendarContent(
                    nav = nav,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        if (showBirthday) {
            AlertDialog(
                onDismissRequest = { vm.dismissBirthday() },
                icon = { Icon(Icons.Default.Cake, contentDescription = null, tint = AppColors.Accent) },
                title = { Text(stringResource(R.string.birthday_title)) },
                text = {
                    Text(
                        birthdayAge?.let { stringResource(R.string.birthday_msg_age, it) }
                            ?: stringResource(R.string.birthday_msg)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.dismissBirthday() }) { Text(stringResource(R.string.birthday_thanks)) }
                },
            )
        }

        confirmComplete?.let { task ->
            val remaining = task.subtaskCount - task.doneSubtaskCount
            AlertDialog(
                onDismissRequest = { confirmComplete = null },
                icon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = AppColors.Accent) },
                title = { Text(stringResource(R.string.subtasks_incomplete_title)) },
                text = {
                    Text(stringResource(R.string.subtasks_incomplete_msg, remaining))
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.toggleDone(task)
                        confirmComplete = null
                    }) { Text(stringResource(R.string.complete_anyway)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmComplete = null }) { Text(stringResource(R.string.action_dismiss)) }
                },
            )
        }

        if (showManageCategories) {
            ManageCategoriesSheet(
                categories = state.allCategories,
                onAdd = vm::addPermanentCategory,
                onRename = vm::renameCategory,
                onDelete = vm::deleteCategory,
                onDismiss = { showManageCategories = false },
            )
        }

        // While completing a workout-linked task: accordion card per exercise, set-based data entry
        workoutCompletion?.let { req ->
            WorkoutCompletionSheet(
                request = req,
                onDismiss = { vm.cancelWorkoutCompletion() },
                onConfirm = { vm.submitWorkoutCompletion(it) },
                onAutosave = { vm.saveWorkoutDraft(req.task.id, it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageCategoriesSheet(
    categories: List<Category>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    val permanent = categories.filter { it.isPermanent }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.permanent_categories), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.permanent_categories_desc),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (permanent.isEmpty()) {
                Text(stringResource(R.string.no_permanent_categories), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                permanent.forEach { cat ->
                    CategoryEditRow(
                        name = cat.name,
                        onRename = { newN -> onRename(cat.name, newN) },
                        onDelete = { pendingDelete = cat.name },
                    )
                }
            }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_permanent_category)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAdd(newName); newName = "" },
                    enabled = newName.isNotBlank(),
                ) { Text(stringResource(R.string.action_add)) }
            }
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_msg, name)) },
            confirmButton = { TextButton(onClick = { onDelete(name); pendingDelete = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_dismiss)) } },
        )
    }
}

@Composable
private fun CategoryEditRow(name: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var text by rememberSaveable(name) { mutableStateOf(name) }
    val canSave = text.isNotBlank() && text.trim() != name
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (canSave) onRename(text.trim()) }, enabled = canSave) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_save_name),
                tint = if (canSave) AppColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = AppColors.PriorityHigh)
        }
    }
}

@Composable
private fun TaskFilterBar(
    filter: TaskFilter,
    categories: List<String>,
    onFilterChange: (TaskFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        // Status filter: All / Active / Completed
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                TaskStatus.ALL    to stringResource(R.string.filter_all),
                TaskStatus.ACTIVE to stringResource(R.string.filter_active),
                TaskStatus.DONE   to stringResource(R.string.filter_done),
            ).forEach { (status, label) ->
                FilterChip(
                    selected = filter.status == status,
                    onClick = { onFilterChange(filter.copy(status = status)) },
                    label = { Text(label) },
                )
            }
        }

        // Category filter: multi-select ANY-match (Phase 2) + untagged bucket.
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter.categories.isEmpty() && !filter.untaggedOnly,
                    onClick = { onFilterChange(filter.copy(categories = emptySet(), untaggedOnly = false)) },
                    label = { Text(stringResource(R.string.categories_all)) },
                )
                FilterChip(
                    selected = filter.untaggedOnly,
                    onClick = {
                        onFilterChange(filter.copy(
                            untaggedOnly = !filter.untaggedOnly,
                            categories = emptySet(),
                        ))
                    },
                    label = { Text(stringResource(R.string.categories_untagged)) },
                )
                categories.forEach { cat ->
                    val selected = filter.categories.any { it.equals(cat, ignoreCase = true) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = if (selected) {
                                filter.categories.filterNot { it.equals(cat, ignoreCase = true) }.toSet()
                            } else {
                                filter.categories + cat
                            }
                            onFilterChange(filter.copy(categories = next, untaggedOnly = false))
                        },
                        label = { Text(cat) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onReorder: (List<String>) -> Unit,
    onToggle: (Task) -> Unit,
    onTap: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    emptyText: String,
) {
    if (tasks.isEmpty()) {
        // LazyColumn (not Box): a scrollable container is needed so the pull-to-refresh gesture
        // is still detected on an empty list. The single item fills the screen, the text is centered.
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    // Local ordered copy for smooth animation while dragging; synced when the data source changes.
    // Persistent write + sync happen only when the drag ENDS (onDragStopped), not on every step.
    var ordered by remember { mutableStateOf(tasks) }
    LaunchedEffect(tasks) { ordered = tasks }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ordered, key = { it.id }) { task ->
            ReorderableItem(reorderableState, key = task.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elevation")
                TaskItem(
                    task = task,
                    elevation = elevation,
                    onToggle = { onToggle(task) },
                    onTap = { onTap(task) },
                    onDelete = { onDelete(task) },
                    dragHandle = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(36.dp).draggableHandle(
                                onDragStopped = { onReorder(ordered.map { it.id }) },
                            ),
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.cd_drag_reorder),
                                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    elevation: Dp = 0.dp,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    // Due-date formatter depends on the composition locale; recreated when the language changes.
    val locale = LocalConfiguration.current.locales[0]
    val taskDueFmt = remember(locale) { SimpleDateFormat("d MMM HH:mm", locale) }
    val overdue = !task.isDone && task.dueDate != null && task.dueDate < System.currentTimeMillis()
    val dueToday = !task.isDone && task.dueDate != null && !overdue &&
        java.text.SimpleDateFormat("yyyyMMdd", locale).format(Date(task.dueDate)) ==
        java.text.SimpleDateFormat("yyyyMMdd", locale).format(Date(System.currentTimeMillis()))
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            dragHandle?.invoke()
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = if (task.isDone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val hasMeta = task.isDone || task.isRecurring || task.dueDate != null ||
                    task.categoryNames.isNotEmpty() || task.linkedWorkoutId != null ||
                    task.linkedProgramId != null || task.subtaskCount > 0
                if (hasMeta) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (task.isDone) {
                            com.moltrax.personalnoteapp.ui.components.DhStatusChip(
                                label = stringResource(R.string.task_badge_done),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                icon = Icons.Default.CheckCircle,
                            )
                        }
                        if (task.isRecurring) {
                            com.moltrax.personalnoteapp.ui.components.DhStatusChip(
                                label = stringResource(R.string.cd_recurring),
                                icon = Icons.Default.Repeat,
                            )
                        }
                        task.dueDate?.let {
                            val (dueLabel, dueContainer) = when {
                                task.isDone -> Pair(taskDueFmt.format(Date(it)), MaterialTheme.colorScheme.secondaryContainer)
                                overdue -> Pair(taskDueFmt.format(Date(it)), MaterialTheme.colorScheme.errorContainer)
                                dueToday -> Pair(taskDueFmt.format(Date(it)), MaterialTheme.colorScheme.primaryContainer)
                                else -> Pair(taskDueFmt.format(Date(it)), MaterialTheme.colorScheme.secondaryContainer)
                            }
                            com.moltrax.personalnoteapp.ui.components.DhStatusChip(
                                label = dueLabel,
                                container = dueContainer,
                                icon = Icons.Default.Schedule,
                            )
                        }
                        if (task.linkedWorkoutId != null || task.linkedProgramId != null) {
                            com.moltrax.personalnoteapp.ui.components.DhStatusChip(
                                label = stringResource(R.string.task_badge_workout),
                                icon = Icons.Default.FitnessCenter,
                            )
                        }
                        task.categoryNames.take(3).forEach { tag ->
                            com.moltrax.personalnoteapp.ui.components.DhStatusChip(label = tag)
                        }
                        if (task.subtaskCount > 0) {
                            Text(
                                "${task.doneSubtaskCount}/${task.subtaskCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Subtask progress bar (visual only; counts already shown above).
                if (task.subtaskCount > 0 && !task.isDone) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { task.subtaskProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Editable (text) fields of a single set in the completion accordion. */
private data class SetRow(
    val reps: String = "",
    val weight: String = "",
    val durationSec: String = "",
    val durationMin: String = "",
    val steps: String = "",
)

/** Converts an editable set row to the persistent draft model (and vice versa). */
private fun SetRow.toDraftSet() = WorkoutDraftSet(reps, weight, durationSec, durationMin, steps)
private fun WorkoutDraftSet.toSetRow() = SetRow(reps, weight, durationSec, durationMin, steps)

/** Builds initial set rows from the user's workout plan (entered sets/reps/weight). */
private fun initialRowsFor(item: WorkoutCompletionItem): List<SetRow> {
    val planned = item.plannedSets
    val first = planned.firstOrNull()
    val setCount = planned.size.coerceAtLeast(1)
    return when (item.type) {
        ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT -> {
            val reps = first?.reps?.takeIf { it > 0 }?.toString() ?: ""
            val weight = first?.weightKg?.takeIf { it > 0 }?.let { formatKg(it) } ?: ""
            List(setCount) { SetRow(reps = reps, weight = weight) }
        }
        ExerciseType.DURATION -> {
            val durSec = first?.durationSeconds?.takeIf { it > 0 }?.toString() ?: ""
            List(setCount) { SetRow(durationSec = durSec) }
        }
        ExerciseType.CARDIO -> {
            val durMin = first?.durationSeconds?.takeIf { it > 0 }?.let { (it / 60).toString() } ?: ""
            val steps = first?.steps?.takeIf { it > 0 }?.toString() ?: ""
            listOf(SetRow(durationMin = durMin, steps = steps))
        }
    }
}

/** Converts a set row to a [LoggedSet] by type. */
private fun SetRow.toLoggedSet(type: ExerciseType): LoggedSet = when (type) {
    ExerciseType.WEIGHTLIFTING, ExerciseType.BODYWEIGHT ->
        LoggedSet(reps = reps.toIntOrNull() ?: 0, weightKg = weight.replace(',', '.').toDoubleOrNull())
    ExerciseType.DURATION ->
        LoggedSet(reps = 0, durationSeconds = durationSec.toIntOrNull())
    ExerciseType.CARDIO ->
        LoggedSet(reps = 0, durationSeconds = durationMin.toIntOrNull()?.times(60), steps = steps.toIntOrNull())
}

/**
 * Screen shown while completing a workout-linked task: each exercise is an expandable (accordion) card.
 * When a card is expanded, each set of that exercise gets its own "Reps/Weight" (or duration/steps) fields;
 * sets can be added or removed. On confirm, [HomeViewModel.submitWorkoutCompletion] completes the task.
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
private fun WorkoutCompletionSheet(
    request: WorkoutCompletionRequest,
    onDismiss: () -> Unit,
    onConfirm: (List<ActualEntry>) -> Unit,
    onAutosave: (Map<String, List<WorkoutDraftSet>>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Editable set rows per exercise. Pre-filled from the saved draft first (if any), otherwise from the plan
    // — so the user resumes where they left off when the screen is closed and reopened.
    val rowsByExercise = remember(request) {
        mutableStateMapOf<String, SnapshotStateList<SetRow>>().apply {
            request.items.forEach { item ->
                val seeded = request.draft[item.exerciseId]
                    ?.map { it.toSetRow() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: initialRowsFor(item)
                put(item.exerciseId, seeded.toMutableStateList())
            }
        }
    }
    // Automatic draft saving: persisted on every change (with a short delay). Even if the user accidentally
    // closes the page or the app is killed, the entered set/weight data is not lost.
    LaunchedEffect(request) {
        snapshotFlow {
            request.items.associate { item ->
                item.exerciseId to rowsByExercise.getValue(item.exerciseId).map { it.toDraftSet() }
            }
        }.debounce(400).collect { onAutosave(it) }
    }
    // Which card is open at a time (accordion). Default: first exercise open.
    var expandedId by remember(request) { mutableStateOf(request.items.firstOrNull()?.exerciseId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.workout_complete_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.workout_complete_subtitle, request.workoutName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (request.items.isEmpty()) {
                Text(stringResource(R.string.workout_no_exercises), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            request.items.forEach { item ->
                val rows = rowsByExercise.getValue(item.exerciseId)
                ExerciseAccordion(
                    item = item,
                    rows = rows,
                    expanded = expandedId == item.exerciseId,
                    onToggleExpand = { expandedId = if (expandedId == item.exerciseId) null else item.exerciseId },
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onConfirm(
                        request.items.map { item ->
                            val rows = rowsByExercise.getValue(item.exerciseId)
                            ActualEntry(item.exerciseId, rows.map { it.toLoggedSet(item.type) })
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.complete))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

/** Expandable/collapsible card of a single exercise: header (summary) + set rows when open. */
@Composable
private fun ExerciseAccordion(
    item: WorkoutCompletionItem,
    rows: SnapshotStateList<SetRow>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (expanded) BorderStroke(1.dp, AppColors.Accent) else null,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.animateContentSize().padding(14.dp)) {
            // Header row — always visible, expands/collapses on tap.
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.exerciseName, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.set_count_type, rows.size, item.type.label()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = AppColors.Accent,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                rows.forEachIndexed { index, row ->
                    SetEntryRow(
                        index = index,
                        type = item.type,
                        row = row,
                        canRemove = rows.size > 1,
                        onChange = { rows[index] = it },
                        onRemove = { rows.removeAt(index) },
                    )
                    if (index < rows.lastIndex) Spacer(Modifier.height(8.dp))
                }
                // Cardio is a single record so it shows no extra set; other types can add sets.
                if (item.type != ExerciseType.CARDIO) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { rows.add(SetRow()) }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_set))
                    }
                }
            }
        }
    }
}

/** Input fields of a single set in an open card (by type). */
@Composable
private fun SetEntryRow(
    index: Int,
    type: ExerciseType,
    row: SetRow,
    canRemove: Boolean,
    onChange: (SetRow) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${index + 1}.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        when (type) {
            ExerciseType.WEIGHTLIFTING -> {
                CompletionField(row.reps, { onChange(row.copy(reps = it)) }, stringResource(R.string.field_reps), Modifier.weight(1f))
                CompletionField(row.weight, { onChange(row.copy(weight = it)) }, stringResource(R.string.field_kg), Modifier.weight(1f), decimal = true)
            }
            ExerciseType.BODYWEIGHT -> {
                CompletionField(row.reps, { onChange(row.copy(reps = it)) }, stringResource(R.string.field_reps), Modifier.weight(1f))
                CompletionField(row.weight, { onChange(row.copy(weight = it)) }, stringResource(R.string.field_added_kg), Modifier.weight(1f), decimal = true)
            }
            ExerciseType.DURATION -> {
                CompletionField(row.durationSec, { onChange(row.copy(durationSec = it)) }, stringResource(R.string.field_duration_sec), Modifier.weight(1f))
            }
            ExerciseType.CARDIO -> {
                CompletionField(row.durationMin, { onChange(row.copy(durationMin = it)) }, stringResource(R.string.field_duration_min), Modifier.weight(1f))
                CompletionField(row.steps, { onChange(row.copy(steps = it)) }, stringResource(R.string.field_steps_distance), Modifier.weight(1f))
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove_set),
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun CompletionField(
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

private fun formatKg(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

/**
 * Root navigation chrome moved to [com.moltrax.personalnoteapp.ui.shell.DailyHubScaffold].
 * Kept as a no-op for backward compatibility until all callers are migrated.
 */
@Deprecated("Navigation chrome lives in DailyHubScaffold; do not call from screens.")
@Composable
fun BottomNavBar(nav: NavController) {
}
