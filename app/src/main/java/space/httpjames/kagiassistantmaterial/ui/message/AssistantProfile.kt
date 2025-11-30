package space.httpjames.kagiassistantmaterial.ui.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import space.httpjames.kagiassistantmaterial.utils.JsonLenient

@Serializable
data class AssistantProfile(
    @SerialName("id") val id: String?,
    @SerialName("model") val model: String,
    @SerialName("model_provider") val family: String,
    @SerialName("name") val name: String,
    @SerialName("model_input_limit") val maxInputChars: Int = 40_000
) {
    val key: String get() = id ?: model
}

inline fun <reified T> JsonElement.toObject(): T =
    JsonLenient.decodeFromJsonElement<T>(this)
