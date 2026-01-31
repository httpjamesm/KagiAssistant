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
import space.httpjames.kagiassistantmaterial.ui.settings.VoiceOption
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import androidx.core.content.edit

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
    val stickyScrollEnabled: Boolean = PreferenceKey.DEFAULT_STICKY_SCROLL,
    val availableVoices: List<VoiceOption> = emptyList(),
    val selectedTtsVoice: String = PreferenceKey.DEFAULT_TTS_VOICE,
    val selectedTtsVoiceDisplayName: String? = null,
    val showVoiceChooserModal: Boolean = false
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
            ),
            selectedTtsVoice = prefs.getString(PreferenceKey.TTS_VOICE.key, null)
                ?: PreferenceKey.DEFAULT_TTS_VOICE
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
        prefs.edit { putBoolean(PreferenceKey.USE_MINI_OVERLAY.key, newValue) }
    }

    fun showAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = true) }
    }

    fun hideAssistantModelChooser() {
        _uiState.update { it.copy(showAssistantModelChooserModal = false) }
    }

    fun saveAssistantModel(key: String) {
        prefs.edit { putString(PreferenceKey.ASSISTANT_MODEL.key, key) }
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
        prefs.edit { putBoolean(PreferenceKey.OPEN_KEYBOARD_AUTOMATICALLY.key, newValue) }
    }

    fun toggleAutoSpeakReplies() {
        val newValue = !_uiState.value.autoSpeakReplies
        _uiState.update { it.copy(autoSpeakReplies = newValue) }
        prefs.edit { putBoolean(PreferenceKey.AUTO_SPEAK_REPLIES.key, newValue) }
    }

    fun clearAllPrefs() {
        prefs.edit { clear() }
        _uiState.update {
            SettingsUiState(
                autoSpeakReplies = PreferenceKey.DEFAULT_AUTO_SPEAK_REPLIES,
                openKeyboardAutomatically = PreferenceKey.DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY,
                selectedAssistantModel = PreferenceKey.DEFAULT_ASSISTANT_MODEL,
                useMiniOverlay = PreferenceKey.DEFAULT_USE_MINI_OVERLAY,
                stickyScrollEnabled = PreferenceKey.DEFAULT_STICKY_SCROLL,
                selectedTtsVoice = PreferenceKey.DEFAULT_TTS_VOICE,
                selectedTtsVoiceDisplayName = null
            )
        }
    }

    fun toggleStickyScroll() {
        val newValue = !_uiState.value.stickyScrollEnabled
        _uiState.update { it.copy(stickyScrollEnabled = newValue) }
        prefs.edit { putBoolean(PreferenceKey.STICKY_SCROLL.key, newValue) }
    }

    fun setAvailableVoices(voices: List<VoiceOption>) {
        val selectedVoice = _uiState.value.selectedTtsVoice
        val displayName = voices.find { it.name == selectedVoice }?.displayName
        _uiState.update {
            it.copy(availableVoices = voices, selectedTtsVoiceDisplayName = displayName)
        }
    }

    fun showVoiceChooser() {
        _uiState.update { it.copy(showVoiceChooserModal = true) }
    }

    fun hideVoiceChooser() {
        _uiState.update { it.copy(showVoiceChooserModal = false) }
    }

    fun saveTtsVoice(voiceName: String) {
        prefs.edit { putString(PreferenceKey.TTS_VOICE.key, voiceName) }
        val displayName = _uiState.value.availableVoices.find { it.name == voiceName }?.displayName
        _uiState.update {
            it.copy(selectedTtsVoice = voiceName, selectedTtsVoiceDisplayName = displayName)
        }
    }
}
