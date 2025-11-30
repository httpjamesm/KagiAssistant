package space.httpjames.kagiassistantmaterial.utils

import kotlinx.serialization.json.Json

val JsonLenient = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
