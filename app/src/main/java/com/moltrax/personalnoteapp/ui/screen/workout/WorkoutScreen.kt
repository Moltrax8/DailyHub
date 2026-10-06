package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.json.WorkoutJsonCodec
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhConfirmDialog
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhFab
import com.moltrax.personalnoteapp.ui.components.DhFormDialog
import com.moltrax.personalnoteapp.ui.components.DhTextField
import com.moltrax.personalnoteapp.ui.components.DhTonalIcon
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.navigation.WorkoutDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(nav: NavController, vm: WorkoutViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notice by vm.fileNotice.collectAsStateWithLifecycle()
    // Pending export: program + suggested file name (written when the picker returns).
    var pendingExport by remember { mutableStateOf<Pair<WorkoutGroup, String>?>(null) }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(context.getString(it.messageRes))
            vm.consumeFileNotice()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Cancellation (null URI) is silently ignored.
        if (uri != null) vm.importProgramFile(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) vm.exportProgramToUri(pending.first, uri)
    }

    if (showAddDialog) {
        DhFormDialog(
            title = stringResource(R.string.workout_new_program),
            onDismiss = { showAddDialog = false; newGroupName = "" },
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                if (newGroupName.isNotBlank()) {
                    vm.addGroup(newGroupName.trim())
                    newGroupName = ""
                    showAddDialog = false
                }
            },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmEnabled = newGroupName.isNotBlank(),
            modifier = Modifier.imePadding(),
            content = {
                DhTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = stringResource(R.string.workout_program_name),
                    singleLine = true,
                )
            },
        )
    }

    Scaffold(
        topBar = {
            DhTopBar(
                title = stringResource(R.string.workout_my_workouts),
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                        Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.cd_import))
                    }
                },
            )
        },
        floatingActionButton = {
            DhFab(
                onClick = { showAddDialog = true },
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.workout_new_program)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                DhEmptyState(
                    icon = Icons.Filled.FitnessCenter,
                    title = stringResource(R.string.workout_no_programs),
                    description = stringResource(R.string.workout_empty_desc),
                    actionLabel = stringResource(R.string.workout_new_program),
                    onAction = { showAddDialog = true },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    GroupCard(
                        group,
                        onTap = { nav.navigate(WorkoutDetail(group.id)) },
                        onExport = {
                            val filename = WorkoutJsonCodec.programFilename(group.name)
                            pendingExport = group to filename
                            exportLauncher.launch(filename)
                        },
                        onDelete = { vm.deleteGroup(group.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: WorkoutGroup,
    onTap: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val exerciseCount = group.workouts.sumOf { it.exercises.size }
    val totalSets = group.workouts.sumOf { w -> w.exercises.sumOf { it.plannedSets.size } }
    DhCard(onClick = onTap) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DhTonalIcon(Icons.Filled.FitnessCenter, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.workout_count, group.workouts.size) +
                        " · " + stringResource(R.string.workout_exercise_count, exerciseCount) +
                        " · " + stringResource(R.string.workout_set_count, totalSets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_export_json)) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        }
        Button(
            onClick = onTap,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.workout_start), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (showDelete) {
        DhConfirmDialog(
            title = stringResource(R.string.workout_delete_title),
            message = stringResource(R.string.workout_delete_confirm, group.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { showDelete = false; onDelete() },
            onDismiss = { showDelete = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}
