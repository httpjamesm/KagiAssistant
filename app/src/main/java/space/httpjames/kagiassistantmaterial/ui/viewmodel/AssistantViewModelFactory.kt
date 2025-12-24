package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepositoryImpl

/**
 * Factory for creating ViewModels with dependencies.
 * Provides proper dependency injection for all ViewModels.
 */
class AssistantViewModelFactory(
    private val assistantClient: AssistantClient,
    private val prefs: SharedPreferences,
    private val cacheDir: String
) : ViewModelProvider.Factory {

    private val repository: AssistantRepository by lazy {
        AssistantRepositoryImpl(assistantClient)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            MainViewModel::class.java -> MainViewModel(repository, prefs) as T
            SettingsViewModel::class.java -> SettingsViewModel(repository, prefs) as T
            CompanionsViewModel::class.java -> CompanionsViewModel(repository, prefs, cacheDir) as T
            LandingViewModel::class.java -> LandingViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * Extension function to easily create ViewModels with the factory.
 *
 * Usage in Composable:
 * ```
 * val viewModel: MainViewModel = viewModel(factory = AssistantViewModelFactory(client, prefs, cacheDir))
 * ```
 */
inline fun <reified VM : ViewModel> viewModelProvider(
    assistantClient: AssistantClient,
    prefs: SharedPreferences,
    cacheDir: String
): ViewModelProvider.Factory {
    return AssistantViewModelFactory(assistantClient, prefs, cacheDir)
}
