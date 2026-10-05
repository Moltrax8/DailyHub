package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.AppViewModel
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhSectionHeader
import com.moltrax.personalnoteapp.ui.components.DhSegmentedControl
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.i18n.AppLanguage
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.theme.DhThemeMode

// Meaningful reminder presets (minutes)
private val reminderPresets = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 360, 720, 1440)

private fun reminderLabel(context: Context, m: Int): String = when {
    m < 60 -> context.getString(R.string.reminder_minutes_before, m)
    m % 60 == 0 -> context.getString(R.string.reminder_hours_before, m / 60)
    else -> context.getString(R.string.reminder_hours_minutes_before, m / 60, m % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val reminderMinutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val systemAlerts by vm.systemAlertsEnabled.collectAsStateWithLifecycle()
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var exactAlarmGranted by remember { mutableStateOf(vm.canScheduleExactAlarms()) }
    var notifEnabled by remember { mutableStateOf(vm.areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = vm.canScheduleExactAlarms()
                notifEnabled = vm.areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appVm: AppViewModel = hiltViewModel(context.findActivity())
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // --- Appearance ---
            SettingsGroup(title = stringResource(R.string.settings_section_appearance), icon = Icons.Filled.SettingsSuggest) {
                Text(
                    stringResource(R.string.settings_appearance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val options = listOf(
                    stringResource(R.string.settings_appearance_system),
                    stringResource(R.string.settings_appearance_light),
                    stringResource(R.string.settings_appearance_dark),
                )
                val selected = when (themeMode) {
                    DhThemeMode.LIGHT -> 1
                    DhThemeMode.DARK -> 2
                    else -> 0
                }
                DhSegmentedControl(
                    options = options,
                    selectedIndex = selected,
                    onSelect = {
                        appVm.setThemeMode(
                            when (it) {
                                1 -> DhThemeMode.LIGHT
                                2 -> DhThemeMode.DARK
                                else -> DhThemeMode.SYSTEM
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // --- Language ---
            SettingsGroup(title = stringResource(R.string.settings_section_language), icon = Icons.Filled.Language) {
                Text(
                    stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LanguageSelector(current = language, onSelect = { appVm.setLanguage(it) })
            }

            // --- Notifications ---
            SettingsGroup(title = stringResource(R.string.settings_section_notifications), icon = Icons.Filled.Notifications) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_task_reminders),
                    supporting = stringResource(R.string.settings_task_reminders_desc),
                    trailing = { Switch(checked = systemAlerts, onCheckedChange = { vm.setSystemAlertsEnabled(it) }) },
                )
                Column {
                    Text(
                        stringResource(R.string.settings_default_alert),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (systemAlerts) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        reminderLabel(context, reminderMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (systemAlerts) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    val currentIdx = reminderPresets.indexOfFirst { it >= reminderMinutes }
                        .let { if (it < 0) reminderPresets.lastIndex else it }
                    androidx.compose.material3.Slider(
                        value = currentIdx.toFloat(),
                        onValueChange = {
                            val idx = it.toInt().coerceIn(0, reminderPresets.lastIndex)
                            vm.setReminderMinutes(reminderPresets[idx])
                        },
                        valueRange = 0f..(reminderPresets.size - 1).toFloat(),
                        steps = reminderPresets.size - 2,
                        enabled = systemAlerts,
                    )
                }
                // System-settings actions are outlined + full-width, visually distinct from toggles.
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_notif_permission)) }
                Text(
                    stringResource(if (notifEnabled) R.string.settings_notif_status_on else R.string.settings_notif_status_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notifEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (!exactAlarmGranted) {
                    OutlinedButton(
                        onClick = { vm.openExactAlarmSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_exact_alarm_action)) }
                    Text(
                        stringResource(R.string.settings_exact_alarm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Automatic updates ---
            SettingsGroup(title = stringResource(R.string.settings_section_updates), icon = Icons.Filled.SystemUpdate) {
                val autoUpdate by vm.autoUpdate.collectAsStateWithLifecycle()
                DhSettingsRow(
                    title = stringResource(R.string.settings_auto_update),
                    supporting = stringResource(R.string.settings_auto_update_desc),
                    trailing = { Switch(checked = autoUpdate, onCheckedChange = { vm.setAutoUpdate(it) }) },
                )
            }

            // --- Exercise demo videos ---
            SettingsGroup(title = stringResource(R.string.settings_exercisedb_title), icon = Icons.Filled.FitnessCenter) {
                val apiKey by vm.exerciseDbKey.collectAsStateWithLifecycle()
                Text(
                    stringResource(R.string.settings_exercisedb_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    apiKey?.let { "••••" + it.takeLast(4) }
                        ?: stringResource(R.string.settings_exercisedb_not_set),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { keyInput = apiKey.orEmpty(); showKeyDialog = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_save)) }
                    if (!apiKey.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { vm.setExerciseDbKey(null) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_exercisedb_clear)) }
                    }
                }
            }

            // --- Sync + Account ---
            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                SettingsGroup(title = stringResource(R.string.settings_section_sync), icon = Icons.Filled.CloudSync) {
                    Text(
                        lastSyncAt?.let { stringResource(R.string.settings_last_sync, it) }
                            ?: stringResource(R.string.settings_never_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.syncNow() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_push)) }
                        OutlinedButton(onClick = { vm.pullNow() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_pull)) }
                    }
                }

                OutlinedButton(
                    onClick = { vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(stringResource(R.string.settings_exercisedb_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.settings_exercisedb_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setExerciseDbKey(keyInput.takeIf { it.isNotBlank() })
                    showKeyDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

/** Bilingual picker: segmented two-option control. Applies instantly. */
@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit) {
    val codes = AppLanguage.entries.map { it.code }
    val names = AppLanguage.entries.map { it.nativeName }
    val selected = codes.indexOf(current).let { if (it < 0) 0 else it }
    DhSegmentedControl(options = names, selectedIndex = selected, onSelect = { onSelect(codes[it]) }, modifier = Modifier.fillMaxWidth())
}

/** Settings group: section header + single bordered card (not a giant floating card per row). */
@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DhSectionHeader(title = title)
        DhCard { content() }
    }
}

private fun Context.findActivity(): ComponentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("SettingsScreen bir ComponentActivity içinde barındırılmalı")
}
