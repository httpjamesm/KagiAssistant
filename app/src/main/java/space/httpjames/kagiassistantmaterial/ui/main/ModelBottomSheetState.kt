package space.httpjames.kagiassistantmaterial.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.httpjames.kagiassistantmaterial.AssistantClient
import java.util.UUID

data class AssistantProfile(
    val id: String,
    val family: String,
    val name: String,
    val maxInputChars: Int,
)

@Composable
fun rememberModelBottomSheetState(
    assistantClient: AssistantClient,
    coroutineScope: CoroutineScope,
): ModelBottomSheetState {
    val prefs = LocalContext.current.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
    return remember(assistantClient, coroutineScope, prefs) {
        ModelBottomSheetState(prefs, assistantClient, coroutineScope)
    }
}

class ModelBottomSheetState(
    private val prefs: SharedPreferences,
    private val assistantClient: AssistantClient,
    private val coroutineScope: CoroutineScope,
) {
    var profiles by mutableStateOf<List<AssistantProfile>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    private var recentlyUsed by mutableStateOf<List<String>>(emptyList())

    private var selectedProfileId by mutableStateOf(prefs.getString("profile", null))

    val filteredProfiles by derivedStateOf {
        if (searchQuery.isBlank()) {
            profiles
        } else {
            profiles.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.family.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    init {
        val recentJson = prefs.getString("recently_used_profiles", "[]")
        recentlyUsed = Json.decodeFromString<List<String>>(recentJson ?: "[]")
    }

    fun fetchProfiles() {
        coroutineScope.launch {
            val streamId = UUID.randomUUID().toString()
            assistantClient.fetchStream(
                streamId = streamId,
                url = "https://kagi.com/assistant/profile_list",
                method = "POST",
                body = """{}""",
                extraHeaders = mapOf("Content-Type" to "application/json"),
                onChunk = { chunk ->
                    if (chunk.header == "profiles.json") {
                        val json = Json.parseToJsonElement(chunk.data)
                        val nest = json.jsonObject
                        val profilesJson = nest["profiles"]?.jsonArray ?: emptyList()

                        val parsedProfiles = profilesJson.map { profile ->
                            println(profile)
                            val obj = profile.jsonObject
                            AssistantProfile(
                                obj["id"]?.jsonPrimitive?.contentOrNull ?: obj["model"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["model_provider"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["model_input_limit"]?.jsonPrimitive?.int ?: 40000,
                            )
                        }

                        val (kagiProfiles, otherProfiles) = parsedProfiles.partition { it.family.equals("kagi", ignoreCase = true) }
                        this@ModelBottomSheetState.profiles = kagiProfiles + otherProfiles
                    }
                }
            )
        }
    }

    fun getProfile(): AssistantProfile? {
        val id = selectedProfileId ?: return null

        return profiles.find { it.id == id }
    }

    fun getRecentlyUsedProfiles(): List<AssistantProfile> {
        if (searchQuery.isNotBlank()) {
            return emptyList()
        }
        return recentlyUsed.mapNotNull { id -> profiles.find { it.id == id } }
    }

    fun onProfileSelected(profile: AssistantProfile) {
        prefs.edit().putString("profile", profile.id).apply()
        selectedProfileId = profile.id

        // Update recently used list
        val updatedRecentlyUsed = recentlyUsed.toMutableList()
        updatedRecentlyUsed.remove(profile.id)
        updatedRecentlyUsed.add(0, profile.id)
        val trimmedList = updatedRecentlyUsed.take(5)
        prefs.edit().putString("recently_used_profiles", Json.encodeToString(trimmedList)).apply()
        recentlyUsed = trimmedList
    }
}
