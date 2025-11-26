package space.httpjames.kagiassistantmaterial.ui.message

data class AssistantProfile(
    val key: String,
    val id: String?,
    val model: String,
    val family: String,
    val name: String,
    val maxInputChars: Int,
)
