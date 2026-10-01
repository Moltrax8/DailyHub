package com.moltrax.personalnoteapp.ui.screen.focus

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(nav: NavController, taskId: String, vm: FocusTimerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val linkedNotice by vm.linkedTaskNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(taskId) { vm.load(taskId) }

    if (state.isCompleted) {
        LaunchedEffect(Unit) { nav.popBackStack() }
    }

    // On a linked task, finishing the counter routes to the completion screen on Home.
    // (Fixed text to avoid touching res; owned-files: ui/** only.)
    LaunchedEffect(linkedNotice) {
        if (linkedNotice) {
            snackbarHostState.showSnackbar(
                "This task is linked to a workout — complete it from Home to log your sets.",
                duration = SnackbarDuration.Long,
            )
            vm.consumeLinkedNotice()
        }
    }

    val accent = AppColors.Accent
    val track  = AppColors.BorderSubtle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.task?.title ?: stringResource(R.string.focus_title)) },
                navigationIcon = { IconButton(onClick = { vm.pause(); nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 20f, cap = StrokeCap.Round)
                    val inset = 10f
                    drawArc(color = track, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = stroke,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2))
                    drawArc(color = accent, startAngle = -90f,
                        sweepAngle = -360f * state.progress,
                        useCenter = false, style = stroke,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2))
                }
                Text(
                    "%02d:%02d".format(state.minutesLeft, state.secondsLeft),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { vm.reset() }) { Icon(Icons.Default.Refresh, null) }
                Button(
                    onClick = { if (state.isRunning) vm.pause() else vm.start() },
                    modifier = Modifier.size(72.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (state.isRunning) R.string.focus_pause else R.string.focus_play), modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
