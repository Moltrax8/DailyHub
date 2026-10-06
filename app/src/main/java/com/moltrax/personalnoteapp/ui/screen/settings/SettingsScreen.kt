package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.AppViewModel
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhFilterChip
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSegmentedControl
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.components.DhTonalIcon
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.i18n.AppLanguage
import com.moltrax.personalnoteapp.ui.navigation.Profile
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.profile.ProfileViewModel
import com.moltrax.personalnoteapp.ui.theme.DhThemeMode
import kotlinx.coroutines.launch

// Reminder presets (minutes). Stored values stay backward compatible: any
// previously saved value still resolves via reminderLabel(), even when it is
// not one of these chips.
private val reminderPresets = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 360, 720, 1440)

private fun reminderLabel(context: Context, m: Int): String = when {
    m < 60 -> context.resources.getQuantityString(R.plurals.reminder_minutes_before, m, m)
    m % 60 == 0 -> {
        val h = m / 60
        context.resources.getQuantityString(R.plurals.reminder_hours_before, h, h)
    }
    else -> context.getString(R.string.reminder_hours_minutes_before, m / 60, m % 60)
}

/** Compact chip label for a preset: short consistent units (5 m … 1 d). */
private fun reminderShortLabel(context: Context, m: Int): String = when {
    m == 90 -> context.getString(R.string.settings_reminder_hour_half)
    m < 60 -> context.getString(R.string.settings_reminder_min, m)
    m % 1440 == 0 -> context.getString(R.string.settings_reminder_day, m / 1440)
    m % 60 == 0 -> context.getString(R.string.settings_reminder_hour, m / 60)
    else -> context.getString(R.string.reminder_hours_minutes_before, m / 60, m % 60)
}

