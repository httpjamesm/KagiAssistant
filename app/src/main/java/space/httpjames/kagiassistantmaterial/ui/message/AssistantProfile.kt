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
    @SerialName("name") val name: String?,
    @SerialName("model_name") val modelName: String, // NOT THE SAME AS NAME! this is the server side base layer model name
    @SerialName("model_input_limit") val maxInputChars: Int = 40_000,
    @SerialName("internet_access") val internetAccess: Boolean = false,
) {
    val key: String get() = id ?: model
    val normalizedName: String get() = (name ?: modelName).lowercase()
}

/**
 * Strips all parenthesized text (e.g. "Model (preview) (reasoning)" -> "Model").
 */
fun String.nameWithoutParentheticals(): String =
    replace(Regex("""\s*\([^)]*\)"""), "").trim()

fun AssistantProfile.hasBaseVariant(all: List<AssistantProfile>): Boolean {
    return all.any {
        it.key != this.key &&
                !it.normalizedName.contains("(reasoning)") &&
                it.normalizedName.nameWithoutParentheticals() == this.normalizedName.nameWithoutParentheticals()
    }
}

fun AssistantProfile.hasReasoningCapability(all: List<AssistantProfile>): Boolean {
    return this.normalizedName.contains("(reasoning)") ||
            all.any {
                it.key != this.key &&
                        it.normalizedName.contains("(reasoning)") &&
                        it.normalizedName.nameWithoutParentheticals() == this.normalizedName.nameWithoutParentheticals()
            }
}

fun AssistantProfile.isReasoningModel(): Boolean {
    return this.normalizedName.contains("(reasoning")
}

inline fun <reified T> JsonElement.toObject(): T =
    JsonLenient.decodeFromJsonElement<T>(this)
