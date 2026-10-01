package com.moltrax.personalnoteapp.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Multi-task selection screen opened via the gear button in the widget. It runs transparent and in a
 * separate task from the main app (manifest: taskAffinity="", singleInstance, excludeFromRecents). Taps therefore
 * do not open MainActivity; it looks like a lightweight dialog. On confirm or dismiss,
 * it removes its own task via [finishAndRemoveTask] and returns directly to the device home screen.
 *
 * Note: Mandatory setup was removed (no android:configure in the appwidget-provider); when the widget is
 * dropped on the home screen this screen does NOT open and shows all tasks by default.
 */
@AndroidEntryPoint
class TaskWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var taskRepo: TaskRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            dismiss()
            return
        }

        // Set up the dialog after loading the existing selection (for later editing).
        lifecycleScope.launch {
            val initial = loadExistingSelection()
            setContent {
                AppTheme {
                    ConfigDialog(
                        taskRepo = taskRepo,
                        initialSelected = initial,
                        onSave = ::applySelection,
                        onDismiss = ::dismiss,
                    )
                }
            }
        }
    }

    /** Close the screen and return directly to the home screen by removing its own task. */
    private fun dismiss() = finishAndRemoveTask()

    private suspend fun loadExistingSelection(): Set<String> = runCatching {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)[TaskWidget.SELECTED_TASK_IDS]
    }.getOrNull() ?: emptySet()

    private fun applySelection(ids: Set<String>) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TaskWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@TaskWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId,
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    // Empty selection = default (all tasks). We remove the key entirely.
                    if (ids.isEmpty()) remove(TaskWidget.SELECTED_TASK_IDS)
                    else this[TaskWidget.SELECTED_TASK_IDS] = ids
                }
            }

            TaskWidget().update(this@TaskWidgetConfigActivity, glanceId)
            dismiss()
        }
    }
}

@Composable
private fun ConfigDialog(
    taskRepo: TaskRepository,
    initialSelected: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tasks by taskRepo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val openTasks = tasks.filter { !it.isDone }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initialSelected) } }

    // Transparent dim (scrim): closes on outside tap → returns to the home screen.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // Consume touches on the card so they don't reach the scrim and close it.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.widget_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.widget_config_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSave(selected.toSet()) }) { Text(stringResource(R.string.action_save)) }
                    OutlinedButton(onClick = { onSave(emptySet()) }) { Text(stringResource(R.string.widget_show_all)) }
                }

                if (openTasks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.widget_no_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(openTasks) { task ->
                            TaskOption(
                                task = task,
                                checked = task.id in selected,
                                onToggle = {
                                    if (task.id in selected) selected.remove(task.id)
                                    else selected.add(task.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskOption(task: Task, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
