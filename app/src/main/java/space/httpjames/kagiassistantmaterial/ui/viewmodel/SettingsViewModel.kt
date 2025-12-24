package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState

/**
 * UI state for the Settings screen
 */
data class SettingsUiState(
    val emailAddress: String = "",
    val emailAddressCallState: DataFetchingState = DataFetchingState.FETCHING,
    val autoSpeakReplies: Boolean = true,
    val openKeyboardAutomatically: Boolean = false,
    val profiles: List<AssistantProfile> = emptyList(),
    val showAssistantModelChooserModal: Boolean = false,
    val selectedAssistantModel: String = "gemini-2-5-flash-lite",
    val selectedAssistantModelName: String? = null,
    val useMiniOverlay: Boolean = true
)

/**
 * ViewModel for the Settings screen.
 * Manages user preferences and profile selection.
 */
class SettingsViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            autoSpeakReplies = prefs.getBoolean("auto_speak_replies", true),
            openKeyboardAutomatically = prefs.getBoolean("open_keyboard_automatically", false),
            selectedAssistantModel = prefs.getString("assistant_model", null) ?: "gemini-2-5-flash-lite",
            useMiniOverlay = prefs.getBoolean("use_mini_overlay", true)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun runInit() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(emailAddressCallState = DataFetchingState.FETCHING) }
                val emailAddress = repository.getAccountEmailAddress()
                val profiles = repository.getProfiles()

                _uiState.update {
                    it.copy(
                        emailAddress = emailAddress,
                        emailAddressCallState = DataFetchingState.OK,
                        profiles = profiles,
                        selectedAssistantModelName = profiles
                            .firstOrNull { profile -> profile.key == it.selectedAssistantModel }?.name
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(emailAddressCallState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun toggleUseMiniOverlay() {
        val newValue = !_uiState.value.useMiniOverlay
        _uiState.update { it.copy(useMiniOverlay = newValue) }
        prefs.edit().putBoolean("use_mini_overlay", newValue).apply()
    }

    fun showAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = true) }
    }

    fun hideAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = false) }
    }

    fun saveAssistantModel(key: String) {
        prefs.edit().putString("assistant_model", key).apply()
        _uiState.update {
            it.copy(
                selectedAssistantModel = key,
                selectedAssistantModelName = it.profiles.firstOrNull { profile -> profile.key == key }?.name
            )
        }
    }

    fun toggleOpenKeyboardAutomatically() {
        val newValue = !_uiState.value.openKeyboardAutomatically
        _uiState.update { it.copy(openKeyboardAutomatically = newValue) }
        prefs.edit().putBoolean("open_keyboard_automatically", newValue).apply()
    }

    fun toggleAutoSpeakReplies() {
        val newValue = !_uiState.value.autoSpeakReplies
        _uiState.update { it.copy(autoSpeakReplies = newValue) }
        prefs.edit().putBoolean("auto_speak_replies", newValue).apply()
    }

    fun clearAllPrefs() {
        prefs.edit().clear().apply()
        // Reset state to defaults
        _uiState.update {
            SettingsUiState(
                autoSpeakReplies = true,
                openKeyboardAutomatically = false,
                selectedAssistantModel = "gemini-2-5-flash-lite",
                useMiniOverlay = true
            )
        }
    }
}
