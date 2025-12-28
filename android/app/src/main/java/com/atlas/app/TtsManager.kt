package com.atlas.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(
    context: Context,
    private val locale: Locale = Locale.US,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready: Boolean = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            return
        }

        val engine = tts ?: run {
            ready = false
            return
        }

        val result = engine.setLanguage(locale)
        val ok = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        ready = ok

        if (ok) {
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)
        }
    }

    fun isReady(): Boolean = ready

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) return
        if (text.isBlank()) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "atlas-sample")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
