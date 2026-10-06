package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import com.moltrax.personalnoteapp.ui.theme.Dh

// ---------------------------------------------------------------------------
// Feedback: loading / empty / error / offline / sync
// ---------------------------------------------------------------------------

/** Inline loading row (non-blocking). Use for background refresh, not full screens. */
@Composable
fun DhLoadingRow(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-area loading state with message. */
@Composable
fun DhLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Centered empty state: icon + title + description + optional action. */
@Composable
fun DhEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Recoverable error with retry. */
@Composable
fun DhErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retryLabel != null && onRetry != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** Offline notice row — compact, not a banner that steals space. */
@Composable
fun DhOfflineRow(modifier: Modifier = Modifier, message: String) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sync progress row with determinate/indeterminate handling. */
@Composable
fun DhSyncRow(
    message: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

// Back-compat wrappers used by older screens (delegating to Dh*).
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    DhSectionHeader(title = title, modifier = modifier, subtitle = subtitle, action = action)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    DhEmptyState(icon = icon, title = title, description = description, modifier = modifier, actionLabel = null, onAction = null)
    action?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() } }
}

// ---------------------------------------------------------------------------
// Structure: section header / card / list row / segmented control
// ---------------------------------------------------------------------------

/**
 * Screen section label: small and quiet (sentence case by callers), with an
 * optional one-line subtitle and trailing action. Shared by Settings/Profile
 * and every later screen — never restyle per screen.
 */
@Composable
fun DhSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action?.let { Row(verticalAlignment = Alignment.CenterVertically) { it() } }
    }
}

/**
 * Grouped-settings section: a quiet label above ONE rounded surfaceContainer
 * block with hairline dividers inset from the leading icon. Not one card per row.
 */
@Composable
fun DhSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DhSectionHeader(title = title, subtitle = subtitle)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** Hairline divider inset from the leading icon (56dp), for use inside [DhSection]. */
@Composable
fun DhDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Leading icon in a 40dp tonal circle (Settings/Profile style, app-wide). */
@Composable
fun DhTonalIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Primary container. Subtle tonal surface with hairline border and small
 * elevation — used sparingly; prefer grouping/typography before adding cards.
 */
@Composable
fun DhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { inner() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { inner() }
    }
}

/** Back-compat: old ModernCard mapped onto DhCard. */
@Composable
fun ModernCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    DhCard(modifier = modifier, onClick = onClick) { content() }
}

/**
 * Settings-style row: [icon] Title / status line ... [switch | value | chevron].
 * Tapping the row does the main action. Minimum 48dp touch target; long text
 * truncates with ellipsis. Works at font scale 1.3 (rows grow vertically).
 */
@Composable
fun DhSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val rowModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else modifier
    ListItem(
        modifier = rowModifier.defaultMinSize(minHeight = 48.dp),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        },
        supportingContent = supporting?.let { text ->
            {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ---------------------------------------------------------------------------
// Switch: ONE shared switch with explicit on/off colours (no muddy off state)
// ---------------------------------------------------------------------------

/**
 * The single shared switch for the whole app (Settings, Task detail,
 * GitHub/Project toggles, ...). Checked = primary track + onPrimary thumb.
 * Unchecked = surfaceVariant track with outline thumb + border. Disabled
 * states stay explicit theme colours — never the muddy default.
 */
@Composable
fun DhSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledCheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
    )
}

// ---------------------------------------------------------------------------
// Top bar: one shared small (64dp) bar — title titleLarge, back at start,
// actions at end. Used by every screen with a top bar so heights and title
// styles match. The bar owns the ONE status-bar inset (via
// TopAppBarDefaults.windowInsets); screens must not add statusBarsPadding.
// ---------------------------------------------------------------------------

/**
 * Shared top bar. [onBack] shows the back arrow at the start; [actions] sit
 * at the end. Exactly one status-bar inset lives here — callers inside
 * DailyHubScaffold must not add their own top inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                    )
                }
            }
        },
        actions = actions,
        windowInsets = androidx.compose.material3.TopAppBarDefaults.windowInsets,
    )
}

/** Compact two/three-option segmented control (e.g. Tasks | Calendar). */
@Composable
fun DhSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String>? = null,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = contentDescriptions?.getOrNull(index) ?: label }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Small status chip with icon + label; color communicates + label text (never color-only). */
@Composable
fun DhStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Filter chip backed by M3 FilterChip with consistent sizing. */
@Composable
fun DhFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

