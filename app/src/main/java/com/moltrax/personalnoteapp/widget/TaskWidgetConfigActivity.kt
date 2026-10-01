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
 * Widget'taki çark butonuyla açılan, çoklu görev seçme ekranı. Saydam ve ana uygulamadan ayrı bir
 * task'ta çalışır (manifest: taskAffinity="", singleInstance, excludeFromRecents). Bu yüzden tıklama
 * MainActivity'yi açmaz; hafif bir dialog gibi görünür. Onaylandığında veya kapatıldığında
 * [finishAndRemoveTask] ile kendi task'ını kaldırır ve doğrudan cihazın ana ekranına dönülür.
 *
 * Not: Zorunlu kurulum kaldırıldı (appwidget-provider'da android:configure yok); widget ana ekrana
 * bırakıldığında bu ekran AÇILMAZ, varsayılan olarak tüm görevleri gösterir.
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

        // Mevcut seçimi (sonradan düzenleme için) yükledikten sonra dialog'u kuruyoruz.
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

    /** Ekranı kapat ve kendi task'ını kaldırarak doğrudan ana ekrana dön. */
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
                    // Boş seçim = varsayılan (tüm görevler). Anahtarı tamamen kaldırıyoruz.
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

    // Saydam karartma (scrim): dışına dokununca kapanır → ana ekrana döner.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // Kart üzerindeki dokunuşları yutarak (consume) scrim'e geçmesini ve kapanmasını engelle.
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
