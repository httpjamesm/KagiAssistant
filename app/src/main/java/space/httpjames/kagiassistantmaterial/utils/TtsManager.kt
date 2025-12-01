package space.httpjames.kagiassistantmaterial.utils


import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import java.util.Locale

class TtsManager(
    private val context: Context,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("TtsManager", "Initialization failed")
            return
        }

        Log.d("TtsManager", "TTS initialized, waiting for text to detect language.")

        selectHighQualityVoiceFor(Locale.US)

        ready = true
    }

    private fun selectHighQualityVoiceFor(locale: Locale) {
        val engine = tts ?: return
        val voices = engine.voices ?: return

        val hqVoice = voices.firstOrNull { v ->
            v.locale == locale &&
                    v.quality in listOf(Voice.QUALITY_HIGH, Voice.QUALITY_VERY_HIGH)
        }

        if (hqVoice != null) {
            engine.voice = hqVoice
            Log.d("TtsManager", "Using high‑quality voice: ${hqVoice.name}")
        } else {
            Log.w("TtsManager", "No high‑quality voice found for $locale")
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        val engine = tts ?: return
        if (!ready) {
            Log.w("TtsManager", "TTS not ready yet")
            return
        }

        // Detect language first
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                val detected = if (langCode != "und") langCode else "en"
                val locale = Locale.forLanguageTag(detected)

                val result = engine.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e("TtsManager", "Detected language not supported: $locale; falling back.")
                } else {
                    Log.d("TtsManager", "Detected language: $locale")
                    selectHighQualityVoiceFor(locale)
                }

                val queueMode =
                    if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

                engine.speak(
                    text,
                    queueMode,
                    null,
                    "tts_utterance_${System.currentTimeMillis()}"
                )
            }
            .addOnFailureListener {
                Log.e("TtsManager", "Language detection failed", it)
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_fallback")
            }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.shutdown()
        tts = null
    }
}
