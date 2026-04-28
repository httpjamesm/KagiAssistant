package space.httpjames.kagiassistantmaterial.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.nameWithoutParentheticals
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import android.content.SharedPreferences
import space.httpjames.kagiassistantmaterial.ui.message.hasBaseVariant
import space.httpjames.kagiassistantmaterial.ui.message.hasReasoningCapability

/**
 * UI state for the ModelBottomSheet screen
 */
data class ModelBottomSheetUiState(
    val profiles: List<AssistantProfile> = emptyList(),
    val profileKeysWithReasoningCapability: Set<String> = emptySet(),
    val profilesCallState: DataFetchingState = DataFetchingState.FETCHING,
    val searchQuery: String = "",
    val selectedProfileId: String? = null,
    val recentlyUsedProfileKeys: List<String> = emptyList()
) {
    /**
     * Profiles filtered by search query
     */
    val filteredProfiles: List<AssistantProfile>
        get() = if (searchQuery.isBlank()) {
            profiles
        } else {
            profiles.filter {
                it.name!!.contains(searchQuery, ignoreCase = true) ||
                        it.family.contains(searchQuery, ignoreCase = true)
            }
        }

    /**
     * Recently used profiles (only shown when not searching)
     */
    val recentlyUsedProfiles: List<AssistantProfile>
        get() = if (searchQuery.isNotBlank()) {
            emptyList()
        } else {
            recentlyUsedProfileKeys.mapNotNull { key -> profiles.find { it.key == key } }
        }

    /**
     * The currently selected profile
     */
    val selectedProfile: AssistantProfile?
        get() {
            val key = selectedProfileId ?: return null
            return profiles.find { it.key == key }
        }
}

/**
 * ViewModel for the ModelBottomSheet screen
 */
class ModelBottomSheetViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ModelBottomSheetUiState(
            selectedProfileId = prefs.getString(PreferenceKey.PROFILE.key, null)
        )
    )
    val uiState: StateFlow<ModelBottomSheetUiState> = _uiState.asStateFlow()

    init {
        syncWithPrefs()
    }

    fun syncWithPrefs() {
        val recentJson = prefs.getString(
            PreferenceKey.RECENTLY_USED_PROFILES.key,
            PreferenceKey.DEFAULT_RECENTLY_USED_PROFILES
        )
        val recentlyUsed = Json.decodeFromString<List<String>>(
            recentJson ?: PreferenceKey.DEFAULT_RECENTLY_USED_PROFILES
        )
        _uiState.update {
            it.copy(
                selectedProfileId = prefs.getString(PreferenceKey.PROFILE.key, null),
                recentlyUsedProfileKeys = recentlyUsed
            )
        }
    }

    fun fetchProfiles() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(profilesCallState = DataFetchingState.FETCHING) }
                val allProfiles = repository.getProfiles()
                val profiles = allProfiles.filter { p ->
                    !p.name!!.contains("(reasoning)") || !p.hasBaseVariant(allProfiles)
                }
                val profileKeysWithReasoningCapability = allProfiles
                    .filter { it.hasReasoningCapability(allProfiles) }
                    .map { it.key }
                    .toSet()
                _uiState.update { it.copy(
                    profiles = profiles,
                    profileKeysWithReasoningCapability = profileKeysWithReasoningCapability,
                    profilesCallState = DataFetchingState.OK
                ) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(profilesCallState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onProfileSelected(profile: AssistantProfile) {
        prefs.edit().putString(PreferenceKey.PROFILE.key, profile.key).apply()
        _uiState.update { it.copy(selectedProfileId = profile.key) }

        // Update recently used list
        val currentList = _uiState.value.recentlyUsedProfileKeys
        val updatedRecentlyUsed = currentList.toMutableList()
        updatedRecentlyUsed.remove(profile.key)
        updatedRecentlyUsed.add(0, profile.key)
        val trimmedList = updatedRecentlyUsed.take(5)
        prefs.edit()
            .putString(PreferenceKey.RECENTLY_USED_PROFILES.key, Json.encodeToString(trimmedList))
            .apply()
        _uiState.update { it.copy(recentlyUsedProfileKeys = trimmedList) }
    }
}
