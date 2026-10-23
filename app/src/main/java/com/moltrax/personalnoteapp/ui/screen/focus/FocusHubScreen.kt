package com.moltrax.personalnoteapp.ui.screen.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.navigation.FocusTimer
import com.moltrax.personalnoteapp.ui.navigation.TaskDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FocusHubViewModel @Inject constructor(repository: TaskRepository) : ViewModel() {
    val tasks = repository.observeAll().map { list -> list.filterNot { it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun FocusHubScreen(nav: NavController, vm: FocusHubViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    Scaffold(topBar = { DhTopBar(title = stringResource(R.string.focus_title)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.focus_pick_task), style = MaterialTheme.typography.bodyLarge) }
            if (tasks.isEmpty()) item {
                DhEmptyState(icon = Icons.Default.Timer, title = stringResource(R.string.empty_active),
                    description = stringResource(R.string.tasks_empty_desc),
                    actionLabel = stringResource(R.string.home_new_task),
                    onAction = { nav.navigate(TaskDetail()) })
            }
            items(tasks, key = { it.id }) { task ->
                DhCard(onClick = { nav.navigate(FocusTimer(task.id)) }) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
