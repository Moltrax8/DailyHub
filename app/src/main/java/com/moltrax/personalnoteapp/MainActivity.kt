package com.moltrax.personalnoteapp

import android.content.Intent
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
import com.moltrax.personalnoteapp.R
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

    // Routing request from the widget (e.g. '+' button → new task screen).
    private val pendingWidgetAction = mutableStateOf<String?>(null)
    // Id carrying which task to open the screen for on a sport-task completion request.
    private val pendingWidgetTaskId = mutableStateOf<String?>(null)
    // Developer-activity push tap (Phase 7): open this project space.
    private val pendingProjectSpaceId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Supabase email-confirm deep link (dailyhub://auth/callback) just lands
        // in the app — verification already happened server-side. It carries no
        // widget extras, so only read them for non-deep-link launches.
        val isAuthCallback = intent?.data?.scheme == "dailyhub"
        if (!isAuthCallback) {
            pendingWidgetAction.value = intent?.getStringExtra(EXTRA_WIDGET_ACTION)
            pendingWidgetTaskId.value = intent?.getStringExtra(EXTRA_WIDGET_TASK_ID)
            pendingProjectSpaceId.value = intent?.getStringExtra(EXTRA_PROJECT_SPACE_ID)
        }
        enableEdgeToEdge()
        setContent {
            val language by appVm.language.collectAsStateWithLifecycle()
            val isSwitchingLanguage by appVm.isSwitchingLanguage.collectAsStateWithLifecycle()

            // Provide localized context + configuration for the selected language. When the language
            // changes this provider recomputes; LocalContext/LocalConfiguration pick up the new value and all
            // stringResource calls switch to the new language INSTANTLY (without an app restart).
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val localizedContext = remember(language, baseContext) { baseContext.localizedFor(language) }
            val localizedConfig = remember(language, baseConfig) { localizedConfiguration(baseConfig, language) }

            // Hide the "Loading" indicator after a short visual delay once the new language has
            // been applied (composed with fresh resources). At launch isSwitchingLanguage is already false.
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
                AppTheme {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            pendingWidgetAction = pendingWidgetAction.value,
                            pendingWidgetTaskId = pendingWidgetTaskId.value,
                            onWidgetActionConsumed = {
                                pendingWidgetAction.value = null
                                pendingWidgetTaskId.value = null
                            },
                            pendingProjectId = pendingProjectSpaceId.value,
                            onProjectConsumed = { pendingProjectSpaceId.value = null },
                        )
                        // Opt-in auto-update prompt (Phase 9): renders nothing when off.
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

    // Catch a new intent from the widget while the Activity is already open (singleTop).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.scheme == "dailyhub") return // auth callback: nothing to route
        pendingWidgetAction.value = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        pendingWidgetTaskId.value = intent.getStringExtra(EXTRA_WIDGET_TASK_ID)
        intent.getStringExtra(EXTRA_PROJECT_SPACE_ID)?.let { pendingProjectSpaceId.value = it }
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"
        const val EXTRA_WIDGET_TASK_ID = "extra_widget_task_id"
        const val EXTRA_PROJECT_SPACE_ID = "extra_project_space_id"
        const val ACTION_NEW_TASK = "new_task"
        // Completing a sport-linked task from the widget: opens the set/weight entry screen in the app.
        const val ACTION_COMPLETE_WORKOUT = "complete_workout"
    }
}
