package space.httpjames.kagiassistantmaterial

import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.httpjames.kagiassistantmaterial.ui.settings.VoiceOption
import space.httpjames.kagiassistantmaterial.utils.TtsManager
import java.util.Locale

class TtsVoiceFilteringTest {

    @Test
    fun voiceOption_createsCorrectly() {
        val voiceOption = VoiceOption(
            name = "en-au-x-aua-local",
            displayName = "English (Australia) - High",
            locale = Locale.forLanguageTag("en-AU"),
            quality = Voice.QUALITY_HIGH,
            requiresNetwork = false
        )

        assertEquals("en-au-x-aua-local", voiceOption.name)
        assertEquals("English (Australia) - High", voiceOption.displayName)
        assertEquals(Locale.forLanguageTag("en-AU"), voiceOption.locale)
        assertEquals(Voice.QUALITY_HIGH, voiceOption.quality)
        assertEquals(false, voiceOption.requiresNetwork)
    }

    @Test
    fun voiceOptions_sortedByQualityDescending() {
        val voices = listOf(
            createVoiceOption("low", Voice.QUALITY_LOW),
            createVoiceOption("high", Voice.QUALITY_HIGH),
            createVoiceOption("normal", Voice.QUALITY_NORMAL),
            createVoiceOption("veryHigh", Voice.QUALITY_VERY_HIGH)
        )

        val sorted = voices.sortedByDescending { it.quality }

        assertEquals("veryHigh", sorted[0].name)
        assertEquals("high", sorted[1].name)
        assertEquals("normal", sorted[2].name)
        assertEquals("low", sorted[3].name)
    }

    @Test
    fun voiceOptions_filterByQuality() {
        val voices = listOf(
            createVoiceOption("low", Voice.QUALITY_LOW),
            createVoiceOption("high", Voice.QUALITY_HIGH),
            createVoiceOption("veryLow", Voice.QUALITY_VERY_LOW),
            createVoiceOption("normal", Voice.QUALITY_NORMAL)
        )

        val filtered = voices.filter { it.quality >= Voice.QUALITY_NORMAL }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == "high" })
        assertTrue(filtered.any { it.name == "normal" })
    }

    @Test
    fun voiceOptions_preferOfflineVoices() {
        val voices = listOf(
            createVoiceOption("online", Voice.QUALITY_HIGH, requiresNetwork = true),
            createVoiceOption("offline", Voice.QUALITY_HIGH, requiresNetwork = false)
        )

        val sorted = voices.sortedBy { it.requiresNetwork }

        assertEquals("offline", sorted[0].name)
        assertEquals("online", sorted[1].name)
    }

    @Test
    fun voiceOptions_combinedSorting() {
        val voices = listOf(
            createVoiceOption("onlineHigh", Voice.QUALITY_HIGH, requiresNetwork = true),
            createVoiceOption("offlineNormal", Voice.QUALITY_NORMAL, requiresNetwork = false),
            createVoiceOption("offlineHigh", Voice.QUALITY_HIGH, requiresNetwork = false),
            createVoiceOption("onlineVeryHigh", Voice.QUALITY_VERY_HIGH, requiresNetwork = true)
        )

        val sorted = voices.sortedWith(
            compareBy(
                { it.requiresNetwork },
                { -it.quality }
            )
        )

        assertEquals("offlineHigh", sorted[0].name)
        assertEquals("offlineNormal", sorted[1].name)
        assertEquals("onlineVeryHigh", sorted[2].name)
        assertEquals("onlineHigh", sorted[3].name)
    }

    @Test
    fun voiceOptions_filterByLocaleLanguage() {
        val voices = listOf(
            createVoiceOption("enUS", Voice.QUALITY_HIGH, locale = Locale.US),
            createVoiceOption("enAU", Voice.QUALITY_HIGH, locale = Locale.forLanguageTag("en-AU")),
            createVoiceOption("frFR", Voice.QUALITY_HIGH, locale = Locale.FRANCE),
            createVoiceOption("deDE", Voice.QUALITY_HIGH, locale = Locale.GERMANY)
        )

        val englishVoices = voices.filter { it.locale.language == "en" }

        assertEquals(2, englishVoices.size)
        assertTrue(englishVoices.any { it.name == "enUS" })
        assertTrue(englishVoices.any { it.name == "enAU" })
    }

    @Test
    fun getSampleText_returnsEnglishForEnglishLocale() {
        val text = TtsManager.getSampleText(Locale.US)
        assertEquals("Hello! This is a sample of my voice.", text)
    }

    @Test
    fun getSampleText_returnsSpanishForSpanishLocale() {
        val text = TtsManager.getSampleText(Locale.forLanguageTag("es-ES"))
        assertEquals("¡Hola! Esta es una muestra de mi voz.", text)
    }

    @Test
    fun getSampleText_returnsLocalizedTextForSupportedLanguages() {
        val supportedLocales = listOf(
            Locale.FRENCH to "Bonjour!",
            Locale.GERMAN to "Hallo!",
            Locale.ITALIAN to "Ciao!",
            Locale.forLanguageTag("pt-BR") to "Olá!",
            Locale.JAPANESE to "こんにちは！",
            Locale.KOREAN to "안녕하세요!",
            Locale.CHINESE to "你好！",
            Locale.forLanguageTag("ru-RU") to "Привет!"
        )

        for ((locale, expectedStart) in supportedLocales) {
            val text = TtsManager.getSampleText(locale)
            assertTrue(
                "Expected text for ${locale.language} to start with '$expectedStart', got: $text",
                text.startsWith(expectedStart)
            )
        }
    }

    @Test
    fun getSampleText_fallsBackToEnglishForUnsupportedLocale() {
        val text = TtsManager.getSampleText(Locale.forLanguageTag("xx-XX"))
        assertEquals("Hello! This is a sample of my voice.", text)
    }

    @Test
    fun getSampleText_australianEnglishUsesEnglishText() {
        val text = TtsManager.getSampleText(Locale.forLanguageTag("en-AU"))
        assertEquals("Hello! This is a sample of my voice.", text)
    }

    @Test
    fun voiceOption_variantField_defaultsToNull() {
        val voice = createVoiceOption("test", Voice.QUALITY_HIGH)
        assertEquals(null, voice.variant)
    }

    @Test
    fun voiceOption_variantField_canBeSet() {
        val voice = VoiceOption(
            name = "en-au-x-aua-local",
            displayName = "English (Australia) - Voice AUA [High]",
            locale = Locale.forLanguageTag("en-AU"),
            quality = Voice.QUALITY_HIGH,
            requiresNetwork = false,
            variant = "Voice AUA"
        )
        assertEquals("Voice AUA", voice.variant)
    }

    private fun createVoiceOption(
        name: String,
        quality: Int,
        requiresNetwork: Boolean = false,
        locale: Locale = Locale.US
    ): VoiceOption {
        return VoiceOption(
            name = name,
            displayName = "Display $name",
            locale = locale,
            quality = quality,
            requiresNetwork = requiresNetwork
        )
    }
}
