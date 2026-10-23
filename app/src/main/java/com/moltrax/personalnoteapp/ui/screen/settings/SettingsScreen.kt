package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.AppViewModel
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSegmentedControl
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhSwitch
import com.moltrax.personalnoteapp.ui.components.DhTonalIcon
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.i18n.AppLanguage
import com.moltrax.personalnoteapp.ui.navigation.Home
import kotlinx.coroutines.launch

private const val URL_PRIVACY = "https://github.com/Moltrax8/DailyHub/blob/main/PRIVACY.md"
private const val URL_TERMS = "https://github.com/Moltrax8/DailyHub/blob/main/TERMS.md"
private const val URL_RELEASES = "https://github.com/Moltrax8/DailyHub/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    appVm: AppViewModel = hiltViewModel(),
) {
    val language by appVm.language.collectAsStateWithLifecycle()
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val reminderMinutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val systemAlerts by vm.systemAlertsEnabled.collectAsStateWithLifecycle()
    val autoUpdate by vm.autoUpdate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val updateVm: com.moltrax.personalnoteapp.ui.UpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateCheck?>(null) }

    Scaffold(
        topBar = {
            DhTopBar(
                title = stringResource(R.string.settings_title),
                onBack = { nav.navigate(Home) { launchSingleTop = true; popUpTo<Home> { inclusive = false } } },
                backContentDescription = stringResource(R.string.action_back),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DhSection(title = stringResource(R.string.settings_section_appearance)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_section_appearance),
                    supporting = stringResource(R.string.settings_appearance_desc),
                    leading = { DhTonalIcon(Icons.Default.Palette, contentDescription = null) },
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val options = listOf(
                        stringResource(R.string.settings_appearance_system),
                        stringResource(R.string.settings_appearance_light),
                        stringResource(R.string.settings_appearance_dark),
                    )
                    val selected = listOf("system", "light", "dark").indexOf(themeMode).coerceAtLeast(0)
                    DhSegmentedControl(
                        options = options,
                        selectedIndex = selected,
                        onSelect = { index -> appVm.setThemeMode(listOf("system", "light", "dark")[index]) },
                    )
                }
            }

            DhSection(title = stringResource(R.string.settings_section_language)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhTonalIcon(Icons.Default.Language, contentDescription = null)
                    LanguageSelector(
                        current = language,
                        onSelect = { appVm.setLanguage(it) },
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }

            DhSection(title = stringResource(R.string.settings_section_notifications)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_task_reminders),
                    supporting = stringResource(R.string.settings_task_reminders_desc),
                    leading = { DhTonalIcon(Icons.Default.Notifications, contentDescription = null) },
                    trailing = {
                        DhSwitch(checked = systemAlerts, onCheckedChange = { vm.setSystemAlertsEnabled(it) })
                    },
                    onClick = { vm.setSystemAlertsEnabled(!systemAlerts) },
                )
                DhDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_default_alert),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (systemAlerts) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60, 120, 1440).forEach { minutes ->
                            FilterChip(
                                selected = reminderMinutes == minutes,
                                onClick = { vm.setReminderMinutes(minutes) },
                                label = { Text(formatLead(minutes)) },
                                enabled = systemAlerts,
                            )
                        }
                    }
                }
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_notif_permission),
                    supporting = stringResource(
                        if (vm.areNotificationsEnabled()) R.string.settings_notif_status_on
                        else R.string.settings_notif_status_off
                    ),
                    leading = { DhTonalIcon(Icons.Default.Notifications, contentDescription = null) },
                    onClick = { vm.openNotificationSettings() },
                )
                if (!vm.canScheduleExactAlarms()) {
                    DhDivider()
                    DhSettingsRow(
                        title = stringResource(R.string.settings_exact_alarm_title),
                        supporting = stringResource(R.string.settings_exact_alarm_desc),
                        leading = { DhTonalIcon(Icons.Default.Alarm, contentDescription = null) },
                        onClick = { vm.openExactAlarmSettings() },
                    )
                }
            }

            DhSection(title = stringResource(R.string.settings_section_updates)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_auto_update),
                    supporting = stringResource(R.string.settings_auto_update_desc),
                    leading = { DhTonalIcon(Icons.Default.SystemUpdate, contentDescription = null) },
                    trailing = { DhSwitch(checked = autoUpdate, onCheckedChange = vm::setAutoUpdate) },
                    onClick = { vm.setAutoUpdate(!autoUpdate) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_check_updates),
                    supporting = when (val result = checkResult) {
                        null -> if (checking) stringResource(R.string.settings_checking) else null
                        UpdateCheck.UpToDate -> stringResource(R.string.settings_up_to_date)
                        UpdateCheck.Unavailable -> stringResource(R.string.settings_update_unavailable)
                        is UpdateCheck.Available -> stringResource(R.string.settings_update_available, result.release.versionName)
                    },
                    leading = { DhTonalIcon(Icons.Default.SystemUpdate, contentDescription = null) },
                    enabled = !checking,
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                checkResult = vm.checkForUpdatesNow()
                                (checkResult as? UpdateCheck.Available)?.let { updateVm.offerManual(it.release) }
                                checking = false
                            }
                        }
                    },
                )
            }

            DhSection(title = stringResource(R.string.settings_section_about)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_about_version),
                    supporting = stringResource(
                        R.string.settings_about_version_value,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    leading = { DhTonalIcon(Icons.Default.Info, contentDescription = null) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_privacy),
                    leading = { DhTonalIcon(Icons.Default.Info, contentDescription = null) },
                    onClick = { openUrl(context, URL_PRIVACY) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_terms),
                    leading = { DhTonalIcon(Icons.Default.Info, contentDescription = null) },
                    onClick = { openUrl(context, URL_TERMS) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_releases),
                    leading = { DhTonalIcon(Icons.Default.SystemUpdate, contentDescription = null) },
                    onClick = { openUrl(context, URL_RELEASES) },
                )
            }
        }
    }
}

@Composable
private fun formatLead(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.settings_reminder_min, minutes)
    else stringResource(R.string.settings_reminder_hour, minutes / 60)

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private tailrec fun Context.findActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Settings requires an Activity")
}

@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val codes = AppLanguage.entries.map { it.code }
    val names = AppLanguage.entries.map { it.nativeName }
    val selected = codes.indexOf(current).let { if (it < 0) 0 else it }
    DhSegmentedControl(options = names, selectedIndex = selected, onSelect = { onSelect(codes[it]) }, modifier = modifier)
}
