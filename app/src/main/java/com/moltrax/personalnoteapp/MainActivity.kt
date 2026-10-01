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
import com.moltrax.personalnoteapp.ui.i18n.localizedConfiguration
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.ui.navigation.AppNavHost
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels()

    // Widget'tan gelen yönlendirme isteği (örn. '+' butonu → yeni görev ekranı).
    private val pendingWidgetAction = mutableStateOf<String?>(null)
    // Spor görevi tamamlama isteğinde hangi görev için ekran açılacağını taşıyan id.
    private val pendingWidgetTaskId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetAction.value = intent?.getStringExtra(EXTRA_WIDGET_ACTION)
        pendingWidgetTaskId.value = intent?.getStringExtra(EXTRA_WIDGET_TASK_ID)
        enableEdgeToEdge()
        setContent {
            val language by appVm.language.collectAsStateWithLifecycle()
            val isSwitchingLanguage by appVm.isSwitchingLanguage.collectAsStateWithLifecycle()

            // Seçili dile göre yerelleştirilmiş context + configuration sağla. Dil değişince bu
            // sağlayıcı yeniden hesaplanır; LocalContext/LocalConfiguration yeni değere geçer ve tüm
            // stringResource çağrıları ANINDA (uygulama yeniden başlamadan) yeni dile döner.
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val localizedContext = remember(language, baseContext) { baseContext.localizedFor(language) }
            val localizedConfig = remember(language, baseConfig) { localizedConfiguration(baseConfig, language) }

            // Yeni dil uygulanıp (yeni kaynaklarla) compose edildikten sonra kısa bir görsel gecikmenin
            // ardından "Yükleniyor" göstergesini kapat. Açılışta isSwitchingLanguage zaten false'tur.
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
                        )
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

    // Activity zaten açıkken (singleTop) widget'tan yeni bir intent gelirse yakala.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetAction.value = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        pendingWidgetTaskId.value = intent.getStringExtra(EXTRA_WIDGET_TASK_ID)
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"
        const val EXTRA_WIDGET_TASK_ID = "extra_widget_task_id"
        const val ACTION_NEW_TASK = "new_task"
        // Spora linkli görevi widget'tan tamamlama: uygulamada set/ağırlık giriş ekranını açar.
        const val ACTION_COMPLETE_WORKOUT = "complete_workout"
    }
}
