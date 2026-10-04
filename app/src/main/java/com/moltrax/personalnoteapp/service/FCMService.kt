package com.moltrax.personalnoteapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.repository.GitHubRepository
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Developer-activity push (Phase 7): GitHub webhook → Edge Function →
 * FCM data message → local notification here. Tap opens the project.
 * New `dev_activity` channel alongside `task_reminders`.
 */
@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    @Inject lateinit var github: GitHubRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(msg: RemoteMessage) {
        if (msg.data["type"] != "github_activity") return
        val spaceId = msg.data["space_id"] ?: return
        val kind = msg.data["kind"].orEmpty()
        val repo = msg.data["repo_full"].orEmpty()
        showDevActivity(spaceId, kind, repo)
    }

    override fun onNewToken(token: String) {
        // Best-effort: skips silently when signed out/unconfigured.
        scope.launch { runCatching { github.registerFcmToken(token) } }
    }

    private fun showDevActivity(spaceId: String, kind: String, repo: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val tap = PendingIntent.getActivity(
            this,
            spaceId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_PROJECT_SPACE_ID, spaceId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = getString(R.string.github_push_title)
        val text = if (repo.isNotBlank()) "$repo • $kind" else kind
        nm.notify(
            spaceId.hashCode(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "dev_activity"
        const val CHANNEL_NAME = "Developer Activity"
    }
}
