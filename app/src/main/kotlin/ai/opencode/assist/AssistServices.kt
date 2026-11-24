package ai.opencode.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Makes Destiny pickable as the digital assistant (power-button hold). */
class AssistInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        instance = this
    }

    override fun onShutdown() {
        if (instance === this) instance = null
        super.onShutdown()
    }

    companion object {
        @Volatile private var instance: AssistInteractionService? = null

        /** Abre el asistente como si mantuvieras el boton de encendido. */
        fun show(): Boolean {
            val s = instance ?: return false
            return runCatching {
                s.showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST)
                true
            }.getOrDefault(false)
        }
    }
}

/** Android exige un RecognitionService para aceptar un asistente; el real es el de Google. */
class AssistRecognitionService : android.speech.RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onStopListening(listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
}

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

/** The Assistant role asks for an ASSIST activity; the real UI is the session's panel. */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
