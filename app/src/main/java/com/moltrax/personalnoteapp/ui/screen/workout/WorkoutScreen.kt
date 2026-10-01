package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.json.WorkoutJsonCodec
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.ui.navigation.WorkoutDetail
import com.moltrax.personalnoteapp.ui.screen.home.BottomNavBar

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
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.workout_new_program)) },
            text = {
                OutlinedTextField(newGroupName, { newGroupName = it }, label = { Text(stringResource(R.string.workout_program_name)) },
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newGroupName.isNotBlank()) { vm.addGroup(newGroupName); newGroupName = ""; showAddDialog = false }
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_my_workouts)) },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                        Icon(Icons.Filled.Upload, stringResource(R.string.cd_import))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.workout_new_program)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomNavBar(nav) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (groups.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.workout_no_programs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
    Card(onClick = onTap, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.workout_count, group.workouts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreHoriz, stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_export_json)) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
