package space.httpjames.kagiassistantmaterial.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState

@Composable
fun rememberSettingsScreenState(assistantClient: AssistantClient): SettingsScreenState {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
    }
    return remember(prefs) {
        SettingsScreenState(prefs, assistantClient)
    }
}

class SettingsScreenState(
    private val prefs: SharedPreferences,
    private val assistantClient: AssistantClient
) {
    var emailAddress by mutableStateOf("")
        private set
    var emailAddressCallState by mutableStateOf<DataFetchingState>(DataFetchingState.FETCHING)
        private set

    var openKeyboardAutomatically by mutableStateOf(
        prefs.getBoolean(
            "open_keyboard_automatically",
            false
        )
    )
        private set

    var profiles by mutableStateOf<List<AssistantProfile>>(emptyList())
        private set

    var showAssistantModelChooserModal by mutableStateOf(false)
        private set
    var selectedAssistantModel by mutableStateOf<String>(
        prefs.getString("assistant_model", null) ?: "gemini-2-5-flash-lite"
    )
        private set
    var selectedAssistantModelName by mutableStateOf<String?>(null)
        private set


    fun showAssistantModelChooser() {
        showAssistantModelChooserModal = true
    }

    fun hideAssistantModelChooser() {
        showAssistantModelChooserModal = false
    }

    fun saveAssistantModel(key: String) {
        prefs.edit().putString("assistant_model", key).apply()
        selectedAssistantModel = key
    }

    fun toggleOpenKeyboardAutomatically() {
        openKeyboardAutomatically = !openKeyboardAutomatically
        prefs.edit().putBoolean("open_keyboard_automatically", openKeyboardAutomatically).apply()
    }

    fun clearAllPrefs() {
        prefs.edit().clear().apply()
    }

    suspend fun runInit() {
        return withContext(Dispatchers.IO) {
            try {
                emailAddressCallState = DataFetchingState.FETCHING
                emailAddress = assistantClient.getAccountEmailAddress()
                emailAddressCallState = DataFetchingState.OK
                profiles = assistantClient.getProfiles()
                selectedAssistantModelName =
                    profiles.firstOrNull { it.key == selectedAssistantModel }?.name
            } catch (e: Exception) {
                emailAddressCallState = DataFetchingState.ERRORED
                e.printStackTrace()
            }
        }
    }


}