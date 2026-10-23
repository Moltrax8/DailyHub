package com.moltrax.personalnoteapp.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhSectionHeader
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.navigation.TaskDetail
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    nav: NavController,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notifBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var notifEnabled by rememberSaveable { mutableStateOf(isNotificationsEnabled(context)) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var incompleteTask by remember { mutableStateOf<Task?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    val completedMessage = stringResource(R.string.task_completed)
    val undoLabel = stringResource(R.string.undo)
    fun toggle(task: Task) {
        vm.toggleDone(task) { before ->
            scope.launch {
                if (snackbars.showSnackbar(completedMessage, undoLabel) == SnackbarResult.ActionPerformed) {
                    vm.undoCompletion(before)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifEnabled = it
        notifBannerDismissed = true
    }

    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                today = LocalDate.now()
                notifEnabled = isNotificationsEnabled(context)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    var entries by remember(state.tasks, today) { mutableStateOf(buildTaskEntries(state.tasks, startOfToday, startOfTomorrow)) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = entries.indexOfFirst { it.key == from.key }
        val toIndex = entries.indexOfFirst { it.key == to.key }
        val fromRow = entries.getOrNull(fromIndex) as? TaskListEntry.Row
        val toRow = entries.getOrNull(toIndex) as? TaskListEntry.Row
        if (fromRow != null && toRow != null && taskGroupIndex(fromRow.task, startOfToday, startOfTomorrow) ==
            taskGroupIndex(toRow.task, startOfToday, startOfTomorrow)) {
            entries = entries.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        topBar = { DhTopBar(title = stringResource(R.string.home_title), actions = {
            IconButton(onClick = { showCategories = true }) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.home_manage_categories))
            }
        }) },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(TaskDetail("new")) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_new_task))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!notifEnabled && !notifBannerDismissed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null)
                        Text(
                            stringResource(R.string.notif_permission_rationale),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text(stringResource(R.string.notif_permission_open_settings)) }
                        IconButton(onClick = { notifBannerDismissed = true }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_dismiss))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                        }
                    }
                },
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.selectedCategories.isEmpty(),
                    onClick = vm::clearCategories,
                    label = { Text(stringResource(R.string.categories_all)) },
                )
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategories.contains(category.name),
                        onClick = { vm.toggleCategory(category.name) },
                        label = { Text(category.name) },
                    )
                }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskStatus.ALL to R.string.filter_all, TaskStatus.ACTIVE to R.string.filter_active,
                    TaskStatus.DONE to R.string.filter_done).forEach { (status, label) ->
                    FilterChip(selected = state.status == status, onClick = { vm.setStatus(status) },
                        label = { Text(stringResource(label)) })
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DhEmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(if (state.status == TaskStatus.DONE) R.string.empty_done else R.string.empty_all),
                        description = stringResource(
                            if (state.status == TaskStatus.DONE) R.string.tasks_empty_done_desc else R.string.tasks_empty_desc
                        ),
                        actionLabel = stringResource(R.string.home_new_task),
                        onAction = { nav.navigate(TaskDetail("new")) },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entries.forEach { entry ->
                        when (entry) {
                            is TaskListEntry.Header -> item(key = entry.key) {
                                DhSectionHeader(title = stringResource(entry.titleRes))
                            }
                            is TaskListEntry.Row -> item(key = entry.task.id) {
                                ReorderableItem(reorderState, key = entry.task.id) {
                                    TaskRow(
                                        task = entry.task,
                                        onOpen = { nav.navigate(TaskDetail(entry.task.id)) },
                                        onToggle = {
                                            if (!entry.task.isDone && entry.task.doneSubtaskCount < entry.task.subtaskCount)
                                                incompleteTask = entry.task
                                            else toggle(entry.task)
                                        },
                                        dragHandle = {
                                            IconButton(onClick = {}, modifier = Modifier.draggableHandle(onDragStopped = {
                                                vm.reorder(entries.filterIsInstance<TaskListEntry.Row>().map { it.task.id })
                                            })) {
                                                Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.cd_drag_reorder))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    incompleteTask?.let { task ->
        AlertDialog(onDismissRequest = { incompleteTask = null },
            title = { Text(stringResource(R.string.subtasks_incomplete_title)) },
            text = { Text(stringResource(R.string.subtasks_incomplete_msg, task.subtaskCount - task.doneSubtaskCount)) },
            confirmButton = { TextButton(onClick = { incompleteTask = null; toggle(task) }) {
                Text(stringResource(R.string.complete_anyway))
            } }, dismissButton = { TextButton(onClick = { incompleteTask = null }) {
                Text(stringResource(R.string.action_cancel))
            } })
    }
    if (showCategories) CategoryManager(state.categories, vm, onDismiss = { showCategories = false })
}

@Composable
private fun TaskRow(task: Task, onOpen: () -> Unit, onToggle: () -> Unit, dragHandle: @Composable () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dueText = task.dueDate?.let { millis ->
        val fmt = SimpleDateFormat("d MMM, HH:mm", locale)
        fmt.format(Date(millis))
    }
    DhCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    dueText?.let { add(it) }
                    if (task.subtaskCount > 0) add("${task.doneSubtaskCount}/${task.subtaskCount}")
                    addAll(task.categoryNames.sorted())
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.isRecurring) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = stringResource(R.string.cd_recurring),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dragHandle()
        }
        if (!task.notes.isNullOrBlank()) {
            Text(
                task.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private sealed interface TaskListEntry {
    val key: String
    data class Header(override val key: String, val titleRes: Int) : TaskListEntry
    data class Row(val task: Task) : TaskListEntry { override val key get() = task.id }
}

private fun taskGroupIndex(task: Task, startOfToday: Long, startOfTomorrow: Long): Int {
    val due = task.dueDate ?: return 3
    if (due < startOfToday) return 0
    if (due < startOfTomorrow) return 1
    return 2
}

private fun buildTaskEntries(
    tasks: List<Task>,
    startOfToday: Long,
    startOfTomorrow: Long,
): List<TaskListEntry> {
    val groups = List(4) { mutableListOf<Task>() }
    tasks.forEach { groups[taskGroupIndex(it, startOfToday, startOfTomorrow)].add(it) }
    val titles = listOf(
        R.string.tasks_group_overdue,
        R.string.tasks_group_today,
        R.string.tasks_group_upcoming,
        R.string.tasks_group_no_date,
    )
    val keys = listOf("g-overdue", "g-today", "g-upcoming", "g-nodate")
    return buildList {
        groups.forEachIndexed { index, list ->
            if (list.isNotEmpty()) {
                add(TaskListEntry.Header(keys[index], titles[index]))
                list.sortedBy { it.isDone }.forEach {
                    add(TaskListEntry.Row(it))
                }
            }
        }
    }
}

@Composable
private fun CategoryManager(categories: List<com.moltrax.personalnoteapp.domain.model.Category>, vm: HomeViewModel, onDismiss: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.home_manage_categories)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(categories, key = { it.name }) { category ->
                    var name by remember(category.name) { mutableStateOf(category.name) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = name.isNotBlank() && name != category.name,
                            onClick = { vm.renameCategory(category.name, name.trim()) }) { Text(stringResource(R.string.action_save)) }
                        IconButton(onClick = { deleting = category.name }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
                item {
                    OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.new_permanent_category)) })
                    TextButton(enabled = newName.isNotBlank(), onClick = { vm.createCategory(newName.trim()); newName = "" }) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } })
    deleting?.let { name ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_msg, name)) },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(name); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

private fun isNotificationsEnabled(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
