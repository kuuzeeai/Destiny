package ai.opencode.assist

import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * "Asistente listo": a silent, minimal notification that keeps Destiny's process alive so the
 * power-button assistant can start with the app closed. Turned on/off in Settings › Voice.
 */
class AssistKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shared(SharedKeys.assistKeepAlive).value) {
            stopSelf()
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Asistente listo", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Asistente listo")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } catch (e: Exception) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "assistant_ready"
        private const val NOTIFICATION_ID = 7320

        /** Starts the service when the setting is on. Never throws (Android may refuse from the background). */
        fun start(context: Context) {
            if (!shared(SharedKeys.assistKeepAlive).value) return
            runCatching { context.startForegroundService(Intent(context, AssistKeepAliveService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AssistKeepAliveService::class.java)) }
        }
    }
}

/** Brings "Asistente listo" back after a reboot or an app update. */
class AssistBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AssistKeepAliveService.start(context)
    }
}
