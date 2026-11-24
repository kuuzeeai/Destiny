package ai.opencode.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps Destiny alive while the assistant listens, thinks and speaks. A plain low-importance
 * notification: no media card, so the island shows nothing. Stops itself after ten minutes.
 */
class AssistForegroundService : Service() {
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Asistente"
        val short = when {
            label.startsWith("Escuchando") -> "Escucha"
            label.startsWith("Respondiendo") -> "Habla"
            label.startsWith("Preparando") -> "Prepara"
            else -> "Piensa"
        }
        val tint = when {
            label.startsWith("Escuchando") -> 0xFF34C759.toInt()
            label.startsWith("Respondiendo") -> 0xFF2196F3.toInt()
            else -> 0xFFFFB300.toInt()
        }
        val heardText = intent?.getStringExtra(EXTRA_HEARD)?.trim().orEmpty()
        val body = if (label.startsWith("Escuchando") && heardText.isNotEmpty()) heardText.takeLast(90) else "Destiny"
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(dotIcon(tint))
            .setContentTitle(label)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(tint)
        // Super Isla de HyperOS 3 (focus notification): icono del color de la fase.
        runCatching {
            val protocol = android.provider.Settings.System.getInt(contentResolver, "notification_focus_protocol", 0)
            val allowed = runCatching {
                val extras = android.os.Bundle().apply { putString("package", packageName) }
                contentResolver.call(
                    android.net.Uri.parse("content://miui.statusbar.notification.public"),
                    "canShowFocus", null, extras,
                )?.getBoolean("canShowFocus", false) ?: false
            }.getOrDefault(false)
            islandInfo = "p$protocol " + if (allowed) "permiso sí" else "permiso no"
            val text = org.json.JSONObject().put("title", label).put("content", body).put("useHighLight", true)
            val pic = org.json.JSONObject().put("type", 1).put("pic", "miui.focus.pic_island_icon")
            val json = org.json.JSONObject().put(
                "param_v2",
                org.json.JSONObject()
                    .put("protocol", 1)
                    .put("business", "assistant")
                    .put("enableFloat", false)
                    .put("updatable", true)
                    .put("ticker", label)
                    .put("tickerPic", "miui.focus.pic_island_icon")
                    .put(
                        "param_island",
                        org.json.JSONObject()
                            .put("islandProperty", 1)
                            .put(
                                "bigIslandArea",
                                org.json.JSONObject().put(
                                    "imageTextInfoLeft",
                                    org.json.JSONObject().put("type", 1).put("picInfo", pic).put("textInfo", text),
                                ),
                            )
                            .put("smallIslandArea", org.json.JSONObject().put("picInfo", pic)),
                    )
                    .put("baseInfo", org.json.JSONObject().put("title", label).put("content", body).put("type", 2)),
            )
            val pics = android.os.Bundle().apply { putParcelable("miui.focus.pic_island_icon", dotIcon(tint)) }
            builder.addExtras(
                android.os.Bundle().apply {
                    putString("miui.focus.param", json.toString())
                    putBundle("miui.focus.pics", pics)
                },
            )
        }
        val notification = builder.build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 10 * 60 * 1000L)
        return START_NOT_STICKY
    }

    /** Circulo del color de la fase (verde escucha, ambar piensa, azul habla). */
    private fun dotIcon(color: Int): android.graphics.drawable.Icon {
        val size = 96
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        android.graphics.Canvas(bmp).drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)
        return android.graphics.drawable.Icon.createWithBitmap(bmp)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(timeout)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "assistant_live"
        private const val NOTIFICATION_ID = 7319
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_HEARD = "heard"

        /** Estado de la Super Isla, para la linea gris (protocolo y permiso). */
        @Volatile var islandInfo = ""
        private val ui = Handler(Looper.getMainLooper())
        @Volatile private var currentLabel = ""
        @Volatile private var pendingHeard = ""
        @Volatile private var heardScheduled = false
        private const val ACTION_STOP = "ai.opencode.assist.STOP"
        @Volatile private var running = false

        /** Kept for the panel's wiring; nothing outside the panel triggers it now. */
        @Volatile var onInterrupt: (() -> Unit)? = null

        /** Starts the service, or just changes the notification's title. Never throws. */
        fun update(context: Context, label: String) {
            currentLabel = label
            pendingHeard = ""
            send(context, label, null)
        }

        /** Lo que vas diciendo, debajo del titulo. Solo mientras escucha; se quita al pasar a pensar. */
        fun heard(context: Context, text: String) {
            if (!running || !currentLabel.startsWith("Escuchando")) return
            val app = context.applicationContext
            pendingHeard = text
            if (heardScheduled) return
            heardScheduled = true
            ui.postDelayed({
                heardScheduled = false
                if (running && currentLabel.startsWith("Escuchando")) send(app, currentLabel, pendingHeard)
            }, 350)
        }

        private fun send(context: Context, label: String, heard: String?) {
            runCatching {
                context.startForegroundService(
                    Intent(context, AssistForegroundService::class.java)
                        .putExtra(EXTRA_LABEL, label)
                        .putExtra(EXTRA_HEARD, heard),
                )
            }
        }

        fun stop(context: Context) {
            currentLabel = ""
            if (!running) return
            runCatching {
                context.startForegroundService(
                    Intent(context, AssistForegroundService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
