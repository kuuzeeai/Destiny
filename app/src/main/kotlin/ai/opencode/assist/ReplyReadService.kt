package ai.opencode.assist

import ai.opencode.feature.session.ReplyWatcher
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/** Mantiene Destiny despierta mientras espera y lee una respuesta del chat. */
class ReplyReadService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Lectura de respuestas", NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ReplyReadService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Destiny")
            .setContentText("Esperando o leyendo la respuesta")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Detener", stop).build())
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (intent?.action == ACTION_STOP) {
            ReplyWatcher.stop()
            stopSelf()
        } else if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "destiny:reply")
                .apply { acquire(40 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "reply_reading"
        private const val NOTIFICATION_ID = 7320
        private const val ACTION_STOP = "ai.opencode.assist.REPLY_STOP"

        fun install(context: Context) {
            val app = context.applicationContext
            ReplyWatcher.hold = { on ->
                val intent = Intent(app, ReplyReadService::class.java)
                runCatching { if (on) app.startForegroundService(intent) else app.stopService(intent) }
            }
        }
    }
}
