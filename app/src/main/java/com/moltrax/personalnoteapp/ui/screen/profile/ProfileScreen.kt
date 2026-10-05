package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.navigation.Settings
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import com.moltrax.personalnoteapp.ui.screen.home.BottomNavBar
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel
import com.moltrax.personalnoteapp.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Profile screen uses the app's shared dark/neon (purple) palette — consistent with all screens.
 * Roles (panel, edge, accent, text) are defined directly via the shared [AppColors].
 */
private object SoloColors {
    val BgTop      = AppColors.BgDeep
    val BgBottom   = AppColors.BgSurface
    val Panel      = AppColors.BgCard
    val PanelEdge  = AppColors.Accent
    val Neon       = AppColors.Accent
    val NeonDeep   = AppColors.Accent
    val TextBright = AppColors.TextPrimary
    val TextDim    = AppColors.TextSecondary
    val Track      = AppColors.BgSurface
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    pvm: ProfileViewModel = hiltViewModel(),
    authVm: SupabaseAuthViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    val birthDate by vm.birthDate.collectAsStateWithLifecycle()
    val age by vm.age.collectAsStateWithLifecycle()
    val status by pvm.uiState.collectAsStateWithLifecycle()
    val session by authVm.sessionState.collectAsStateWithLifecycle()
    val social by socialVm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showNameEditor by remember { mutableStateOf(false) }
    val signedIn = session is SessionState.SignedIn
    // Single identity: server avatar/username win when signed in, local otherwise.
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_panel_profile)) },
                actions = {
                    IconButton(onClick = { nav.navigate(Settings) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
        bottomBar = { BottomNavBar(nav) },
        modifier = Modifier.background(
            Brush.verticalGradient(listOf(SoloColors.BgTop, SoloColors.BgBottom)),
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HunterHeaderPanel(
                status = status,
                username = headerUsername,
                avatarUrl = headerAvatar,
                onEditName = { showNameEditor = true },
                onPickAvatar = if (signedIn) {
                    { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                } else null,
            )
            SettingsPanel(
                birthDate = birthDate,
                age = age,
                onPickDate = { showDatePicker = true },
            )
            // Managed account (Phase 3+ Supabase): sign-in, friends, sign-out. Independent from Drive.
            AccountCard(nav)

            // Sign-out is hidden while Drive sync is off (consistent with SettingsScreen):
            // avoids navigating to Login and getting stuck in a dead end.
            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                Button(
                    onClick = { vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = birthDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
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
        NameEditorDialog(
            initialName = status.displayName,
            onDismiss = { showNameEditor = false },
            onSave = { name ->
                // One editor writes both identities: local display name + server profile.
                vm.setDisplayName(name)
                if (signedIn) socialVm.setMyDisplayName(name)
                showNameEditor = false
            },
        )
    }
}

@Composable
private fun NameEditorDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
            )
        },
        // Leaving it blank clears the override → falls back to the Google account name.
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}


// ----------------------------------------------------------------------------
// Status Window panels
// ----------------------------------------------------------------------------

/** Neon-edged, translucent thematic panel — "system window" aesthetics. */
@Composable
private fun StatusPanel(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SoloColors.Panel)
            .border(1.dp, SoloColors.PanelEdge.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                "⟦ $title ⟧",
                style = MaterialTheme.typography.labelMedium,
                color = SoloColors.Neon,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SoloColors.PanelEdge.copy(alpha = 0.25f))
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
private fun HunterHeaderPanel(
    status: ProfileUiState,
    username: String?,
    avatarUrl: String?,
    onEditName: () -> Unit,
    onPickAvatar: (() -> Unit)?,
) {
    StatusPanel(title = stringResource(R.string.profile_panel_profile)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
                // Glowing circular avatar (tap to change when signed in)
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SoloColors.NeonDeep.copy(alpha = 0.18f))
                    .border(2.dp, SoloColors.Neon, CircleShape)
                    .let { m -> if (onPickAvatar != null) m.clickable(onClick = onPickAvatar) else m },
                contentAlignment = Alignment.Center,
            ) {
                val photo = avatarUrl
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        modifier = Modifier.size(68.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null,
                        tint = SoloColors.Neon, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // Tapping the name opens it too; the pencil icon next to it also opens the edit dialog.
                Row(
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onEditName),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        status.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = SoloColors.TextBright,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.profile_edit_name_title),
                        tint = SoloColors.Neon,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // Server handle, read-only (chosen at sign-up; friends find you by it).
                username?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "@$it",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloColors.TextDim,
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------------------------------
// Settings/edit panel (existing features inside a thematic shell)
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    birthDate: LocalDate?,
    age: Int?,
    onPickDate: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    StatusPanel(title = stringResource(R.string.profile_panel_info)) {
        SettingRow(
            icon = { Icon(Icons.Filled.Cake, contentDescription = null, tint = SoloColors.Neon) },
            title = stringResource(R.string.profile_birthdate),
            subtitle = birthDate?.format(dateFmt) ?: stringResource(R.string.profile_not_selected_tap),
            trailing = { age?.let { Text(stringResource(R.string.profile_age_value, it), style = MaterialTheme.typography.labelMedium, color = SoloColors.Neon) } },
            onClick = onPickDate,
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = SoloColors.TextBright, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SoloColors.TextDim)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
