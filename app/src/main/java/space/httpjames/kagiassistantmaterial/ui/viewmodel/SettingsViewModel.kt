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
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey

/**
 * UI state for the Settings screen
 */
data class SettingsUiState(
    val emailAddress: String = "",
    val emailAddressCallState: DataFetchingState = DataFetchingState.FETCHING,
    val autoSpeakReplies: Boolean = PreferenceKey.DEFAULT_AUTO_SPEAK_REPLIES,
    val openKeyboardAutomatically: Boolean = PreferenceKey.DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY,
    val profiles: List<AssistantProfile> = emptyList(),
    val showAssistantModelChooserModal: Boolean = false,
    val selectedAssistantModel: String = PreferenceKey.DEFAULT_ASSISTANT_MODEL,
    val selectedAssistantModelName: String? = null,
    val useMiniOverlay: Boolean = PreferenceKey.DEFAULT_USE_MINI_OVERLAY,
    val stickyScrollEnabled: Boolean = PreferenceKey.DEFAULT_STICKY_SCROLL
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
            autoSpeakReplies = prefs.getBoolean(
                PreferenceKey.AUTO_SPEAK_REPLIES.key,
                PreferenceKey.DEFAULT_AUTO_SPEAK_REPLIES
            ),
            openKeyboardAutomatically = prefs.getBoolean(
                PreferenceKey.OPEN_KEYBOARD_AUTOMATICALLY.key,
                PreferenceKey.DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY
            ),
            selectedAssistantModel = prefs.getString(PreferenceKey.ASSISTANT_MODEL.key, null)
                ?: PreferenceKey.DEFAULT_ASSISTANT_MODEL,
            useMiniOverlay = prefs.getBoolean(
                PreferenceKey.USE_MINI_OVERLAY.key,
                PreferenceKey.DEFAULT_USE_MINI_OVERLAY
            ),
            stickyScrollEnabled = prefs.getBoolean(
                PreferenceKey.STICKY_SCROLL.key,
                PreferenceKey.DEFAULT_STICKY_SCROLL
            )
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        fetchEmailAddress()
    }

    private fun fetchEmailAddress() {
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
        prefs.edit().putBoolean(PreferenceKey.USE_MINI_OVERLAY.key, newValue).apply()
    }

    fun showAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = true) }
    }

    fun hideAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = false) }
    }

    fun saveAssistantModel(key: String) {
        prefs.edit().putString(PreferenceKey.ASSISTANT_MODEL.key, key).apply()
        _uiState.update {
            it.copy(
                selectedAssistantModel = key,
                selectedAssistantModelName = it.profiles.firstOrNull { profile -> profile.key == key }?.name
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            if (repository.deleteSession())
                clearAllPrefs()
        }
    }

    fun toggleOpenKeyboardAutomatically() {
        val newValue = !_uiState.value.openKeyboardAutomatically
        _uiState.update { it.copy(openKeyboardAutomatically = newValue) }
        prefs.edit().putBoolean(PreferenceKey.OPEN_KEYBOARD_AUTOMATICALLY.key, newValue).apply()
    }

    fun toggleAutoSpeakReplies() {
        val newValue = !_uiState.value.autoSpeakReplies
        _uiState.update { it.copy(autoSpeakReplies = newValue) }
        prefs.edit().putBoolean(PreferenceKey.AUTO_SPEAK_REPLIES.key, newValue).apply()
    }

    fun clearAllPrefs() {
        prefs.edit().clear().apply()
        // Reset state to defaults
        _uiState.update {
            SettingsUiState(
                autoSpeakReplies = PreferenceKey.DEFAULT_AUTO_SPEAK_REPLIES,
                openKeyboardAutomatically = PreferenceKey.DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY,
                selectedAssistantModel = PreferenceKey.DEFAULT_ASSISTANT_MODEL,
                useMiniOverlay = PreferenceKey.DEFAULT_USE_MINI_OVERLAY,
                stickyScrollEnabled = PreferenceKey.DEFAULT_STICKY_SCROLL
            )
        }
    }

    fun toggleStickyScroll() {
        val newValue = !_uiState.value.stickyScrollEnabled
        _uiState.update { it.copy(stickyScrollEnabled = newValue) }
        prefs.edit().putBoolean(PreferenceKey.STICKY_SCROLL.key, newValue).apply()
    }
}
