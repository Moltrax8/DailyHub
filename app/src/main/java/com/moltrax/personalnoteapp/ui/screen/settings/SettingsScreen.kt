package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.moltrax.personalnoteapp.ui.i18n.AppLanguage
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.theme.AppColors

// Anlamlı hatırlatma ön ayarları (dakika)
private val reminderPresets = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 360, 720, 1440)

private fun reminderLabel(context: Context, m: Int): String = when {
    m < 60       -> context.getString(R.string.reminder_minutes_before, m)
    m % 60 == 0  -> context.getString(R.string.reminder_hours_before, m / 60)
    else         -> context.getString(R.string.reminder_hours_minutes_before, m / 60, m % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val reminderMinutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val systemAlerts    by vm.systemAlertsEnabled.collectAsStateWithLifecycle()
    val lastSyncAt      by vm.lastSyncAt.collectAsStateWithLifecycle()
    val language        by vm.language.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Kesin-alarm izni ayarlardan dönünce değişmiş olabilir; ON_RESUME'da tazele.
    var exactAlarmGranted by remember { mutableStateOf(vm.canScheduleExactAlarms()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = vm.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Dil değişimini Activity-kapsamlı AppViewModel üzerinden yap; böylece kök composition'daki
    // "Yükleniyor" göstergesi (MainActivity) aynı örneği dinler.
    val appVm: AppViewModel = hiltViewModel(context.findActivity())
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Dil ---
            SettingsSection(title = stringResource(R.string.settings_section_language), icon = Icons.Filled.Language) {
                Text(
                    stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LanguageSelector(
                    current = language,
                    onSelect = { appVm.setLanguage(it) },
                )
            }

            // --- Bildirimler ---
            SettingsSection(title = stringResource(R.string.settings_section_notifications), icon = Icons.Filled.Notifications) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_task_reminders),
                    subtitle = stringResource(R.string.settings_task_reminders_desc),
                    checked = systemAlerts,
                    onCheckedChange = { vm.setSystemAlertsEnabled(it) },
                )
                HorizontalDivider(color = AppColors.BorderSubtle)
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
                        color = if (systemAlerts) AppColors.Accent
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    val currentIdx = reminderPresets.indexOfFirst { it >= reminderMinutes }
                        .let { if (it < 0) reminderPresets.lastIndex else it }
                    Slider(
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
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_notif_permission)) }
                // Kesin alarm izni yoksa hatırlatmalar gecikebilir — izin ekranına yönlendir.
                if (!exactAlarmGranted) {
                    OutlinedButton(
                        onClick = { vm.openExactAlarmSettings() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_exact_alarm_action)) }
                    Text(
                        stringResource(R.string.settings_exact_alarm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Egzersiz demo videoları (kullanıcının kendi RapidAPI anahtarı) ---
            SettingsSection(title = stringResource(R.string.settings_exercisedb_title), icon = Icons.Filled.FitnessCenter) {
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_save)) }
                    if (!apiKey.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { vm.setExerciseDbKey(null) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_exercisedb_clear)) }
                    }
                }
            }

            // --- Senkronizasyon + Hesap ---
            // Drive sync şimdilik kapalı (bkz. FeatureFlags): giriş yapılmadığından
            // yedekleme/çıkış bölümleri gizlenir. Bayrak açılınca geri gelir.
            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                SettingsSection(title = stringResource(R.string.settings_section_sync), icon = Icons.Filled.CloudSync) {
                    Text(
                        lastSyncAt?.let { stringResource(R.string.settings_last_sync, it) }
                            ?: stringResource(R.string.settings_never_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.syncNow() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_push)) }
                        OutlinedButton(
                            onClick = { vm.pullNow() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_pull)) }
                    }
                }

                Button(
                    onClick = { vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // RapidAPI anahtar giriş diyaloğu: kaydet boşsa temizler, iptal dokunmaz.
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text(stringResource(R.string.settings_exercisedb_title)) },
            text = {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.settings_exercisedb_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
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
        )
    }
}

/** İki dilli (TR/EN) modern seçici: yan yana iki seçilebilir buton. Seçim anında uygulanır. */
@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        AppLanguage.entries.forEach { lang ->
            val selected = current == lang.code
            if (selected) {
                Button(
                    onClick = { onSelect(lang.code) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    modifier = Modifier.weight(1f),
                ) { Text(lang.nativeName) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(lang.code) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(lang.nativeName) }
            }
        }
    }
}

/**
 * Tek bir ayar grubu için yuvarlatılmış kart. Başlıkta bir ikon + bölüm adı; altında [content].
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/**
 * Bu context'i saran [ComponentActivity]'yi bulur. MainActivity, LocalContext'i yerelleştirilmiş bir
 * [ContextWrapper] ile sardığından doğrudan cast yerine zinciri yürümek gerekir.
 */
private fun Context.findActivity(): ComponentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("SettingsScreen bir ComponentActivity içinde barındırılmalı")
}

/** Başlık + açıklama solda, sağda Switch — aç/kapat ayarları için hizalı satır. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
