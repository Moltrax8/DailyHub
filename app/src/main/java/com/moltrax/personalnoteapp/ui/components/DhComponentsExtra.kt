package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Extra shared components (rd3). Reuses Dh* primitives unchanged; screens in
// Workout / Projects / Social / Space share these instead of restyling.
// ---------------------------------------------------------------------------

/**
 * Clean scrollable tab row for hubs with 2+ tabs (project Board/GitHub,
 * space Overview/Plan/Discuss/Library/People). Tonal, no alarming colours.
 */
@Composable
fun DhScrollTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        options.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp),
                text = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (index == selectedIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
    }
}

/** Search input with leading icon; single line, 48dp target, ellipsis. */
@Composable
fun DhSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    onClear: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        label = label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty() && onClear != null) {
            {
                TextButton(onClick = onClear) {
                    Text("×")
                }
            }
        } else null,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
    )
}

/**
 * Consistent form dialog: title + optional 1-2 line message + scrollable
 * content + max two actions. Scroll + IME safe for small screens.
 */
@Composable
fun DhFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.invoke()
            }
        },
        confirmButton = {
            if (isDestructive) {
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(confirmLabel) }
            } else {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        },
        dismissButton = dismissLabel?.let { label -> { TextButton(onClick = onDismiss) { Text(label) } } },
        shape = MaterialTheme.shapes.large,
    )
}

/** Overlapping initials avatars for member lists (max shown + "+n"). */
@Composable
fun DhAvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    maxShown: Int = 4,
) {
    if (names.isEmpty()) return
    val shown = names.take(maxShown)
    val extra = names.size - shown.size
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { index, name ->
            val initials = name.trim().split(Regex("\\s+")).take(2)
                .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                .ifBlank { "•" }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = (-8 * index).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        if (extra > 0) {
            Text(
                "+$extra",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Hevy-style set row: [set #] [previous] [kg] [reps] [check].
 * Checked rows show a filled tonal check; unchecked show an outline target.
 */
@Composable
fun DhSetRow(
    index: Int,
    previous: String?,
    kg: String,
    onKgChange: (String) -> Unit,
    reps: String,
    onRepsChange: (String) -> Unit,
    checked: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    kgLabel: String,
    repsLabel: String,
    checkDescription: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            previous ?: "–",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
            value = kg,
            onValueChange = onKgChange,
            label = { Text(kgLabel, maxLines = 1) },
            modifier = Modifier.width(76.dp),
            singleLine = true,
            enabled = enabled && !checked,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
        )
        OutlinedTextField(
            value = reps,
            onValueChange = onRepsChange,
            label = { Text(repsLabel, maxLines = 1) },
            modifier = Modifier.width(76.dp),
            singleLine = true,
            enabled = enabled && !checked,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
        )
        if (checked) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = checkDescription)
                }
            }
        } else {
            IconButton(
                onClick = onCheck,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = checkDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact log row for duration/cardio: one input + check action. */
@Composable
fun DhLogInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    checkDescription: String,
    enabled: Boolean = true,
    decimal: Boolean = false,
    secondValue: String? = null,
    onSecondValueChange: ((String) -> Unit)? = null,
    secondLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            shape = MaterialTheme.shapes.small,
        )
        if (secondValue != null && onSecondValueChange != null) {
            OutlinedTextField(
                value = secondValue,
                onValueChange = onSecondValueChange,
                label = secondLabel?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
            )
        }
        IconButton(
            onClick = onLog,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = checkDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Tonal rest-timer pill: countdown + add-time + skip. */
@Composable
fun DhRestTimerPill(
    secondsLeft: Int,
    onAdd30: () -> Unit,
    onSkip: () -> Unit,
    addLabel: String,
    skipLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            TextButton(onClick = onAdd30) { Text(addLabel, maxLines = 1) }
            TextButton(onClick = onSkip) { Text(skipLabel, maxLines = 1) }
        }
    }
}
