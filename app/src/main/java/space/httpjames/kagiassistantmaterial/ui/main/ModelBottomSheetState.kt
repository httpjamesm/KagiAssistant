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
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile

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
            try {
                profiles = assistantClient.getProfiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getProfile(): AssistantProfile? {
        val key = selectedProfileId ?: return null

        return profiles.find { it.key == key }
    }

    fun getRecentlyUsedProfiles(): List<AssistantProfile> {
        if (searchQuery.isNotBlank()) {
            return emptyList()
        }
        return recentlyUsed.mapNotNull { key -> profiles.find { it.key == key } }
    }

    fun onProfileSelected(profile: AssistantProfile) {
        prefs.edit().putString("profile", profile.key).apply()
        selectedProfileId = profile.key

        // Update recently used list
        val updatedRecentlyUsed = recentlyUsed.toMutableList()
        updatedRecentlyUsed.remove(profile.key)
        updatedRecentlyUsed.add(0, profile.key)
        val trimmedList = updatedRecentlyUsed.take(5)
        prefs.edit().putString("recently_used_profiles", Json.encodeToString(trimmedList)).apply()
        recentlyUsed = trimmedList
    }
}
