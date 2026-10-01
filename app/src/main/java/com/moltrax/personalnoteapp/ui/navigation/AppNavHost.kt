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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.moltrax.personalnoteapp.ui.screen.auth.LoginScreen
import com.moltrax.personalnoteapp.ui.screen.focus.FocusScreen
import com.moltrax.personalnoteapp.ui.screen.home.HomeScreen
import com.moltrax.personalnoteapp.ui.screen.profile.ProfileScreen
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsScreen
import com.moltrax.personalnoteapp.ui.screen.task.TaskDetailScreen
import com.moltrax.personalnoteapp.ui.screen.workout.LiveWorkoutScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutDetailScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutScreen
import com.moltrax.personalnoteapp.ui.screen.workout.WorkoutSummaryScreen

@Composable
fun AppNavHost(
    // Drive sync kapalıyken giriş ekranını atla, doğrudan ana ekrandan başla.
    startDestination: Any = if (FeatureFlags.DRIVE_SYNC_ENABLED) Login else Home,
    pendingWidgetAction: String? = null,
    pendingWidgetTaskId: String? = null,
    onWidgetActionConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    // Ön plana gelince (arka plandan dönüş) sessiz otomatik senkronizasyon. Böylece ikinci bir
    // cihazda yapılan değişiklikler, uygulamaya geri dönüldüğünde otomatik çekilir. İlk ON_START
    // soğuk başlangıçtır; açılış senkronizasyonunu HomeViewModel.init zaten yapar, bu yüzden
    // yalnızca SONRAKİ ön plana gelişlerde tetikleriz (çift sync'i önler). Giriş yapılmamışsa
    // repository sessizce no-op döner.
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

    // Widget'ın '+' butonu yeni görev ekranını açmak ister. Tek seferlik (single-shot):
    // beklemeden launchSingleTop ile açılır; böylece yinelenen girişlerde üst üste yığılmaz.
    LaunchedEffect(pendingWidgetAction) {
        if (pendingWidgetAction == MainActivity.ACTION_NEW_TASK) {
            nav.navigate(TaskDetail("new")) { launchSingleTop = true }
            onWidgetActionConsumed()
        }
    }

    // Üst inset'i Scaffolds'lar sahiplenir (her ekran kendi topBar/windowInsets'ini uygular);
    // burada dıştan statusBarsPadding eklemiyoruz — çift üst boşluğu önler.
    // Global sync banner'ı tüm sekmelerin üstünde: görünürken yer kaplar, Idle'da hiç yer kaplamaz.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (FeatureFlags.DRIVE_SYNC_ENABLED) {
            SyncBanner()
        }

        // Ekran geçişleri: varsayılan ~300 ms kaydır+solma yerine çok kısa (90 ms) bir solma.
        // Böylece sekme/ekran değişimi gözle anlık algılanır, gezinme "gecikmesiz" hissettirir.
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
        }
    }
}
