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

@Composable
fun rememberSettingsScreenState(
    assistantClient: AssistantClient
): SettingsScreenState {
    val prefs = LocalContext.current.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
    return remember(prefs, assistantClient) {
        SettingsScreenState(prefs, assistantClient)
    }
}

class SettingsScreenState(
    private val prefs: SharedPreferences,
    private val assistantClient: AssistantClient
) {
    var emailAddress by mutableStateOf("")
        private set
    var emailAddressLoading by mutableStateOf(true)
        private set

    var openKeyboardAutomatically by mutableStateOf(
        prefs.getBoolean(
            "open_keyboard_automatically",
            false
        )
    )
        private set

    fun toggleOpenKeyboardAutomatically() {
        openKeyboardAutomatically = !openKeyboardAutomatically
        prefs.edit().putBoolean("open_keyboard_automatically", openKeyboardAutomatically).apply()
    }

    fun clearAllPrefs() {
        prefs.edit().clear().apply()
    }

    suspend fun runInit() {
        return withContext(Dispatchers.IO) {
            emailAddress = assistantClient.getAccountEmailAddress()
            emailAddressLoading = false
        }
    }


}