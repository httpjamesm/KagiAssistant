package space.httpjames.kagiassistantmaterial.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.KagiCompanion
import space.httpjames.kagiassistantmaterial.QrRemoteSessionDetails
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.ThreadListResponse
import space.httpjames.kagiassistantmaterial.ThreadPageData
import space.httpjames.kagiassistantmaterial.ThreadSearchResult
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
    suspend fun getThreads(cursor: JsonElement? = null): ThreadListResponse
    suspend fun searchThreads(query: String): List<ThreadSearchResult>
    suspend fun stopGeneration(traceId: String): Result<Unit>
    suspend fun deleteChat(threadId: String): Result<Unit>
    suspend fun fetchThreadPage(threadId: String): ThreadPageData

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

    suspend fun getAutoSave(): Boolean
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

    override suspend fun getThreads(cursor: JsonElement?): ThreadListResponse =
        withContext(Dispatchers.IO) {
            assistantClient.getThreads(cursor) ?: ThreadListResponse(
                threads = emptyMap(),
                nextCursor = null,
                hasMore = false,
                count = 0,
                totalCounts = null,
            )
        }

    override suspend fun searchThreads(query: String): List<ThreadSearchResult> =
        withContext(Dispatchers.IO) {
            assistantClient.searchThreads(query)
        }

    override suspend fun stopGeneration(traceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        assistantClient.stopGeneration(traceId)
    }

    override suspend fun deleteChat(threadId: String): Result<Unit> = withContext(Dispatchers.IO) {
        assistantClient.deleteChat(threadId)
    }

    override suspend fun fetchThreadPage(threadId: String): ThreadPageData =
        assistantClient.fetchThreadPage(threadId)

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

    override suspend fun getAutoSave(): Boolean = withContext(Dispatchers.IO) {
        assistantClient.getAutoSave()
    }
}
