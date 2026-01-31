package space.httpjames.kagiassistantmaterial.ui.settings

import java.util.Locale

data class VoiceOption(
    val name: String,
    val displayName: String,
    val locale: Locale,
    val quality: Int,
    val requiresNetwork: Boolean,
    val variant: String? = null
)
