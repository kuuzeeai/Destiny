package ai.opencode.assist

import ai.opencode.feature.system.assist.AssistRunner
import ai.opencode.feature.voice.VoiceBargeIn
import ai.opencode.feature.voice.VoiceListen
import ai.opencode.feature.voice.FishSpeech
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The floating panel of the power-button assistant: listens, sends the message to the last chat in
 * the background, and Fish Audio reads the reply. It never opens the app.
 */
class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var statusView: TextView? = null
    private var heardView: TextView? = null
    private var pillView: View? = null
    private var visible = false
    private var followUp = false
    private var retries = 0
    private var working = false
    private var listening = false
    private var bars: AssistBars? = null
    private var gen = 0
    private var diagView: TextView? = null
    private var startCount = 0
    private var timing = ""
    private var tReady = 0L
    private var tRecEnd = 0L
    private var tText = 0L
    private var listenToken = 0

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    override fun onCreateContentView(): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF000000.toInt())
                cornerRadius = dp(26).toFloat()
            }
            elevation = dp(8).toFloat()
            isClickable = true
            setOnClickListener { if (!listening) interrupt() }
        }
        val barsView = AssistBars(context)
        val status = TextView(context).apply {
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(12), 0, 0, 0)
            text = "Escuchando…"
        }
        val close = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFF9A9AA2.toInt())
            setPadding(dp(10), dp(4), 0, dp(4))
            setOnClickListener {
                recognizer?.cancel()
                VoiceListen.cancel()
                FishSpeech.stop()
                AssistRunner.cancel()
                working = false
                hide()
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(barsView, LinearLayout.LayoutParams(dp(34), dp(26)))
            addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
        }
        val heard = TextView(context).apply {
            textSize = 14f
            maxLines = 2
            setTextColor(0xFFB0B0B8.toInt())
            setPadding(dp(46), dp(4), 0, 0)
        }
        pill.addView(row)
        val diagText = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFF6E6E76.toInt())
            setPadding(dp(46), dp(2), 0, 0)
        }
        heard.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                AssistForegroundService.heard(context, s?.toString().orEmpty())
            }
        })
        pill.addView(heard)
        pill.addView(diagText)
        diagView = diagText
        statusView = status
        heardView = heard
        bars = barsView
        val root = FrameLayout(context)
        val width = minOf(dp(340), context.resources.displayMetrics.widthPixels - dp(32))
        val params = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        params.topMargin = dp(44)
        root.setOnApplyWindowInsetsListener { _, insets ->
            params.topMargin = insets.systemWindowInsetTop + dp(6)
            pill.layoutParams = params
            insets
        }
        root.addView(pill, params)
        pillView = pill
        return root
    }

    /** Cuts the answer (or the wait) and listens again right away. */
    private fun interrupt() {
        VoiceBargeIn.stop()
        recognizer?.cancel()
        VoiceListen.cancel()
        FishSpeech.stop()
        AssistRunner.cancel()
        working = false
        followUp = true
        retries = 0
        heardView?.text = ""
        startListening()
    }

    /** Prueba: la ventana mide solo lo que mide la burbuja y deja pasar los toques a la app de abajo. */
    private fun makeTouchThrough() {
        runCatching {
            val w = window?.window ?: return
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            w.setGravity(Gravity.TOP)
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        makeTouchThrough()
        main.post { makeTouchThrough() }
        pillView?.visibility =
            if (ai.opencode.core.sharing.shared(ai.opencode.core.sharing.SharedKeys.assistIslandOnly).value) View.GONE else View.VISIBLE
        FishSpeech.stop()
        AssistRunner.cancel()
        heardView?.text = ""
        visible = true
        AssistForegroundService.onInterrupt = {
            main.post {
                if (visible) {
                    if (!listening) interrupt()
                } else {
                    FishSpeech.stop()
                    AssistRunner.cancel()
                    working = false
                    AssistForegroundService.stop(context)
                }
            }
        }
        followUp = false
        retries = 0
        working = false
        setStatus("Escuchando…")
        startListening()
    }

    override fun onHide() {
        visible = false
        if (!working) AssistForegroundService.stop(context)
        gen++
        listening = false
        VoiceBargeIn.stop()
        VoiceListen.cancel()
        recognizer?.destroy()
        recognizer = null
        super.onHide()
    }

    override fun onDestroy() {
        AssistForegroundService.onInterrupt = null
        VoiceBargeIn.stop()
        AssistForegroundService.stop(context)
        VoiceListen.cancel()
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun secs(ms: Long): String = String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)

    private fun diag(text: String) {
        diagView?.text = text
    }

    private fun setStatus(text: String) {
        statusView?.text = text
        bars?.setMode(
            when {
                text.startsWith("Escuchando") -> AssistBars.Mode.Listening
                text.startsWith("Pensando") || text.startsWith("Enviando") || text.startsWith("Transcribiendo") -> AssistBars.Mode.Thinking
                text.startsWith("Respondiendo") -> AssistBars.Mode.Speaking
                else -> AssistBars.Mode.Idle
            },
        )
    }

    // Usa otro reconocedor (Google), no el de relleno de Destiny.
    private fun createRecognizer(): SpeechRecognizer {
        val list = context.packageManager
            .queryIntentServices(Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo }
            .filter { it.packageName != context.packageName }
        val svc = list.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
            ?: list.firstOrNull { it.packageName.contains("google") } ?: list.firstOrNull()
        return if (svc != null) {
            SpeechRecognizer.createSpeechRecognizer(context, android.content.ComponentName(svc.packageName, svc.name))
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun startListening() {
        startCount++
        AssistForegroundService.update(context, "Preparando…")
        listening = true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setStatus("Este celular no tiene reconocimiento de voz")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Dale permiso de micrófono a Destiny")
            return
        }
        if (VoiceListen.available) {
            gen++
            recognizer?.destroy()
            recognizer = null
            diag("Groq · intento $startCount · preparando")
            listenWithGroq()
            return
        }
        val stale = recognizer
        recognizer = null
        stale?.destroy()
        diag("Google · intento $startCount · preparando")
        val turn = ++gen
        main.postDelayed({ if (turn == gen && visible) beginRecognizer() }, 300)
    }

    private fun listenWithGroq() {
        val token = ++listenToken
        main.postDelayed({
            if (token == listenToken && visible && listening) {
                VoiceListen.cancel()
                listening = false
                setStatus("No te escuché (se trabó). Toca para reintentar.")
            }
        }, 45000L)
        setStatus("Preparando…")
        VoiceListen.listen(
            onReady = {
                main.post {
                    if (visible) {
                        setStatus("Escuchando…")
                        tReady = android.os.SystemClock.elapsedRealtime()
                        diag("Groq · intento $startCount · grabando")
                        AssistForegroundService.update(context, "Escuchando…")
                    }
                }
            },
            onTranscribing = { main.post { if (visible) { setStatus("Transcribiendo…"); tRecEnd = android.os.SystemClock.elapsedRealtime(); diag("Groq · intento $startCount · transcribiendo") } } },
            onText = { text ->
                main.post {
                    if (visible) {
                        retries = 0
                        tText = android.os.SystemClock.elapsedRealtime()
                        diag("Groq · intento $startCount · texto recibido")
                        send(text)
                    }
                }
            },
            onFail = { kind, why ->
                main.post {
                    if (!visible) return@post
                    listening = false
                    diag("Groq · intento $startCount · fallo: $kind")
                    when (kind) {
                        VoiceListen.Failure.Silence ->
                            if (followUp) hide() else setStatus("No te escuché. Toca para reintentar.")
                        VoiceListen.Failure.Mic -> setStatus("No pude usar el micrófono" + (if (why != null) " ($why)" else "") + ". Toca para reintentar.")
                        VoiceListen.Failure.Network -> setStatus("No pude entender el audio. Toca para reintentar.")
                    }
                }
            },
        )
    }

    private fun beginRecognizer() {
        val r = createRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { diag("Google · intento $startCount · listo, habla") }
            override fun onBeginningOfSpeech() { diag("Google · intento $startCount · oyendo voz") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { diag("Google · intento $startCount · fin de voz") }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                diag("Google · intento $startCount · error $error")
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (followUp && quiet) {
                    hide()
                } else if (retries < 4 && (error == 5 || error == 8 || error == 11)) {
                    retries++
                    main.postDelayed({ if (visible) startListening() }, 500L * retries)
                } else {
                    listening = false
                    setStatus(
                        when {
                            quiet -> "No te escuché. Toca para reintentar."
                            error == 8 -> "Reconocedor ocupado. Toca para reintentar."
                            else -> "Error de voz ($error). Toca para reintentar."
                        },
                    )
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                heardView?.text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) {
                    { listening = false; if (followUp) hide() else setStatus("No te escuché. Toca para reintentar.") }
                } else {
                    retries = 0
                    send(text)
                }
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag()),
        )
    }

    private fun send(text: String) {
        heardView?.text = text
        working = true
        listening = false
        AssistRunner.send(
            text,
            speak = { reply ->
                main.post {
                    FishSpeech.lastError = null
                    FishSpeech.speak(reply) {
                        VoiceBargeIn.stop()
                        val fishErr = FishSpeech.lastError
                        if (fishErr != null) {
                            working = false
                            listening = false
                            if (!visible) AssistForegroundService.stop(context)
                            if (visible) {
                                setStatus("Fish falló. Toca para reintentar.")
                                diag(fishErr.take(140))
                            }
                            return@speak
                        }
                        working = false
                        if (!visible) AssistForegroundService.stop(context)
                        if (visible) {
                            followUp = true
                            heardView?.text = ""
                            setStatus("Escuchando…")
                            // Un momento para que se apague la cola del audio de Fish y no se escuche a sí mismo.
                            main.postDelayed({ if (visible && !working && !listening) startListening() }, 450)
                        }
                    }
                    if (visible) {
                        VoiceBargeIn.start { main.post { if (visible && working && !listening) { interrupt(); followUp = false } } }
                        main.post(object : Runnable {
                            override fun run() {
                                if (visible && working && FishSpeech.active) {
                                    val f = FishSpeech.firstSoundAt
                                    val fish = if (f > 0) secs(f - FishSpeech.startedAt) else "..."
                                    diag("$timing · Fish $fish${FishSpeech.keyLabel} · voz ${VoiceBargeIn.level}/${VoiceBargeIn.threshold} · isla ${AssistForegroundService.islandInfo}")
                                    main.postDelayed(this, 400)
                                }
                            }
                        })
                    }
                }
            },
            onStage = { stage, message ->
                main.post {
                    when (stage) {
                        AssistRunner.Stage.Sending -> {
                            setStatus("Enviando…")
                            AssistForegroundService.update(context, "Pensando…")
                        }
                        AssistRunner.Stage.Waiting -> {
                            setStatus("Pensando…")
                            AssistForegroundService.update(context, "Pensando…")
                        }
                        AssistRunner.Stage.Reading -> {
                            val nowT = android.os.SystemClock.elapsedRealtime()
                            timing = "grabó ${secs(tRecEnd - tReady)} · Groq ${secs(tText - tRecEnd)} · modelo ${secs(nowT - tText)}"
                            diag(timing)
                            setStatus("Respondiendo…")
                            AssistForegroundService.update(context, "Respondiendo…")
                        }
                        AssistRunner.Stage.Failed -> {
                            working = false
                            val msg = message ?: "Algo falló"
                            setStatus(msg)
                            FishSpeech.speak(msg) { if (!visible) AssistForegroundService.stop(context) }
                        }
                    }
                }
            },
        )
    }
}
