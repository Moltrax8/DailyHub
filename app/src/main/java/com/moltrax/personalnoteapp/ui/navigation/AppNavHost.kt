package com.moltrax.personalnoteapp.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.ui.SyncViewModel
import com.moltrax.personalnoteapp.ui.components.SyncBanner
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthScreen
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.auth.LoginScreen
import com.moltrax.personalnoteapp.ui.screen.focus.FocusScreen
import com.moltrax.personalnoteapp.ui.screen.home.HomeScreen
import com.moltrax.personalnoteapp.ui.screen.profile.ProfileScreen
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsScreen
import com.moltrax.personalnoteapp.ui.screen.project.ProjectDetailScreen
import com.moltrax.personalnoteapp.ui.screen.project.ProjectsScreen
import com.moltrax.personalnoteapp.ui.screen.social.FriendRequestsScreen
import com.moltrax.personalnoteapp.ui.screen.social.SocialGraphScreen
import com.moltrax.personalnoteapp.ui.screen.social.UserProfileScreen
import com.moltrax.personalnoteapp.ui.screen.space.DuoHubScreen
import com.moltrax.personalnoteapp.ui.screen.task.TaskDetailScreen
import com.moltrax.personalnoteapp.ui.screen.workout.LiveWorkoutScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutDetailScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutSummaryScreen
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState

@Composable
fun AppNavHost(
    // Skip the login screen when Drive sync is off, start directly from the home screen.
    startDestination: Any = if (FeatureFlags.DRIVE_SYNC_ENABLED) Login else Home,
    pendingWidgetAction: String? = null,
    pendingWidgetTaskId: String? = null,
    onWidgetActionConsumed: () -> Unit = {},
    // Developer-activity push tap (Phase 7): open this project space once.
    pendingProjectId: String? = null,
    onProjectConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    // Silent auto-sync when coming to the foreground (returning from background). Changes made on a second
    // device are thus pulled automatically when returning to the app. The first ON_START
    // is a cold start; the launch sync is already done by HomeViewModel.init, so
    // we only trigger on LATER foreground arrivals (avoids double sync). If not signed in,
    // the repository silently returns a no-op.
    if (FeatureFlags.DRIVE_SYNC_ENABLED) {
        val syncVm: SyncViewModel = hiltViewModel()
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            var firstStart = true
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    if (firstStart) firstStart = false else syncVm.syncSilent()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // Managed-account entry (Phase 3): without a session the app opens the Auth
    // screen (v2 startDestination rule, applied after async session restore).
    // Auth is an entry point, not a lock: backing out lands Home and the app
    // stays fully usable offline; after an explicit sign-out we land on Auth.
    if (FeatureFlags.SUPABASE_ENABLED) {
        val gateVm: SupabaseAuthViewModel = hiltViewModel()
        val session by gateVm.sessionState.collectAsStateWithLifecycle()
        var redirected by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { gateVm.restore() }
        LaunchedEffect(session) {
            if (session is SessionState.SignedIn) {
                redirected = false
            } else if (!redirected) {
                redirected = true
                nav.navigate(SupabaseAuth) { launchSingleTop = true }
            }
        }
    }

    // The widget's '+' button wants to open the new-task screen. Single-shot:
    // opened without delay via launchSingleTop; so repeated entries don't pile up.
    LaunchedEffect(pendingWidgetAction) {
        if (pendingWidgetAction == MainActivity.ACTION_NEW_TASK) {
            nav.navigate(TaskDetail("new")) { launchSingleTop = true }
            onWidgetActionConsumed()
        }
    }

    // Developer-activity push tap: open the project once, then consume.
    LaunchedEffect(pendingProjectId) {
        if (pendingProjectId != null) {
            nav.navigate(ProjectDetail(pendingProjectId)) { launchSingleTop = true }
            onProjectConsumed()
        }
    }

    // Scaffolds own the top inset (each screen applies its own topBar/windowInsets);
    // we don't add statusBarsPadding from outside here — avoids double top spacing.
    // Global sync banner above all tabs: takes space when visible, no space at all when Idle.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (FeatureFlags.DRIVE_SYNC_ENABLED) {
            SyncBanner()
        }

        // Screen transitions: a very short (90 ms) fade instead of the default ~300 ms slide+fade.
        // Tab/screen changes thus feel instant, navigation feels "lag-free".
        val fast = tween<Float>(durationMillis = 90)
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(fast) },
            exitTransition = { fadeOut(fast) },
            popEnterTransition = { fadeIn(fast) },
            popExitTransition = { fadeOut(fast) },
        ) {
            composable<Login>         { LoginScreen(nav) }
            composable<SupabaseAuth>  { SupabaseAuthScreen(nav) }
            composable<Home>          {
                HomeScreen(
                    nav = nav,
                    pendingWidgetAction = pendingWidgetAction,
                    pendingWidgetTaskId = pendingWidgetTaskId,
                    onWidgetActionConsumed = onWidgetActionConsumed,
                )
            }
            composable<TaskDetail>    { entry -> TaskDetailScreen(nav, entry.toRoute<TaskDetail>().taskId) }
            composable<Profile>       { ProfileScreen(nav) }
            composable<Settings>      { SettingsScreen(nav) }
            composable<FocusTimer>    { entry -> FocusScreen(nav, entry.toRoute<FocusTimer>().taskId) }
            composable<WorkoutList>   { WorkoutScreen(nav) }
            composable<WorkoutDetail> { entry -> WorkoutDetailScreen(nav, entry.toRoute<WorkoutDetail>().groupId) }
            composable<LiveWorkout>   { entry ->
                val r = entry.toRoute<LiveWorkout>()
                LiveWorkoutScreen(nav, r.workoutId, r.groupId)
            }
            composable<WorkoutSummary> { entry ->
                WorkoutSummaryScreen(nav, entry.toRoute<WorkoutSummary>().sessionId)
            }
            composable<SocialGraph>   { SocialGraphScreen(nav) }
            composable<FriendRequests> { FriendRequestsScreen(nav) }
            composable<UserProfile>   { entry -> UserProfileScreen(entry.toRoute<UserProfile>().username, nav) }
            composable<DuoHub>        { entry -> DuoHubScreen(entry.toRoute<DuoHub>().spaceId, nav) }
            composable<Projects>      { ProjectsScreen(nav) }
            composable<ProjectDetail> { entry -> ProjectDetailScreen(entry.toRoute<ProjectDetail>().spaceId, nav) }
        }
    }
}
