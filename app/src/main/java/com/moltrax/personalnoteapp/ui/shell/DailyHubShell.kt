package com.moltrax.personalnoteapp.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import com.moltrax.personalnoteapp.ui.navigation.Home
import com.moltrax.personalnoteapp.ui.navigation.Profile
import com.moltrax.personalnoteapp.ui.navigation.Projects
import com.moltrax.personalnoteapp.ui.navigation.SocialGraph
import com.moltrax.personalnoteapp.ui.navigation.WorkoutList

/**
 * Application-level navigation chrome. Root destinations live here —
 * never inside a feature screen (HomeScreen must not own the bottom bar).
 */

private data class RootItem(
    val labelRes: Int,
    val icon: ImageVector,
    val matches: (String?) -> Boolean,
    val navigate: (NavController) -> Unit,
)

private fun rootItems(): List<RootItem> = listOf(
    RootItem(R.string.nav_tasks, Icons.Filled.CheckCircle,
        { r -> r?.contains("Home") == true },
        { it.navigate(Home) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_workouts, Icons.Filled.FitnessCenter,
        { r -> r?.contains("Workout") == true },
        { it.navigate(WorkoutList) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_projects, Icons.Filled.Dashboard,
        { r -> r?.contains("Project") == true },
        { it.navigate(Projects) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_social, Icons.Filled.People,
        { r -> r?.contains("Social") == true || r?.contains("Friend") == true || r?.contains("UserProfile") == true },
        { it.navigate(SocialGraph) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
    RootItem(R.string.nav_profile, Icons.Filled.Person,
        { r -> r?.contains("Profile") == true },
        { it.navigate(Profile) { launchSingleTop = true; popUpTo<Home> { saveState = true }; restoreState = true } }),
)

/** True when the current route is a root destination (chrome visible). */
fun isRootRoute(route: String?): Boolean {
    if (route == null) return false
    return listOf("Home", "WorkoutList", "Projects", "SocialGraph", "Profile").any { route.contains(it) }
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
            // Foundation: the shell owns ONLY the bottom (navigation-bar) inset via
            // its bottomBar. Status-bar insets belong to each screen's own top bar
            // (DhTopBar / TopAppBarDefaults.windowInsets) exactly once — so the
            // shell consumes no system insets itself (avoids the double top band).
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
