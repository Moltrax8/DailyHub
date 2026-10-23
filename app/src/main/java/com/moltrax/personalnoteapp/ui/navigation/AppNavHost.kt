package com.moltrax.personalnoteapp.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.ui.screen.calendar.CalendarScreen
import com.moltrax.personalnoteapp.ui.screen.focus.FocusHubScreen
import com.moltrax.personalnoteapp.ui.screen.focus.FocusScreen
import com.moltrax.personalnoteapp.ui.screen.home.HomeScreen
import com.moltrax.personalnoteapp.ui.screen.notes.NoteDetailScreen
import com.moltrax.personalnoteapp.ui.screen.notes.NotesScreen
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsScreen
import com.moltrax.personalnoteapp.ui.screen.task.TaskDetailScreen
import com.moltrax.personalnoteapp.ui.shell.DailyHubScaffold

@Composable
fun AppNavHost(
    pendingWidgetAction: String? = null,
    pendingWidgetTaskId: String? = null,
    pendingWidgetTick: Int = 0,
    onWidgetActionConsumed: () -> Unit = {},
    pendingReminderTaskId: String? = null,
    onReminderConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    // Widget "+" opens the new-task screen. Single-shot; the tick re-fires repeated taps.
    LaunchedEffect(pendingWidgetAction, pendingWidgetTick) {
        if (pendingWidgetAction == MainActivity.ACTION_NEW_TASK) {
            nav.navigate(TaskDetail("new")) { launchSingleTop = true }
            onWidgetActionConsumed()
        }
    }

    LaunchedEffect(pendingReminderTaskId) {
        if (pendingReminderTaskId != null) {
            nav.navigate(TaskDetail(pendingReminderTaskId)) { launchSingleTop = true }
            onReminderConsumed()
        }
    }

    LaunchedEffect(pendingWidgetTaskId, pendingWidgetTick) {
        if (pendingWidgetTaskId != null) {
            nav.navigate(TaskDetail(pendingWidgetTaskId)) { launchSingleTop = true }
            onWidgetActionConsumed()
        }
    }

    DailyHubScaffold(nav = nav) { contentModifier ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(contentModifier)
        ) {
            val fast = tween<Float>(durationMillis = 90)
            NavHost(
                navController = nav,
                startDestination = Home,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(fast) },
                exitTransition = { fadeOut(fast) },
                popEnterTransition = { fadeIn(fast) },
                popExitTransition = { fadeOut(fast) },
            ) {
                composable<Home> {
                    HomeScreen(nav = nav)
                }
                composable<TaskDetail> { entry -> TaskDetailScreen(nav, entry.toRoute<TaskDetail>().taskId) }
                composable<Notes> { NotesScreen(nav) }
                composable<NoteDetail> { entry -> NoteDetailScreen(nav, entry.toRoute<NoteDetail>().noteId) }
                composable<Calendar> { CalendarScreen(nav) }
                composable<FocusHub> { FocusHubScreen(nav) }
                composable<FocusTimer> { entry -> FocusScreen(nav, entry.toRoute<FocusTimer>().taskId) }
                composable<Settings> { SettingsScreen(nav) }
            }
        }
    }
}
