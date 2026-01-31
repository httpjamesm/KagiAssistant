package space.httpjames.kagiassistantmaterial.utils

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import space.httpjames.kagiassistantmaterial.ui.settings.VoiceOption
import java.util.Locale

class TtsManager(
    context: Context,
    private val prefs: SharedPreferences? = null,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {},
    private val onReady: () -> Unit = {},
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var selectedVoiceName: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("TtsManager", "Initialization failed")
            return
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("TtsManager", "Speech started: $utteranceId")
                onStart()
            }

            override fun onDone(utteranceId: String?) {
                Log.d("TtsManager", "Speech completed: $utteranceId")
                onDone()
            }

            // SDK 21 requirement for deprecated function signature
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                handleError(utteranceId, null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleError(utteranceId, errorCode)
            }

            private fun handleError(utteranceId: String?, errorCode: Int?) {
                val codeStr = errorCode?.let { " (code: $it)" } ?: ""
                Log.e("TtsManager", "Speech error $codeStr: $utteranceId")
            }
        })

        Log.d("TtsManager", "TTS initialized successfully")

        val savedVoice = prefs?.getString(PreferenceKey.TTS_VOICE.key, null)
        if (!savedVoice.isNullOrEmpty() && setVoice(savedVoice)) {
            // Successfully restored saved voice
        } else {
            if (!savedVoice.isNullOrEmpty()) {
                Log.w("TtsManager", "Saved voice '$savedVoice' no longer available, using default")
            }
            selectHighQualityVoiceFor(Locale.getDefault())
        }

        ready = true
        onReady()
    }

    fun isReady(): Boolean = ready

    fun getAvailableVoices(): List<VoiceOption> {
        if (!ready) {
            Log.w("TtsManager", "getAvailableVoices called before TTS ready")
            return emptyList()
        }
        val voices = tts?.voices
        if (voices == null) {
            Log.w("TtsManager", "TTS voices is null")
            return emptyList()
        }
        Log.d("TtsManager", "Found ${voices.size} total voices")
        val filtered = voices.filter { it.quality >= Voice.QUALITY_NORMAL }
        Log.d("TtsManager", "After filtering: ${filtered.size} voices with quality >= NORMAL")

        // Group by locale to detect when we need variant labels
        val voicesByLocale = filtered.groupBy { it.locale }

        return filtered
            .sortedWith(
                compareBy(
                    { it.isNetworkConnectionRequired },
                    { -it.quality },
                    { it.locale.displayName }
                )
            )
            .map { voice ->
                val variant = extractVariant(voice)
                val needsVariant = voicesByLocale[voice.locale]?.size?.let { it > 1 } == true
                VoiceOption(
                    name = voice.name,
                    displayName = formatDisplayName(voice, if (needsVariant) variant else null),
                    locale = voice.locale,
                    quality = voice.quality,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    variant = variant
                )
            }
    }

    private fun extractVariant(voice: Voice): String? {
        val name = voice.name.lowercase()

        // Check for explicit gender markers in voice name
        if (name.contains("female") || name.contains("-f-") || name.contains("_f_")) {
            return "Female"
        }
        if (name.contains("male") || name.contains("-m-") || name.contains("_m_")) {
            return "Male"
        }

        // Google TTS voices use patterns like "en-au-x-aua-local"
        // Extract the variant code (e.g., "aua", "aub") for disambiguation
        val variantPattern = Regex("""-x-([a-z]+)-""")
        val match = variantPattern.find(name)
        if (match != null) {
            val code = match.groupValues[1]
            // Return a cleaned-up version - these are opaque IDs but help distinguish voices
            return "Voice ${code.uppercase()}"
        }

        // Samsung and other engines may use different patterns
        val numericVariant = Regex("""[_-](\d+)$""").find(name)
        if (numericVariant != null) {
            return "Voice ${numericVariant.groupValues[1]}"
        }

        return null
    }

    fun setVoice(voiceName: String): Boolean {
        val engine = tts ?: return false
        val voices = engine.voices ?: return false

        val voice = voices.find { it.name == voiceName }
        if (voice != null) {
            engine.voice = voice
            selectedVoiceName = voiceName
            Log.d("TtsManager", "Voice set to: ${voice.name}")
            return true
        }
        Log.w("TtsManager", "Voice not found: $voiceName")
        return false
    }

    private fun formatDisplayName(voice: Voice, variant: String? = null): String {
        val locale = voice.locale
        val language = locale.displayLanguage
        val country = locale.displayCountry.takeIf { it.isNotEmpty() }
        val qualityLabel = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> "Very High"
            Voice.QUALITY_HIGH -> "High"
            else -> null
        }

        return buildString {
            append(language)
            if (country != null) append(" ($country)")
            if (variant != null) append(" - $variant")
            if (qualityLabel != null) append(" [$qualityLabel]")
        }
    }

    fun resetToDefaultVoice() {
        selectedVoiceName = null
        selectHighQualityVoiceFor(Locale.getDefault())
    }

    private fun selectHighQualityVoiceFor(locale: Locale) {
        val engine = tts ?: return
        val voices = engine.voices ?: return

        val hqVoice = voices
            .filter { v ->
                v.locale.language == locale.language &&
                        v.quality in listOf(Voice.QUALITY_HIGH, Voice.QUALITY_VERY_HIGH)
            }
            .sortedWith(
                compareBy(
                    { it.isNetworkConnectionRequired },
                    { -it.quality },
                    { if (it.locale.country == locale.country) 0 else 1 }
                )
            )
            .firstOrNull()

        if (hqVoice != null) {
            engine.voice = hqVoice
            selectedVoiceName = hqVoice.name
            Log.d("TtsManager", "Using high-quality voice: ${hqVoice.name}")
        } else {
            Log.w("TtsManager", "No high-quality voice found for $locale")
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        val engine = tts ?: return
        if (!ready) {
            Log.w("TtsManager", "TTS not ready yet")
            return
        }

        if (selectedVoiceName != null) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, queueMode, null, "tts_utterance_${System.currentTimeMillis()}")
            return
        }

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
                engine.speak(text, queueMode, null, "tts_utterance_${System.currentTimeMillis()}")
            }
            .addOnFailureListener {
                Log.e("TtsManager", "Language detection failed", it)
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_fallback")
            }
    }

    fun previewVoice(voiceName: String) {
        val engine = tts ?: return
        if (!ready) return

        stop()

        if (voiceName.isEmpty()) {
            // Preview device default voice
            selectHighQualityVoiceFor(Locale.getDefault())
            val sampleText = getSampleText(Locale.getDefault())
            engine.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "tts_preview")
            return
        }

        val voices = engine.voices ?: return
        val voice = voices.find { it.name == voiceName } ?: return

        engine.voice = voice

        val sampleText = getSampleText(voice.locale)
        engine.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "tts_preview")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.shutdown()
        tts = null
    }

    companion object {
        fun getSampleText(locale: Locale): String {
            return when (locale.language) {
                "en" -> "Hello! This is a sample of my voice."
                "es" -> "¡Hola! Esta es una muestra de mi voz."
                "fr" -> "Bonjour! Ceci est un échantillon de ma voix."
                "de" -> "Hallo! Dies ist eine Probe meiner Stimme."
                "it" -> "Ciao! Questo è un campione della mia voce."
                "pt" -> "Olá! Esta é uma amostra da minha voz."
                "ja" -> "こんにちは！これは私の声のサンプルです。"
                "ko" -> "안녕하세요! 이것은 제 목소리 샘플입니다."
                "zh" -> "你好！这是我的声音样本。"
                "ru" -> "Привет! Это образец моего голоса."
                else -> "Hello! This is a sample of my voice."
            }
        }
    }
}