// ---------------------------------------------------------------------------
// Identity: avatar + name row shared by Profile / Social / Spaces
// ---------------------------------------------------------------------------

/** Reusable identity primitive: avatar circle + name (+ optional subtitle). */
@Composable
fun DhUserRow(
    displayName: String,
    modifier: Modifier = Modifier,
    username: String? = null,
    subtitle: String? = null,
    photoUrl: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(clickable.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DhAvatar(name = displayName, photoUrl = photoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName.ifBlank { username ?: "" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = subtitle ?: username?.let { "@$it" }
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Row(verticalAlignment = Alignment.CenterVertically) { it() } }
    }
}

/** Avatar circle with initials fallback (photo loading handled by callers via Coil where needed). */
@Composable
fun DhAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val initials = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        .ifBlank { "•" }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (photoUrl.isNullOrBlank()) {
                Text(initials, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                // Photo rendering is done by callers with Coil; fallback keeps layout stable.
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs / confirmations / forms
// ---------------------------------------------------------------------------

/** Destructive confirmation dialog with consistent copy + roles. */
@Composable
fun DhConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    isDestructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (isDestructive) {
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(confirmLabel)
                }
            } else {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
        shape = MaterialTheme.shapes.large,
    )
}

/** Standard single-line input with label + error + IME-safe defaults. */
@Composable
fun DhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            isError = error != null,
            enabled = enabled,
            singleLine = singleLine,
            trailingIcon = trailing,
            shape = MaterialTheme.shapes.small,
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------------------
// Misc: overdue dot, completion check, content width wrapper
// ---------------------------------------------------------------------------

/** Constrains phone-first columns on wide screens. */
@Composable
fun DhContentColumn(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/**
 * Design-system FAB: bright primary container (not the muted default
 * primaryContainer), so Tasks / Projects / Duo-hub / Workout all match.
 */
@Composable
fun DhFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Previews (no Hilt/network dependencies)
// ---------------------------------------------------------------------------

@Preview(name = "Dh components — light", showBackground = true)
@Composable
private fun DhComponentsPreviewLight() {
    AppTheme(themeMode = "light") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhSectionHeader(title = "Today", subtitle = "3 tasks due")
            DhSegmentedControl(options = listOf("Tasks", "Calendar"), selectedIndex = 0, onSelect = {})
            DhCard { Text("Project Phoenix", style = MaterialTheme.typography.titleMedium) }
            DhUserRow(displayName = "Ada Lovelace", username = "ada")
            DhStatusChip(label = "Overdue", container = MaterialTheme.colorScheme.errorContainer, icon = Icons.Filled.HourglassEmpty)
            DhEmptyState(icon = Icons.Filled.Check, title = "All done", description = "No tasks left for today.")
        }
    }
}

@Preview(name = "Dh components — dark", showBackground = true, backgroundColor = 0xFF111318)
@Composable
private fun DhComponentsPreviewDark() {
    AppTheme(themeMode = "dark") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhSectionHeader(title = "Today", subtitle = "3 tasks due")
            DhSegmentedControl(options = listOf("Tasks", "Calendar"), selectedIndex = 1, onSelect = {})
            DhCard { Text("Project Phoenix", style = MaterialTheme.typography.titleMedium) }
            DhUserRow(displayName = "Ada Lovelace", username = "ada")
            DhStatusChip(label = "Synced", icon = Icons.Filled.Check)
            DhErrorState(title = "Couldn't load", description = "Check your connection and retry.", retryLabel = "Retry", onRetry = {})
        }
    }
}

@Preview(name = "Task row states", showBackground = true)
@Composable
private fun DhTaskStatesPreview() {
    AppTheme(themeMode = "light") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Active: Design review · Today · 2 subtasks") }, leadingIcon = { Icon(Icons.Filled.HourglassEmpty, null) })
            AssistChip(
                onClick = {},
                label = { Text("Completed: Release notes") },
                leadingIcon = { Icon(Icons.Filled.Check, null) },
                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}
