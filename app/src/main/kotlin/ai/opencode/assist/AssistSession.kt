package ai.opencode.assist

import ai.opencode.feature.system.assist.AssistRunner
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
    private var visible = false
    private var followUp = false
    private var retries = 0
    private var working = false

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    override fun onCreateContentView(): View {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) 0xFF26262B.toInt() else 0xFFF1F1F4.toInt()
        val fg = if (dark) 0xFFFFFFFF.toInt() else 0xFF1B1B1F.toInt()
        val soft = if (dark) 0xFFB8B8C0.toInt() else 0xFF5F5F66.toInt()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(28).toFloat()
            }
            isClickable = true
        }
        val status = TextView(context).apply {
            textSize = 18f
            setTextColor(fg)
            text = "Escuchando…"
        }
        val heard = TextView(context).apply {
            textSize = 15f
            setTextColor(soft)
            setPadding(0, dp(8), 0, 0)
        }
        val close = TextView(context).apply {
            text = "Cerrar"
            textSize = 15f
            setTextColor(fg)
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                recognizer?.cancel()
                FishSpeech.stop()
                AssistRunner.cancel()
                working = false
                hide()
            }
        }
        card.addView(status)
        card.addView(heard)
        card.addView(close)
        statusView = status
        heardView = heard
        val root = FrameLayout(context)
        root.setOnClickListener { hide() }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        params.setMargins(dp(12), dp(12), dp(12), dp(24))
        root.addView(card, params)
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        FishSpeech.stop()
        AssistRunner.cancel()
        heardView?.text = ""
        visible = true
        followUp = false
        retries = 0
        working = false
        setStatus("Escuchando…")
        startListening()
    }

    override fun onHide() {
        visible = false
        if (!working) AssistForegroundService.stop(context)
        recognizer?.cancel()
        super.onHide()
    }

    override fun onDestroy() {
        AssistForegroundService.stop(context)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun setStatus(text: String) {
        statusView?.text = text
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
        AssistForegroundService.update(context, "Escuchando…")
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setStatus("Este celular no tiene reconocimiento de voz")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Dale permiso de micrófono a Destiny")
            return
        }
        recognizer?.destroy()
        val r = createRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (followUp && quiet) {
                    hide()
                } else if (retries < 2 && (error == 5 || error == 8 || error == 11)) {
                    retries++
                    main.postDelayed({ if (visible) startListening() }, 400)
                } else {
                    setStatus(if (quiet) "No te escuché. Intenta otra vez." else "Error de voz ($error)")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                heardView?.text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) {
                    if (followUp) hide() else setStatus("No te escuché. Intenta otra vez.")
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
        AssistRunner.send(
            text,
            speak = { reply ->
                main.post {
                    FishSpeech.speak(reply) {
                        working = false
                        if (!visible) AssistForegroundService.stop(context)
                        if (visible) {
                            followUp = true
                            heardView?.text = ""
                            setStatus("Te escucho… (o espera para cerrar)")
                            startListening()
                        }
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
                            setStatus("Leyendo la respuesta…")
                            AssistForegroundService.update(context, "Hablando…")
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
