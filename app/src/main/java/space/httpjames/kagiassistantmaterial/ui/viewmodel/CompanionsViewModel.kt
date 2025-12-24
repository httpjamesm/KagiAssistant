package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.KagiCompanion
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import java.io.File

/**
 * UI state for the Companions screen
 */
data class CompanionsUiState(
    val companionsFetchingState: DataFetchingState = DataFetchingState.FETCHING,
    val companions: List<KagiCompanion> = emptyList(),
    val selectedCompanion: String? = null
)

/**
 * ViewModel for the Companions screen.
 * Manages companion selection and data.
 */
class CompanionsViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences,
    private val cacheDir: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionsUiState())
    val uiState: StateFlow<CompanionsUiState> = _uiState.asStateFlow()

    init {
        // Initialize with currently selected companion
        _uiState.update { it.copy(selectedCompanion = getCurrentCompanion()) }
    }

    fun runInit() {
        viewModelScope.launch {
            _uiState.update { it.copy(companionsFetchingState = DataFetchingState.FETCHING) }
            try {
                val companions = repository.getKagiCompanions().drop(1)
                _uiState.update {
                    it.copy(
                        companions = companions,
                        selectedCompanion = getCurrentCompanion(),
                        companionsFetchingState = DataFetchingState.OK
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(companionsFetchingState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun setCompanion(id: String?) {
        if (id == null) {
            prefs.edit().remove("companion").apply()
            _uiState.update { it.copy(selectedCompanion = null) }
            return
        }

        val companion = _uiState.value.companions.firstOrNull { it.id == id } ?: return
        val svgFile = File(cacheDir, "companion_$id.svg")
        svgFile.writeText(companion.data, Charsets.UTF_8)

        prefs.edit().putString("companion", id).apply()
        _uiState.update { it.copy(selectedCompanion = id) }
    }

    fun getCurrentCompanion(): String? {
        return prefs.getString("companion", null)
    }
}
