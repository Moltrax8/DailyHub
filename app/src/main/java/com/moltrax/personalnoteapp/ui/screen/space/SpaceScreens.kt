package com.moltrax.personalnoteapp.ui.screen.space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R

private const val TAB_NOTES = 0
private const val TAB_TASKS = 1
private const val TAB_LINKS = 2

/**
 * One Duo hub: Notes | Tasks | Links tabs + members row (Phase 5 MVP).
 * Pulls on open; refresh button re-pulls (Realtime push arrives in Phase 9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuoHubScreen(spaceId: String, nav: NavController, vm: SpaceViewModel = hiltViewModel()) {
    val state by vm.detail.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(TAB_NOTES) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(spaceId) { vm.openSpace(spaceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.space?.name ?: stringResource(R.string.spaces_duo)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(spaceId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_NOTES, onClick = { tab = TAB_NOTES }, text = { Text(stringResource(R.string.spaces_notes)) })
                Tab(selected = tab == TAB_TASKS, onClick = { tab = TAB_TASKS }, text = { Text(stringResource(R.string.spaces_tasks)) })
                Tab(selected = tab == TAB_LINKS, onClick = { tab = TAB_LINKS }, text = { Text(stringResource(R.string.spaces_links)) })
            }
            if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            when (tab) {
                TAB_NOTES -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.notes.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_notes)) }
                    }
                    items(state.notes, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        note.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.spaces_untitled),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { vm.deleteNote(spaceId, note.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                    }
                                }
                                note.bodyMd?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                TAB_TASKS -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.tasks.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_tasks)) }
                    }
                    items(state.tasks, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = task.isDone,
                                    onCheckedChange = { vm.toggleSharedTask(spaceId, task) },
                                )
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.deleteSharedTask(spaceId, task.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.links.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_links)) }
                    }
                    items(state.links, key = { it.id }) { link ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        link.title?.takeIf { it.isNotBlank() } ?: link.url,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (!link.title.isNullOrBlank()) {
                                        Text(
                                            link.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.deleteLink(spaceId, link.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        when (tab) {
            TAB_NOTES -> NoteDialog(onDismiss = { showAdd = false }) { title, body ->
                vm.addNote(spaceId, title, body) { showAdd = false }
            }
            TAB_TASKS -> TaskDialog(onDismiss = { showAdd = false }) { title ->
                vm.addSharedTask(spaceId, title) { showAdd = false }
            }
            else -> LinkDialog(onDismiss = { showAdd = false }) { url, title ->
                vm.addLink(spaceId, url, title) { showAdd = false }
            }
        }
    }
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.spaces_note_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.spaces_note_body)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(title.takeIf { it.isNotBlank() }, body.takeIf { it.isNotBlank() })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_task)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.spaces_link_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = url.isNotBlank() && !valid,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.spaces_link_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), title.takeIf { it.isNotBlank() }) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Row for one space in lists (name + type label). */
@Composable
fun SpaceRow(spaceName: String, typeLabel: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spaceName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
