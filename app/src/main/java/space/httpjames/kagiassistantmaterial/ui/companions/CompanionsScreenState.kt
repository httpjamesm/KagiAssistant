package space.httpjames.kagiassistantmaterial.ui.companions

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
import space.httpjames.kagiassistantmaterial.KagiCompanion
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import java.io.File

@Composable
fun rememberCompanionsScreenState(
    assistantClient: AssistantClient
): CompanionsScreenState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE) }
    val cacheDir = remember { context.cacheDir.absolutePath }

    return remember(prefs, cacheDir) {
        CompanionsScreenState(prefs, cacheDir, assistantClient)
    }
}

class CompanionsScreenState(
    private val prefs: SharedPreferences,
    private val cacheDir: String,
    private val assistantClient: AssistantClient
) {
    var companionsFetchingState by mutableStateOf<DataFetchingState>(DataFetchingState.FETCHING)
        private set

    var companions by mutableStateOf<List<KagiCompanion>>(emptyList())
        private set

    var selectedCompanion by mutableStateOf<String?>(null)
        private set

    suspend fun runInit() {
        return withContext(Dispatchers.IO) {
            companionsFetchingState = DataFetchingState.FETCHING
            try {
                companions = assistantClient.getKagiCompanions().drop(1)
                selectedCompanion = getCurrentCompanion()
                companionsFetchingState = DataFetchingState.OK
            } catch (e: Exception) {
                e.printStackTrace()
                companionsFetchingState = DataFetchingState.ERRORED
            }
        }
    }


    fun setCompanion(id: String?) {
        if (id == null) {
            prefs.edit().remove("companion").apply()
            selectedCompanion = null
            return
        }
        // create a cache svg file with the companion
        val companion = companions.firstOrNull { it.id == id } ?: return
        val svgFile = File(cacheDir, "companion_$id.svg")
        svgFile.writeText(companion.data, Charsets.UTF_8)

        prefs.edit().putString("companion", id).apply()
        selectedCompanion = id
    }

    fun getCurrentCompanion(): String? {
        return prefs.getString("companion", null)
    }
}