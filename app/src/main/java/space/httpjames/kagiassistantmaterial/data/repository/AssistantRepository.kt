package space.httpjames.kagiassistantmaterial.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.KagiCompanion
import space.httpjames.kagiassistantmaterial.QrRemoteSessionDetails
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile

/**
 * Repository interface for Kagi Assistant data operations.
 * Abstracts the data layer from the UI layer, enabling testing and clean architecture.
 */
interface AssistantRepository {

    // Authentication operations
    suspend fun checkAuthentication(): Boolean
    suspend fun getQrRemoteSession(): Result<QrRemoteSessionDetails>
    suspend fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String>
    suspend fun deleteSession(): Boolean
    suspend fun getAccountEmailAddress(): String

    // Thread operations
    suspend fun getThreads(): Map<String, List<AssistantThread>>
    suspend fun deleteChat(threadId: String): Result<Unit>

    // Message streaming - now returns Flow
    fun fetchStream(
        url: String,
        body: String?,
        method: String,
        extraHeaders: Map<String, String>
    ): Flow<StreamChunk>

    // Profile operations
    suspend fun getProfiles(): List<AssistantProfile>

    // Companion operations
    suspend fun getKagiCompanions(): List<KagiCompanion>

    // Multipart requests - now returns Flow
    fun sendMultipartRequest(
        url: String,
        requestBody: space.httpjames.kagiassistantmaterial.KagiPromptRequest,
        files: List<space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile>
    ): Flow<StreamChunk>

    fun getSessionToken(): String
}

/**
 * Implementation of AssistantRepository that wraps AssistantClient.
 * This provides a clean abstraction layer over the existing client.
 */
class AssistantRepositoryImpl(
    private val assistantClient: AssistantClient
) : AssistantRepository {

    override suspend fun checkAuthentication(): Boolean = withContext(Dispatchers.IO) {
        assistantClient.checkAuthentication()
    }

    override suspend fun getQrRemoteSession(): Result<QrRemoteSessionDetails> =
        withContext(Dispatchers.IO) {
            assistantClient.getQrRemoteSession()
        }

    override suspend fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String> =
        withContext(Dispatchers.IO) {
            assistantClient.checkQrRemoteSession(details)
        }

    override suspend fun deleteSession(): Boolean = withContext(Dispatchers.IO) {
        assistantClient.deleteSession()
    }

    override suspend fun getAccountEmailAddress(): String = withContext(Dispatchers.IO) {
        assistantClient.getAccountEmailAddress()
    }

    override suspend fun getThreads(): Map<String, List<AssistantThread>> =
        withContext(Dispatchers.IO) {
            assistantClient.getThreads() ?: emptyMap()
        }

    override suspend fun deleteChat(threadId: String): Result<Unit> = withContext(Dispatchers.IO) {
        assistantClient.deleteChat(threadId)
    }

    override fun fetchStream(
        url: String,
        body: String?,
        method: String,
        extraHeaders: Map<String, String>
    ): Flow<StreamChunk> {
        return assistantClient.fetchStream(url, body, method, extraHeaders)
    }

    override suspend fun getProfiles(): List<AssistantProfile> = withContext(Dispatchers.IO) {
        assistantClient.getProfiles()
    }

    override suspend fun getKagiCompanions(): List<KagiCompanion> = withContext(Dispatchers.IO) {
        assistantClient.getKagiCompanions()
    }

    override fun sendMultipartRequest(
        url: String,
        requestBody: space.httpjames.kagiassistantmaterial.KagiPromptRequest,
        files: List<space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile>
    ): Flow<StreamChunk> {
        return assistantClient.sendMultipartRequest(url, requestBody, files)
    }

    override fun getSessionToken(): String {
        return assistantClient.getSessionToken()
    }
}
