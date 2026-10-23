package com.moltrax.personalnoteapp.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.navigation.Calendar
import com.moltrax.personalnoteapp.ui.navigation.FocusHub
import com.moltrax.personalnoteapp.ui.navigation.Home
import com.moltrax.personalnoteapp.ui.navigation.Notes
import com.moltrax.personalnoteapp.ui.navigation.Settings

/**
 * Application-level navigation chrome. Root destinations live here —
 * never inside a feature screen.
 */
private data class RootItem(
    val labelRes: Int,
    val icon: ImageVector,
    val matches: (String?) -> Boolean,
    val navigate: (NavController) -> Unit,
)

private val rootDestinations = listOf("Home", "Notes", "Calendar", "FocusHub", "Settings")

private fun rootItems(): List<RootItem> = listOf(
    RootItem(R.string.nav_tasks, Icons.Filled.CheckCircle,
        { r -> r?.contains("Home") == true },
        { it.navigate(Home) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_notes, Icons.Filled.Description,
        { r -> r?.contains("Notes") == true || r?.contains("NoteDetail") == true },
        { it.navigate(Notes) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_calendar, Icons.Filled.CalendarMonth,
        { r -> r?.contains("Calendar") == true },
        { it.navigate(Calendar) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_focus, Icons.Filled.Timer,
        { r -> r?.contains("Focus") == true },
        { it.navigate(FocusHub) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_settings, Icons.Filled.Settings,
        { r -> r?.contains("Settings") == true },
        { it.navigate(Settings) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
)

/** True when the current route is a root destination (chrome visible). */
fun isRootRoute(route: String?): Boolean {
    if (route == null) return false
    return rootDestinations.any { route.contains(it) }
}

@Composable
fun DailyHubScaffold(
    nav: NavController,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showChrome = isRootRoute(route)
    val items = rootItems()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp && showChrome
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    items.forEach { d ->
                        val label = stringResource(d.labelRes)
                        NavigationRailItem(
                            selected = d.matches(route),
                            onClick = { d.navigate(nav) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showChrome) {
                        NavigationBar {
                            items.forEach { d ->
                                val label = stringResource(d.labelRes)
                                NavigationBarItem(
                                    selected = d.matches(route),
                                    onClick = { d.navigate(nav) },
                                    icon = { Icon(d.icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> content(Modifier.padding(padding)) }
        }
    }
}
