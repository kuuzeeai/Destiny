package ai.opencode.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Makes Destiny pickable as the digital assistant (power-button hold). */
class AssistInteractionService : VoiceInteractionService()

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
