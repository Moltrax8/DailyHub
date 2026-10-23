package com.moltrax.personalnoteapp.ui.screen.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.components.DhTopBar

@Composable
fun NoteDetailScreen(nav: NavController, noteId: String, vm: NoteDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(noteId) { vm.load(noteId) }
    if (state.missing) {
        AlertDialog(onDismissRequest = { nav.popBackStack() },
            title = { Text(stringResource(R.string.note_missing)) },
            confirmButton = { TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_back)) } })
    }
    Scaffold(topBar = {
        DhTopBar(title = stringResource(if (noteId == "new") R.string.note_new else R.string.note_edit),
            onBack = { nav.popBackStack() }, backContentDescription = stringResource(R.string.action_back),
            actions = {
                IconToggleButton(checked = state.note.isPinned, onCheckedChange = { vm.edit(pinned = it) }) {
                    Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.note_pin))
                }
                if (noteId != "new") IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
                TextButton(onClick = { vm.save { nav.popBackStack() } },
                    enabled = state.loaded && !state.saving &&
                        (state.note.title.isNotBlank() || state.note.body.isNotBlank())) {
                    Text(stringResource(R.string.action_save))
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.note.title, onValueChange = { vm.edit(title = it) },
                label = { Text(stringResource(R.string.task_title_field)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.note.body, onValueChange = { vm.edit(body = it) },
                label = { Text(stringResource(R.string.note_body)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.note_delete_title)) },
        text = { Text(stringResource(R.string.note_delete_message)) },
        confirmButton = { TextButton(onClick = { vm.delete { nav.popBackStack() } }) {
            Text(stringResource(R.string.action_delete))
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) {
            Text(stringResource(R.string.action_cancel))
        } })
}
