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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.lifecycleScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-widget filter screen opened via the gear button in the widget (Phase 2).
 * Runs transparent in a separate task (manifest: taskAffinity="",
 * singleInstance, excludeFromRecents); confirm/dismiss returns to home.
 *
 * Each widget instance keeps its own [WidgetFilter] in Glance state: title,
 * tag multi-select (ANY-match, or ALL-match via the toggle), show-done switch
 * and result limit. Saving clears any legacy per-task ID selection.
 *
 * Note: Mandatory setup was removed (no android:configure in the
 * appwidget-provider); a freshly dropped widget shows all open tasks.
 */
@AndroidEntryPoint
class TaskWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var taskRepo: TaskRepository
    @Inject lateinit var categoryRepo: CategoryRepository

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

        lifecycleScope.launch {
            val initial = loadExistingFilter()
            val tags = availableTags()
            setContent {
                AppTheme {
                    ConfigDialog(
                        allTags = tags,
                        initial = initial,
                        onSave = ::applyFilter,
                        onDismiss = ::dismiss,
                    )
                }
            }
        }
    }

    /** Close the screen and return directly to the home screen by removing its own task. */
    private fun dismiss() = finishAndRemoveTask()

    private suspend fun loadExistingFilter(): WidgetFilter = runCatching {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        WidgetFilter.load(getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId))
    }.getOrNull() ?: WidgetFilter()

    /** Permanent categories + tags currently used by open tasks (same source as Home chips). */
    private suspend fun availableTags(): List<String> = runCatching {
        val permanent = categoryRepo.observeAll().first()
            .filter { it.isPermanent }.map { it.name }
        val used = taskRepo.observeAll().first()
            .filter { !it.isDone }.flatMap { it.categoryNames }
        (permanent + used).distinct().sortedBy { it.lowercase() }
    }.getOrDefault(emptyList())

    private fun applyFilter(filter: WidgetFilter) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TaskWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@TaskWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId,
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    // The tag filter replaces the legacy per-task ID selection.
                    remove(TaskWidget.SELECTED_TASK_IDS)
                    WidgetFilter.save(this, filter)
                }
            }

            TaskWidget().update(this@TaskWidgetConfigActivity, glanceId)
            dismiss()
        }
    }
}

@Composable
private fun ConfigDialog(
    allTags: List<String>,
    initial: WidgetFilter,
    onSave: (WidgetFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(initial.categoryNames) } }
    var showDone by remember { mutableStateOf(initial.showDone) }
    var matchAll by remember { mutableStateOf(initial.matchAll) }
    var limit by remember { mutableIntStateOf(initial.limit) }

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

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.widget_filter_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = stringResource(R.string.widget_filter_tags),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (allTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.widget_no_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(allTags) { tag ->
                            TagOption(
                                name = tag,
                                checked = tags.any { it.equals(tag, ignoreCase = true) },
                                onToggle = {
                                    val existing = tags.firstOrNull { it.equals(tag, ignoreCase = true) }
                                    if (existing != null) tags.remove(existing) else tags.add(tag)
                                },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showDone, onCheckedChange = { showDone = it })
                    Text(
                        text = stringResource(R.string.widget_filter_show_done),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = matchAll, onCheckedChange = { matchAll = it })
                    Text(
                        text = stringResource(R.string.widget_filter_match_all),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.widget_filter_limit),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { limit = (limit - 1).coerceAtLeast(1) },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.Remove, contentDescription = null) }
                    Text(text = limit.toString(), style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = { limit = (limit + 1).coerceAtMost(WidgetFilter.MAX_LIMIT) },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.Add, contentDescription = null) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        onSave(WidgetFilter(title.trim(), tags.toSet(), showDone, limit, matchAll))
                    }) { Text(stringResource(R.string.action_save)) }
                    OutlinedButton(onClick = { onSave(WidgetFilter()) }) {
                        Text(stringResource(R.string.widget_filter_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagOption(name: String, checked: Boolean, onToggle: () -> Unit) {
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
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
