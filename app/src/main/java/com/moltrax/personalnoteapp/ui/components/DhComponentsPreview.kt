package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moltrax.personalnoteapp.ui.theme.AppTheme

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