private const val URL_PRIVACY = "https://github.com/Moltrax8/DailyHub/blob/main/PRIVACY.md"
private const val URL_TERMS = "https://github.com/Moltrax8/DailyHub/blob/main/TERMS.md"
private const val URL_RELEASES = "https://github.com/Moltrax8/DailyHub/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    profileVm: ProfileViewModel = hiltViewModel(),
    authVm: SupabaseAuthViewModel = hiltViewModel(),
) {
    val reminderMinutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val systemAlerts by vm.systemAlertsEnabled.collectAsStateWithLifecycle()
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val autoUpdate by vm.autoUpdate.collectAsStateWithLifecycle()
    val accountEmail by authVm.userEmail.collectAsStateWithLifecycle()
    val profileState by profileVm.uiState.collectAsStateWithLifecycle()

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

    val scope = rememberCoroutineScope()
    var checkingUpdates by rememberSaveable { mutableStateOf(false) }
    var updateResult by rememberSaveable { mutableStateOf<String?>(null) }
    val upToDateText = stringResource(R.string.settings_up_to_date)
    val unavailableText = stringResource(R.string.settings_update_unavailable)
    val checkingText = stringResource(R.string.settings_checking)

    Scaffold(
        topBar = {
            DhTopBar(
                title = stringResource(R.string.settings_title),
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Account (links to Profile): short status, details live on Profile. ---
            val signedOutLabel = stringResource(R.string.profile_status_offline)
            val accountSupporting = accountEmail ?: vm.accountEmail ?: signedOutLabel
            DhSection(title = stringResource(R.string.settings_section_account)) {
                DhSettingsRow(
                    title = profileState.displayName,
                    supporting = accountSupporting,
                    leading = {
                        DhTonalIcon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { nav.navigate(Profile) },
                )
            }

            // --- Appearance ---
            DhSection(title = stringResource(R.string.settings_section_appearance)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhTonalIcon(Icons.Filled.Palette, contentDescription = null)
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }

            // --- Language ---
            DhSection(title = stringResource(R.string.settings_section_language)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhTonalIcon(Icons.Filled.Language, contentDescription = null)
                    LanguageSelector(
                        current = language,
                        onSelect = { appVm.setLanguage(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }

            // --- Notifications ---
            DhSection(title = stringResource(R.string.settings_section_notifications)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_task_reminders),
                    supporting = stringResource(R.string.settings_task_reminders_desc),
                    leading = { DhTonalIcon(Icons.Filled.Notifications, contentDescription = null) },
                    trailing = {
                        Switch(
                            checked = systemAlerts,
                            onCheckedChange = { vm.setSystemAlertsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    },
                    onClick = { vm.setSystemAlertsEnabled(!systemAlerts) },
                )
                DhDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_default_alert),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (systemAlerts) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        text = reminderLabel(context, reminderMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // One scrollable row of equal chips (short consistent labels).
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reminderPresets.forEach { preset ->
                            DhFilterChip(
                                selected = reminderMinutes == preset,
                                onClick = { vm.setReminderMinutes(preset) },
                                label = reminderShortLabel(context, preset),
                                enabled = systemAlerts,
                            )
                        }
                    }
                }
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_notif_status_title),
                    supporting = null,
                    leading = {
                        DhTonalIcon(
                            if (notifEnabled) Icons.Filled.Notifications
                            else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                        )
                    },
                    trailing = {
                        DhStatusChip(
                            label = stringResource(
                                if (notifEnabled) R.string.settings_state_on
                                else R.string.settings_state_off,
                            ),
                        )
                    },
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )
                if (!exactAlarmGranted) {
                    DhDivider()
                    DhSettingsRow(
                        title = stringResource(R.string.settings_exact_alarm_title),
                        supporting = stringResource(R.string.settings_exact_alarm_desc),
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { vm.openExactAlarmSettings() },
                    )
                }
            }

            // --- Sync & backup (existing behaviour: push/pull) ---
            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                DhSection(title = stringResource(R.string.settings_section_sync)) {
                    DhSettingsRow(
                        title = stringResource(R.string.settings_sync_status),
                        supporting = lastSyncAt?.let { stringResource(R.string.settings_last_sync, it) }
                            ?: stringResource(R.string.settings_never_synced),
                        leading = { DhTonalIcon(Icons.Filled.CloudSync, contentDescription = null) },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { vm.syncNow() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_push))
                        }
                        OutlinedButton(onClick = { vm.pullNow() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_pull))
                        }
                    }
                }
            }

            // --- Integrations (GitHub link + exercise demo key) ---
            DhSection(title = stringResource(R.string.settings_section_github)) {
                GitHubRow()
                DhDivider()
                val apiKey by vm.exerciseDbKey.collectAsStateWithLifecycle()
                DhSettingsRow(
                    title = stringResource(R.string.settings_exercisedb_title),
                    supporting = apiKey?.let { "••••" + it.takeLast(4) }
                        ?: stringResource(R.string.settings_exercisedb_not_set),
                    leading = { DhTonalIcon(Icons.Filled.FitnessCenter, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { keyInput = apiKey.orEmpty(); showKeyDialog = true },
                )
            }

            // --- Updates ---
            DhSection(title = stringResource(R.string.settings_section_updates)) {
                DhSettingsRow(
                    title = stringResource(R.string.settings_auto_update),
                    supporting = stringResource(
                        if (autoUpdate) R.string.settings_state_on
                        else R.string.settings_state_off,
                    ),
                    leading = { DhTonalIcon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    trailing = {
                        Switch(
                            checked = autoUpdate,
                            onCheckedChange = { vm.setAutoUpdate(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    },
                    onClick = { vm.setAutoUpdate(!autoUpdate) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_check_updates),
                    supporting = if (checkingUpdates) checkingText else updateResult,
                    leading = { DhTonalIcon(Icons.Filled.Refresh, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        if (checkingUpdates) return@DhSettingsRow
                        checkingUpdates = true
                        scope.launch {
                            val result = vm.checkForUpdatesNow()
                            checkingUpdates = false
                            updateResult = when (result) {
                                UpdateCheck.UpToDate -> upToDateText
                                UpdateCheck.Unavailable -> unavailableText
                                is UpdateCheck.Available ->
                                    context.getString(R.string.settings_update_available, result.release.versionName)
                            }
                        }
                    },
                )
            }

            // --- About ---
            DhSection(title = stringResource(R.string.settings_section_about)) {
                DhSettingsRow(
                    title = stringResource(R.string.app_name),
                    supporting = context.getString(
                        R.string.settings_about_version_value,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    leading = { DhTonalIcon(Icons.Filled.Info, contentDescription = null) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_about_build),
                    supporting = BuildConfig.BUILD_TYPE,
                    leading = { DhTonalIcon(Icons.Filled.Build, contentDescription = null) },
                )
                DhDivider()
                AboutLinkRow(
                    title = stringResource(R.string.settings_privacy),
                    icon = Icons.Filled.Security,
                    onClick = { context.openUrl(URL_PRIVACY) },
                )
                DhDivider()
                AboutLinkRow(
                    title = stringResource(R.string.settings_terms),
                    icon = Icons.Filled.Description,
                    onClick = { context.openUrl(URL_TERMS) },
                )
                DhDivider()
                AboutLinkRow(
                    title = stringResource(R.string.settings_releases),
                    icon = Icons.Filled.OpenInNew,
                    onClick = { context.openUrl(URL_RELEASES) },
                )
            }
        }
    }

    if (showKeyDialog) {
        val apiKey by vm.exerciseDbKey.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(stringResource(R.string.settings_exercisedb_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_exercisedb_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(stringResource(R.string.settings_exercisedb_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                    )
                    if (!apiKey.isNullOrBlank()) {
                        TextButton(
                            onClick = { vm.setExerciseDbKey(null); showKeyDialog = false },
                        ) { Text(stringResource(R.string.settings_exercisedb_clear)) }
                    }
                }
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

@Composable
private fun AboutLinkRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DhSettingsRow(
        title = title,
        supporting = null,
        leading = { DhTonalIcon(icon, contentDescription = null) },
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Bilingual picker: segmented two-option control. Applies instantly. */
@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val codes = AppLanguage.entries.map { it.code }
    val names = AppLanguage.entries.map { it.nativeName }
    val selected = codes.indexOf(current).let { if (it < 0) 0 else it }
    DhSegmentedControl(options = names, selectedIndex = selected, onSelect = { onSelect(codes[it]) }, modifier = modifier)
}

/** GitHub connect/disconnect row: links the account for repo picking (Projects tab). */
@Composable
private fun GitHubRow(ghVm: GithubSettingsViewModel = hiltViewModel()) {
    val state by ghVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDisconnect by remember { mutableStateOf(false) }
    val openFailed = stringResource(R.string.github_open_failed)
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { ghVm.reportError(openFailed) }
    }

    // Finish a link started here (or in Projects): the browser returns via
    // dailyhub://github-callback while this screen is open.
    val pendingCallback by com.moltrax.personalnoteapp.ui.github.GitHubCallbackBus.pending
        .collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingCallback) {
        val cb = pendingCallback ?: return@LaunchedEffect
        com.moltrax.personalnoteapp.ui.github.GitHubCallbackBus.consume()
        ghVm.finishLink(cb.code, cb.state)
    }

    // One row: title "GitHub", neutral status line, trailing tonal action.
    // Ordinary states stay in onSurfaceVariant — error red is only for real failures.
    DhSettingsRow(
        title = stringResource(R.string.settings_section_github),
        supporting = state.login?.let { context.getString(R.string.github_connected_as, "@$it") }
            ?: stringResource(R.string.github_not_connected_title),
        leading = { DhTonalIcon(Icons.Filled.Code, contentDescription = null) },
        trailing = {
            if (state.login == null) {
                FilledTonalButton(
                    onClick = { ghVm.startConnect(openUrl) },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.github_connect)) }
            } else {
                FilledTonalButton(
                    onClick = { showDisconnect = true },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.github_disconnect)) }
            }
        },
    )
    state.error?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
    }

    if (showDisconnect) {
        com.moltrax.personalnoteapp.ui.components.DhConfirmDialog(
            title = stringResource(R.string.github_disconnect_title),
            message = stringResource(R.string.github_disconnect_confirm),
            confirmLabel = stringResource(R.string.github_disconnect),
            onConfirm = { showDisconnect = false; ghVm.disconnect() },
            onDismiss = { showDisconnect = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
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
