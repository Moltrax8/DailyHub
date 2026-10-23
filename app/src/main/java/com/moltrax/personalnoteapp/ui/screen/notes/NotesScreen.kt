package com.moltrax.personalnoteapp.ui.screen.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Note
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhSearchField
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.navigation.NoteDetail
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun NotesScreen(
    nav: NavController,
    vm: NotesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DhTopBar(title = stringResource(R.string.notes_title)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(NoteDetail("new")) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.note_new))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DhSearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                onClear = { vm.setQuery("") },
                placeholder = stringResource(R.string.notes_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (state.loaded && state.notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DhEmptyState(
                        icon = Icons.Default.Description,
                        title = stringResource(if (state.query.isBlank()) R.string.notes_empty_title else R.string.notes_no_results),
                        description = stringResource(R.string.notes_empty_desc),
                        actionLabel = stringResource(R.string.note_new),
                        onAction = { nav.navigate(NoteDetail("new")) },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { nav.navigate(NoteDetail(note.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = SimpleDateFormat("d MMM yyyy, HH:mm", locale).format(Date(note.updatedAt))
    DhCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title.ifBlank { stringResource(R.string.note_untitled) },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.note_pinned),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (note.body.isNotBlank()) {
            Text(
                note.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            dateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
