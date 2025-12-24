package space.httpjames.kagiassistantmaterial.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import space.httpjames.kagiassistantmaterial.QrRemoteSessionDetails
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository

/**
 * UI state for the Landing (authentication) screen
 */
data class LandingUiState(
    val authSessionDetails: QrRemoteSessionDetails? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for the Landing screen.
 * Manages QR code authentication flow.
 */
class LandingViewModel(
    private val repository: AssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LandingUiState())
    val uiState: StateFlow<LandingUiState> = _uiState.asStateFlow()

    /**
     * Start the authentication ceremony - generates QR code session
     */
    suspend fun startCeremony(): Result<String> {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        return try {
            val qrRemoteSession = repository.getQrRemoteSession()

            println("QR Remote Session: $qrRemoteSession")


            if (qrRemoteSession.isSuccess) {
                val sessionDetails = qrRemoteSession.getOrNull()
                _uiState.update {
                    it.copy(
                        authSessionDetails = sessionDetails,
                        isLoading = false
                    )
                }
                Result.success(sessionDetails?.token ?: "")
            } else {
                val error = qrRemoteSession.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
                Result.failure(qrRemoteSession.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Check if the QR code has been scanned and authenticated
     * Returns the session token if authenticated, null otherwise
     */
    suspend fun checkCeremony(): String? {
        val sessionDetails = _uiState.value.authSessionDetails ?: return null

        _uiState.update { it.copy(errorMessage = null) }

        return try {
            val checkResult = repository.checkQrRemoteSession(sessionDetails)
            if (checkResult.isSuccess) {
                val token = checkResult.getOrNull()
                if (token != null) {
                    _uiState.update {
                        it.copy(
                            authSessionDetails = null,
                            isLoading = false
                        )
                    }
                    return token
                }
            }
            null
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = e.message
                )
            }
            null
        }
    }

    /**
     * Clear the authentication session details
     */
    fun clearSession() {
        _uiState.update { it.copy(authSessionDetails = null) }
    }
}
