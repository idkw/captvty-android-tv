package dev.valentin.replaytv.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.valentin.replaytv.MainActivity
import dev.valentin.replaytv.R
import dev.valentin.replaytv.ReplayTvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui maintient l'application en vie pendant les téléchargements
 * (l'utilisateur peut revenir à l'accueil Google TV) et affiche la progression en notification.
 */
@OptIn(FlowPreview::class)
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = ReplayTvApp.from(this).downloads
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Préparation…", 0, true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        scope.launch {
            manager.entries.sample(1000).collectLatest { entries ->
                val running = entries.firstOrNull { it.status is DownloadStatus.Running }
                val queued = entries.count { it.status is DownloadStatus.Queued }
                if (running == null && queued == 0) {
                    stopSelf()
                    return@collectLatest
                }
                val status = running?.status as? DownloadStatus.Running
                val percent = status?.progress?.toInt()?.coerceIn(0, 100) ?: 0
                val text = buildString {
                    append(running?.meta?.title ?: "En attente")
                    if (queued > 0) append(" (+$queued en file)")
                }
                notificationManager().notify(NOTIFICATION_ID, buildNotification(text, percent, status == null || status.progress <= 0f))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(text: String, percent: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Téléchargement de replay")
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
