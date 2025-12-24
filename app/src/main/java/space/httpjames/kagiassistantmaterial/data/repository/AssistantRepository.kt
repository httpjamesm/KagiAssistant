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
import java.io.File

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
    suspend fun deleteChat(threadId: String, onDone: () -> Unit)

    // Message streaming
    suspend fun fetchStream(
        streamId: String,
        url: String,
        body: String?,
        method: String,
        extraHeaders: Map<String, String>,
        onChunk: suspend (StreamChunk) -> Unit
    )

    // Profile operations
    suspend fun getProfiles(): List<AssistantProfile>

    // Companion operations
    suspend fun getKagiCompanions(): List<KagiCompanion>

    // Multipart requests
    suspend fun sendMultipartRequest(
        streamId: String,
        url: String,
        requestBody: space.httpjames.kagiassistantmaterial.KagiPromptRequest,
        files: List<space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile>,
        onChunk: suspend (StreamChunk) -> Unit
    )

    fun getSessionToken(): String
}

/**
 * Implementation of AssistantRepository that wraps AssistantClient.
 * This provides a clean abstraction layer over the existing client.
 */
class AssistantRepositoryImpl(
    private val assistantClient: AssistantClient
) : AssistantRepository {

    override suspend fun checkAuthentication(): Boolean {
        return withContext(Dispatchers.IO) {
            assistantClient.checkAuthentication()
        }
    }

    override suspend fun getQrRemoteSession(): Result<QrRemoteSessionDetails> {
        return withContext(Dispatchers.IO) {
            assistantClient.getQrRemoteSession()
        }
    }

    override suspend fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String> {
        return withContext(Dispatchers.IO) {
            assistantClient.checkQrRemoteSession(details)
        }
    }

    override suspend fun deleteSession(): Boolean {
        return withContext(Dispatchers.IO) {
            assistantClient.deleteSession()
        }
    }

    override suspend fun getAccountEmailAddress(): String {
        return withContext(Dispatchers.IO) {
            assistantClient.getAccountEmailAddress()
        }
    }

    override suspend fun getThreads(): Map<String, List<AssistantThread>> {
        return assistantClient.getThreads()
    }

    override suspend fun deleteChat(threadId: String, onDone: () -> Unit) {
        return assistantClient.deleteChat(threadId, onDone)
    }

    override suspend fun fetchStream(
        streamId: String,
        url: String,
        body: String?,
        method: String,
        extraHeaders: Map<String, String>,
        onChunk: suspend (StreamChunk) -> Unit
    ) {
        return assistantClient.fetchStream(streamId, url, body, method, extraHeaders, onChunk)
    }

    override suspend fun getProfiles(): List<AssistantProfile> {
        return assistantClient.getProfiles()
    }

    override suspend fun getKagiCompanions(): List<KagiCompanion> {
        return withContext(Dispatchers.IO) {
            assistantClient.getKagiCompanions()
        }
    }

    override suspend fun sendMultipartRequest(
        streamId: String,
        url: String,
        requestBody: space.httpjames.kagiassistantmaterial.KagiPromptRequest,
        files: List<space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile>,
        onChunk: suspend (StreamChunk) -> Unit
    ) {
        return assistantClient.sendMultipartRequest(streamId, url, requestBody, files, onChunk)
    }

    override fun getSessionToken(): String {
        return assistantClient.getSessionToken()
    }
}
