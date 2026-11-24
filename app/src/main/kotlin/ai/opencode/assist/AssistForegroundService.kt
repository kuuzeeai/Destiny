package ai.opencode.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps Destiny alive while the assistant listens, thinks and speaks, and shows that state as a
 * media card (the kind the island shows for music). It stops itself after ten minutes.
 */
class AssistForegroundService : Service() {
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Asistente de voz", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "DestinyAssistant").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() { onInterrupt?.invoke() }
                override fun onStop() { onInterrupt?.invoke() }
                override fun onPlay() { onInterrupt?.invoke() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Asistente"
        val s = session
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(label)
            .setContentText("Destiny")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (s != null) {
            s.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, label)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Destiny")
                    .build(),
            )
            s.setPlaybackState(PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE)
                    .build())
            builder.setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken))
        }
        startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 10 * 60 * 1000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(timeout)
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "assistant_state"
        private const val NOTIFICATION_ID = 7319
        private const val EXTRA_LABEL = "label"
        private const val ACTION_STOP = "ai.opencode.assist.STOP"
        @Volatile private var running = false

        /** Set by the panel: the media card's pause/stop button interrupts the assistant. */
        @Volatile var onInterrupt: (() -> Unit)? = null

        /** Starts the service, or just changes the card's title. Never throws. */
        fun update(context: Context, label: String) {
            runCatching {
                context.startForegroundService(
                    Intent(context, AssistForegroundService::class.java).putExtra(EXTRA_LABEL, label),
                )
            }
        }

        fun stop(context: Context) {
            if (!running) return
            runCatching {
                context.startForegroundService(
                    Intent(context, AssistForegroundService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
