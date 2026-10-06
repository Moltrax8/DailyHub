package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.ui.components.DhConfirmDialog
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.components.DhTonalIcon
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.navigation.Settings
import com.moltrax.personalnoteapp.ui.navigation.SupabaseAuth
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.settings.GithubSettingsViewModel
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    pvm: ProfileViewModel = hiltViewModel(),
    authVm: SupabaseAuthViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
    ghVm: GithubSettingsViewModel = hiltViewModel(),
) {
    val birthDate by vm.birthDate.collectAsStateWithLifecycle()
    val age by vm.age.collectAsStateWithLifecycle()
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle()
    val status by pvm.uiState.collectAsStateWithLifecycle()
    val session by authVm.sessionState.collectAsStateWithLifecycle()
    val accountEmail by authVm.userEmail.collectAsStateWithLifecycle()
    val social by socialVm.state.collectAsStateWithLifecycle()
    val ghState by ghVm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showNameEditor by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val supabaseSignedIn = session is SessionState.SignedIn
    val driveSignedIn = vm.accountEmail != null
    val signedIn = supabaseSignedIn || driveSignedIn
    val email = accountEmail ?: vm.accountEmail
    val headerAvatar = social.myProfile?.avatarUrl?.takeIf { it.isNotBlank() } ?: status.photoUrl
    val headerUsername = social.myProfile?.username?.takeIf { signedIn }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || !signedIn) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.endsWith("png") -> "png"
                    mime.endsWith("webp") -> "webp"
                    else -> "jpg"
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unreadable image.")
                socialVm.uploadMyAvatar(bytes, ext)
            }
        }
    }

    Scaffold(
        topBar = {
            DhTopBar(
                title = stringResource(R.string.profile_panel_profile),
                actions = {
                    IconButton(onClick = { nav.navigate(Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Identity header: avatar + name (pencil to edit) + email/handle + status chip.
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                            .let { m ->
                                if (signedIn) m.clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else m
                            },
                    ) {
                        if (headerAvatar != null) {
                            AsyncImage(
                                model = headerAvatar,
                                contentDescription = stringResource(R.string.cd_profile_photo),
                                modifier = Modifier.size(72.dp).clip(CircleShape),
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                status.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { showNameEditor = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.profile_edit_name_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        headerUsername?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "@$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DhStatusChip(
                            label = stringResource(
                                if (signedIn) R.string.profile_status_signed_in
                                else R.string.profile_status_offline,
                            ),
                            icon = if (signedIn) Icons.Filled.Check else Icons.Filled.CloudOff,
                        )
                    }
                }
            }

            // ONE Account block: sign in when signed out, sign out (confirmed) when signed in.
            DhSection(title = stringResource(R.string.profile_section_account)) {
                if (!signedIn) {
                    Text(
                        stringResource(R.string.profile_account_offline_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Button(
                        onClick = { nav.navigate(SupabaseAuth) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    ) { Text(stringResource(R.string.supabase_auth_sign_in)) }
                } else {
                    email?.let {
                        DhSettingsRow(
                            title = it,
                            supporting = headerUsername?.let { u -> "@$u" },
                            leading = { DhTonalIcon(Icons.Filled.Person, contentDescription = null) },
                        )
                        DhDivider()
                    }
                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_sign_out))
                    }
                }
            }

            // Personal: birth date.
            DhSection(title = stringResource(R.string.profile_section_personal)) {
                val locale = LocalConfiguration.current.locales[0]
                val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
                DhSettingsRow(
                    title = stringResource(R.string.profile_birthdate),
                    supporting = birthDate?.format(dateFmt) ?: stringResource(R.string.profile_not_selected_tap),
                    leading = { DhTonalIcon(Icons.Filled.Cake, contentDescription = null) },
                    trailing = {
                        age?.let {
                            DhStatusChip(label = stringResource(R.string.profile_age_value, it))
                        }
                    },
                    onClick = { showDatePicker = true },
                )
            }

            // Sync & integrations in ONE group block (details live in Settings).
            DhSection(title = stringResource(R.string.profile_section_sync_integrations)) {
                DhSettingsRow(
                    title = stringResource(R.string.profile_sync_title),
                    supporting = lastSyncAt?.let { stringResource(R.string.settings_last_sync, it) }
                        ?: stringResource(R.string.settings_never_synced),
                    leading = { DhTonalIcon(Icons.Filled.CloudSync, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { nav.navigate(Settings) },
                )
                DhDivider()
                DhSettingsRow(
                    title = stringResource(R.string.settings_section_github),
                    supporting = ghState.login?.let { context.getString(R.string.github_connected_as, "@$it") }
                        ?: stringResource(R.string.github_not_connected_title),
                    leading = { DhTonalIcon(Icons.Filled.Code, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { nav.navigate(Settings) },
                )
            }

            // App version footer.
            Text(
                stringResource(
                    R.string.profile_version_footer,
                    stringResource(R.string.app_name),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.BUILD_TYPE,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSignOutConfirm) {
        DhConfirmDialog(
            title = stringResource(R.string.profile_sign_out),
            message = stringResource(R.string.profile_sign_out_confirm),
            confirmLabel = stringResource(R.string.profile_sign_out),
            onConfirm = {
                showSignOutConfirm = false
                if (supabaseSignedIn) authVm.signOut()
                if (driveSignedIn && FeatureFlags.DRIVE_SYNC_ENABLED) {
                    vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } }
                }
            },
            onDismiss = { showSignOutConfirm = false },
            dismissLabel = stringResource(R.string.action_dismiss),
        )
    }

    if (showDatePicker) {
        val initialMillis = birthDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val today = remember { LocalDate.now() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isAfter(today)
                }
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            vm.setBirthDate(date)
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showNameEditor) {
        var name by remember { mutableStateOf(status.displayName) }
        AlertDialog(
            onDismissRequest = { showNameEditor = false },
            title = { Text(stringResource(R.string.profile_edit_name_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().imePadding(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDisplayName(name.trim())
                    if (supabaseSignedIn) socialVm.setMyDisplayName(name.trim())
                    showNameEditor = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showNameEditor = false }) { Text(stringResource(R.string.action_dismiss)) } },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Preview(name = "Profile — light", showBackground = true)
@Composable
private fun ProfilePreview() {
    AppTheme(themeMode = "light") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhSection(title = "Personal") {
                DhSettingsRow(title = "Birth date", supporting = "12 May 1990")
            }
        }
    }
}
