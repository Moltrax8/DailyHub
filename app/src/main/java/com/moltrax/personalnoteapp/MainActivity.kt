package com.moltrax.personalnoteapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moltrax.personalnoteapp.ui.AppViewModel
import com.moltrax.personalnoteapp.ui.components.UpdatePrompt
import com.moltrax.personalnoteapp.ui.i18n.localizedConfiguration
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.ui.navigation.AppNavHost
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels()

    // Routing request from the widget ("+" button → new task screen).
    private val pendingWidgetAction = mutableStateOf<String?>(null)
    private val pendingWidgetTaskId = mutableStateOf<String?>(null)
    private val pendingWidgetTick = mutableStateOf(0)
    // Reminder tap: open this task once.
    private val pendingReminderTaskId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetAction.value = intent?.getStringExtra(EXTRA_WIDGET_ACTION)
        pendingWidgetTaskId.value = intent?.getStringExtra(EXTRA_WIDGET_TASK_ID)
        if (pendingWidgetAction.value != null) pendingWidgetTick.value++
        parseReminderTaskId(intent)?.let { pendingReminderTaskId.value = it }
        enableEdgeToEdge()
        setContent {
            val language by appVm.language.collectAsStateWithLifecycle()
            val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
            val isSwitchingLanguage by appVm.isSwitchingLanguage.collectAsStateWithLifecycle()

            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val localizedContext = remember(language, baseContext) { baseContext.localizedFor(language) }
            val localizedConfig = remember(language, baseConfig) { localizedConfiguration(baseConfig, language) }

            LaunchedEffect(language) {
                if (appVm.isSwitchingLanguage.value) {
                    kotlinx.coroutines.delay(300)
                    appVm.onLanguageApplied()
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
            ) {
                AppTheme(themeMode = themeMode) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            pendingWidgetAction = pendingWidgetAction.value,
                            pendingWidgetTaskId = pendingWidgetTaskId.value,
                            pendingWidgetTick = pendingWidgetTick.value,
                            onWidgetActionConsumed = {
                                pendingWidgetAction.value = null
                                pendingWidgetTaskId.value = null
                            },
                            pendingReminderTaskId = pendingReminderTaskId.value,
                            onReminderConsumed = { pendingReminderTaskId.value = null },
                        )
                        UpdatePrompt()
                        if (isSwitchingLanguage) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.loading),
                                    modifier = Modifier.padding(top = 16.dp),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseReminderTaskId(intent)?.let { pendingReminderTaskId.value = it }
        pendingWidgetAction.value = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        pendingWidgetTaskId.value = intent.getStringExtra(EXTRA_WIDGET_TASK_ID)
        if (pendingWidgetAction.value != null) pendingWidgetTick.value++
    }

    /** Extracts the task id from personalnoteapp://reminder_tap/<taskId>. */
    private fun parseReminderTaskId(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "personalnoteapp") return null
        if (data.host != "reminder_tap") return null
        return data.lastPathSegment?.takeIf { it.isNotBlank() }?.let {
            runCatching { Uri.decode(it) }.getOrDefault(it)
        }
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"
        const val EXTRA_WIDGET_TASK_ID = "extra_widget_task_id"
        const val ACTION_NEW_TASK = "new_task"
    }
}
