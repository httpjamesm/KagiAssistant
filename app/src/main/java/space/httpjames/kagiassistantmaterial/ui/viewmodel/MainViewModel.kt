package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.KagiPromptRequestFocus
import space.httpjames.kagiassistantmaterial.KagiPromptRequestProfile
import space.httpjames.kagiassistantmaterial.KagiPromptRequestThreads
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.parseThreadListHtml
import space.httpjames.kagiassistantmaterial.toObject
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.copyToTempFile
import space.httpjames.kagiassistantmaterial.ui.message.getFileName
import space.httpjames.kagiassistantmaterial.ui.message.to84x84ThumbFile
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ========== Separate UI State Classes ==========

/**
 * UI state for thread list management
 */
data class ThreadsUiState(
    val callState: DataFetchingState = DataFetchingState.FETCHING,
    val threads: Map<String, List<AssistantThread>> = emptyMap(),
    val currentThreadId: String? = null
)

/**
 * UI state for message list and thread operations
 */
data class MessagesUiState(
    val callState: DataFetchingState = DataFetchingState.OK,
    val messages: List<AssistantThreadMessage> = emptyList(),
    val currentThreadTitle: String? = null,
    val editingMessageId: String? = null,
    val isTemporaryChat: Boolean = false,
    val inProgressAssistantMessageId: String? = null
)

/**
 * UI state for MessageCenter component
 */
data class MessageCenterUiState(
    val text: String = "",
    val isSearchEnabled: Boolean = false,
    val showModelBottomSheet: Boolean = false,
    val profiles: List<AssistantProfile> = emptyList(),
    val showAttachmentBottomSheet: Boolean = false,
    val attachmentUris: List<String> = emptyList(),
    val showAttachmentSizeLimitWarning: Boolean = false
)

const val STATE_UPDATE_THROTTLE_MS = 32

/**
 * ViewModel for the Main screen.
 * Manages thread, message, and message center state using separate StateFlows.
 */
class MainViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences,
    private val onTokenReceived: () -> Unit = {}
) : ViewModel() {

    // Threads state
    private val _threadsState = MutableStateFlow(ThreadsUiState())
    val threadsState: StateFlow<ThreadsUiState> = _threadsState.asStateFlow()

    // Messages state
    private val _messagesState = MutableStateFlow(MessagesUiState())
    val messagesState: StateFlow<MessagesUiState> = _messagesState.asStateFlow()

    // MessageCenter state
    private val _messageCenterState = MutableStateFlow(MessageCenterUiState())
    val messageCenterState: StateFlow<MessageCenterUiState> = _messageCenterState.asStateFlow()

    init {
        restoreThread()
        fetchProfiles()
    }

    // ========== Thread operations ==========

    fun fetchThreads() {
        viewModelScope.launch {
            try {
                _threadsState.update { it.copy(callState = DataFetchingState.FETCHING) }
                val threads = repository.getThreads()
                _threadsState.update {
                    it.copy(
                        threads = threads,
                        callState = DataFetchingState.OK
                    )
                }
            } catch (e: Exception) {
                println("Error fetching threads: ${e.message}")
                e.printStackTrace()
                _threadsState.update { it.copy(callState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            val threadId = _threadsState.value.currentThreadId ?: return@launch
            val result = repository.deleteChat(threadId)
            if (result.isSuccess) {
                newChat()
            } else {
                result.exceptionOrNull()?.printStackTrace()
            }
        }
    }

    fun newChat() {
        if (_messagesState.value.isTemporaryChat) {
            _messagesState.update { it.copy(isTemporaryChat = false) }
            deleteChat()
            return
        }
        _messagesState.update {
            it.copy(
                editingMessageId = null,
                messages = emptyList(),
                currentThreadTitle = null
            )
        }
        _threadsState.update { it.copy(currentThreadId = null) }
        prefs.edit().remove(PreferenceKey.SAVED_THREAD_ID.key).apply()
    }

    fun toggleIsTemporaryChat() {
        _messagesState.update { it.copy(isTemporaryChat = !it.isTemporaryChat) }
    }

    fun editMessage(messageId: String) {
        val currentMessages = _messagesState.value.messages
        val index = currentMessages.indexOfFirst { it.id == messageId }

        if (index != -1) {
            val oldContent = currentMessages[index].content
            if (index == 0) {
                newChat()
                _messageCenterState.update { it.copy(text = oldContent) }
                return
            }

            _messagesState.update {
                it.copy(
                    editingMessageId = messageId,
                    messages = currentMessages.subList(0, index)
                )
            }
            _messageCenterState.update { it.copy(text = oldContent) }
        }
    }

    fun onThreadSelected(threadId: String) {
        _threadsState.update {
            it.copy(
                currentThreadId = threadId
            )
        }
        _messagesState.update { it.copy(currentThreadTitle = null) }
        prefs.edit().putString(PreferenceKey.SAVED_THREAD_ID.key, threadId).apply()

        viewModelScope.launch {
            try {
                _messagesState.update { it.copy(callState = DataFetchingState.FETCHING) }
                repository.fetchStream(
                    url = "https://kagi.com/assistant/thread_open",
                    method = "POST",
                    body = """{"focus":{"thread_id":"$threadId"}}""",
                    extraHeaders = mapOf("Content-Type" to "application/json")
                ).collect { chunk ->
                    when (chunk.header) {
                        "thread.json" -> {
                            val thread = Json.parseToJsonElement(chunk.data)
                            val title = thread.jsonObject["title"]?.jsonPrimitive?.content
                            _threadsState.update { it.copy(currentThreadId = threadId) }
                            _messagesState.update { it.copy(currentThreadTitle = title) }
                        }

                        "messages.json" -> {
                            val dtoList = Json.parseToJsonElement(chunk.data)
                                .toObject<List<MessageDto>>()

                            val messages = dtoList.flatMap { dto ->
                                val docs = dto.documents.map { d ->
                                    AssistantThreadMessageDocument(
                                        id = d.id,
                                        name = d.name,
                                        mime = d.mime,
                                        data = if (d.mime.startsWith("image"))
                                            d.data?.decodeDataUriToBitmap()
                                        else null
                                    )
                                }

                                val citations = parseReferencesHtml(dto.references_html)

                                listOf(
                                    AssistantThreadMessage(
                                        id = dto.id,
                                        content = dto.prompt,
                                        role = AssistantThreadMessageRole.USER,
                                        documents = docs,
                                        branchIds = dto.branch_list,
                                        finishedGenerating = true
                                    ),
                                    AssistantThreadMessage(
                                        id = "${dto.id}.reply",
                                        content = dto.reply,
                                        role = AssistantThreadMessageRole.ASSISTANT,
                                        citations = citations,
                                        branchIds = dto.branch_list,
                                        finishedGenerating = true,
                                        markdownContent = dto.md,
                                        metadata = parseMetadata(dto.metadata)
                                    )
                                )
                            }
                            _messagesState.update {
                                it.copy(
                                    messages = messages,
                                    callState = DataFetchingState.OK
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _messagesState.update { it.copy(callState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun restoreThread() {
        val savedId = prefs.getString(PreferenceKey.SAVED_THREAD_ID.key, null)
        if (savedId != null && savedId != _threadsState.value.currentThreadId) {
            onThreadSelected(savedId)
        }
    }

    // ========== Message state getters/setters ==========

    fun setEditingMessageId(id: String?) {
        _messagesState.update { it.copy(editingMessageId = id) }
    }

    fun setCurrentThreadTitle(title: String) {
        _messagesState.update { it.copy(currentThreadTitle = title) }
    }

    fun setCurrentThreadId(id: String?) {
        _threadsState.update { it.copy(currentThreadId = id) }
    }

    fun getEditingMessageId(): String? = _messagesState.value.editingMessageId

    // ========== MessageCenter functions ==========

    private fun fetchProfiles() {
        viewModelScope.launch {
            try {
                _messageCenterState.update { it.copy(profiles = repository.getProfiles()) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val showKeyboardAutomatically: Boolean
        get() = prefs.getBoolean(
            PreferenceKey.OPEN_KEYBOARD_AUTOMATICALLY.key,
            PreferenceKey.DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY
        )

    fun restoreText() {
        _messageCenterState.update {
            it.copy(
                text = prefs.getString(
                    PreferenceKey.SAVED_TEXT.key,
                    PreferenceKey.DEFAULT_SAVED_TEXT
                ) ?: ""
            )
        }
    }

    private fun saveText(newText: String) {
        prefs.edit().putString(PreferenceKey.SAVED_TEXT.key, newText).apply()
    }

    fun onMessageCenterTextChanged(newText: String) {
        _messageCenterState.update { it.copy(text = newText) }
        saveText(newText)
    }

    fun toggleSearch() {
        _messageCenterState.update { it.copy(isSearchEnabled = !it.isSearchEnabled) }
    }

    fun openModelBottomSheet() {
        _messageCenterState.update { it.copy(showModelBottomSheet = true) }
    }

    fun dismissModelBottomSheet() {
        _messageCenterState.update { it.copy(showModelBottomSheet = false) }
    }

    fun getProfile(): AssistantProfile? {
        val key = prefs.getString(PreferenceKey.PROFILE.key, null) ?: return null
        return _messageCenterState.value.profiles.find { it.key == key }
    }

    fun addAttachmentUri(context: Context, uri: String) {
        val totalSize =
            _messageCenterState.value.attachmentUris.sumOf { getFileSize(context, Uri.parse(it)) }
        val newUriSize = getFileSize(context, Uri.parse(uri))

        if (totalSize + newUriSize > 16 * 1024 * 1024) { // 16 MB
            _messageCenterState.update { it.copy(showAttachmentSizeLimitWarning = true) }
            return
        }
        _messageCenterState.update { it.copy(attachmentUris = it.attachmentUris + uri) }
    }

    fun dismissAttachmentSizeLimitWarning() {
        _messageCenterState.update { it.copy(showAttachmentSizeLimitWarning = false) }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun removeAttachmentUri(uri: String) {
        _messageCenterState.update { it.copy(attachmentUris = it.attachmentUris - uri) }
    }

    /**
     * Add multiple shared attachment URIs from an intent (e.g., from Android share sheet).
     * Takes URI permissions to persist access to the shared content.
     */
    fun addSharedAttachmentUris(context: Context, uris: List<Uri>) {
        // Take persistable URI permissions for the shared content
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Permission may already be held or not persistable
                // Continue anyway as the URI might still be accessible
                e.printStackTrace()
            }
        }

        // Add each URI using the existing validation logic
        uris.forEach { uri ->
            addAttachmentUri(context, uri.toString())
        }
    }

    fun onDismissAttachmentBottomSheet() {
        _messageCenterState.update { it.copy(showAttachmentBottomSheet = false) }
    }

    fun showAttachmentBottomSheet() {
        _messageCenterState.update { it.copy(showAttachmentBottomSheet = true) }
    }

    fun sendMessage(context: Context) {
        var messageId = UUID.randomUUID().toString()
        val inProgressId = "$messageId.reply"
        _messagesState.update { it.copy(inProgressAssistantMessageId = inProgressId) }

        // Use MutableList for O(1) index-based updates during streaming
        val localMessages = _messagesState.value.messages.toMutableList()

        val assistantMessages = localMessages.filter { it.role == AssistantThreadMessageRole.USER }
        val latestAssistantMessageId = assistantMessages.lastOrNull()?.id
        val lastMessage = localMessages.lastOrNull()
        val branchIdContext = lastMessage?.branchIds ?: emptyList()

        // Add user message and assistant message
        localMessages += AssistantThreadMessage(
            id = messageId,
            content = _messageCenterState.value.text,
            role = AssistantThreadMessageRole.USER,
            citations = emptyList(),
            branchIds = branchIdContext,
        )
        localMessages += AssistantThreadMessage(
            id = inProgressId,
            content = "",
            role = AssistantThreadMessageRole.ASSISTANT,
            citations = emptyList(),
            branchIds = branchIdContext,
            markdownContent = "",
            metadata = emptyMap(),
        )
        _messagesState.update { it.copy(messages = localMessages.toList()) }

        viewModelScope.launch(Dispatchers.IO) {
            var lastStateUpdateTime = 0L

            val branchId = localMessages.lastOrNull()?.branchIds?.lastOrNull()

            val focus = KagiPromptRequestFocus(
                _threadsState.value.currentThreadId,
                latestAssistantMessageId,
                _messageCenterState.value.text.trim(),
                branchId,
            )

            onMessageCenterTextChanged("")

            try {
                if (_messageCenterState.value.profiles.isEmpty()) {
                    _messageCenterState.update { it.copy(profiles = repository.getProfiles()) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val requestBody = KagiPromptRequest(
                focus,
                KagiPromptRequestProfile(
                    getProfile()?.id,
                    _messageCenterState.value.isSearchEnabled,
                    null,
                    getProfile()?.model ?: "",
                    false,
                ),
                if (getEditingMessageId().isNullOrBlank()) null else listOf(
                    KagiPromptRequestThreads(
                        listOf(),
                        saved = !_messagesState.value.isTemporaryChat,
                        shared = false
                    )
                )
            )

            val url =
                if (!getEditingMessageId().isNullOrBlank()) "https://kagi.com/assistant/message_regenerate" else "https://kagi.com/assistant/prompt"

            setEditingMessageId(null)

            val jsonString =
                Json.encodeToString(KagiPromptRequest.serializer(), requestBody)

            // Track current in-progress ID for efficient lookup
            var currentInProgressId = inProgressId

            // Helper: update message by ID (O(N) find + O(1) update)
            fun updateMessageById(
                targetId: String,
                update: (AssistantThreadMessage) -> AssistantThreadMessage
            ) {
                // check the last element first since it's the most likely target
                val lastIndex = localMessages.lastIndex
                if (lastIndex != -1 && localMessages[lastIndex].id == targetId) {
                    localMessages[lastIndex] = update(localMessages[lastIndex])
                    return
                }
                val index = localMessages.indexOfFirst { it.id == targetId }
                if (index != -1) {
                    localMessages[index] = update(localMessages[index])
                }
            }

            // Helper: throttled state update
            suspend fun maybeUpdateState(force: Boolean = false) {
                val currentTime = System.currentTimeMillis()
                if (force || currentTime - lastStateUpdateTime >= STATE_UPDATE_THROTTLE_MS) {
                    lastStateUpdateTime = currentTime
                    withContext(Dispatchers.Main) {
                        _messagesState.update { it.copy(messages = localMessages.toList()) }
                        onTokenReceived()
                    }
                }
            }

            suspend fun onChunk(chunk: StreamChunk) {
                when (chunk.header) {
                    "thread.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val id = json.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                        if (id != null) setCurrentThreadId(id)
                        val title = json.jsonObject["title"]?.jsonPrimitive?.contentOrNull
                        if (title != null) setCurrentThreadTitle(title)
                    }

                    "location.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val newBranchId = json.jsonObject["branch_id"]?.jsonPrimitive?.contentOrNull
                        if (newBranchId != null) {
                            updateMessageById(currentInProgressId) { msg ->
                                if (newBranchId !in msg.branchIds) {
                                    msg.copy(branchIds = msg.branchIds + newBranchId)
                                } else msg
                            }
                        }
                    }

                    "thread_list.html" -> {
                        _threadsState.update {
                            it.copy(
                                threads = chunk.data.parseThreadListHtml()
                            )
                        }
                    }

                    "new_message.json" -> {
                        val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()

                        // Update user message ID
                        updateMessageById(messageId) { it.copy(id = dto.id) }

                        val newInProgressId = "${dto.id}.reply"
                        currentInProgressId = newInProgressId

                        // Update old in-progress message ID
                        updateMessageById(inProgressId) { it.copy(id = newInProgressId) }

                        messageId = dto.id

                        val preparedCitations = parseReferencesHtml(dto.references_html)

                        // Update assistant message with initial content
                        updateMessageById(newInProgressId) { msg ->
                            msg.copy(
                                content = dto.reply,
                                citations = preparedCitations,
                                markdownContent = dto.md,
                                metadata = parseMetadata(dto.metadata)
                            )
                        }

                        withContext(Dispatchers.Main) {
                            _messagesState.update { it.copy(inProgressAssistantMessageId = newInProgressId) }
                        }
                        maybeUpdateState(force = true)
                    }

                    "tokens.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        val incomingId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        // O(1) update by finding index once
                        val targetId = "$incomingId.reply"
                        updateMessageById(targetId) { it.copy(content = newText) }

                        maybeUpdateState()
                    }
                }

                if (chunk.done) {
                    // Mark in-progress message as finished
                    updateMessageById(currentInProgressId) { it.copy(finishedGenerating = true) }
                }
            }

            if (_messageCenterState.value.attachmentUris.isNotEmpty()) {
                // Move file operations to IO thread
                val files = withContext(Dispatchers.IO) {
                    _messageCenterState.value.attachmentUris.mapNotNull { uriStr ->
                        try {
                            val uri = uriStr.toUri()
                            val fileName = context.getFileName(uri) ?: return@mapNotNull null
                            val file =
                                uri.copyToTempFile(context, "." + fileName.substringAfterLast("."))
                            val thumbnail =
                                if (fileName.endsWith(".webp") || fileName.endsWith(".jpg") || fileName.endsWith(
                                        ".png"
                                    )
                                ) {
                                    file.to84x84ThumbFile()
                                } else null

                            MultipartAssistantPromptFile(
                                file,
                                thumbnail,
                                context.contentResolver.getType(uri) ?: "application/octet-stream"
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }

                // Update messages with document info
                files.forEach { promptFile ->
                    updateMessageById(messageId) { msg ->
                        val fileName = promptFile.file.name
                        msg.copy(
                            documents = msg.documents + AssistantThreadMessageDocument(
                                id = UUID.randomUUID().toString(),
                                name = fileName,
                                mime = promptFile.mime,
                                data =
                                    promptFile.thumbnail?.let { BitmapFactory.decodeFile(it.absolutePath) }

                            )
                        )
                    }
                }

                _messageCenterState.update { it.copy(attachmentUris = emptyList()) }

                try {
                    repository.sendMultipartRequest(
                        url = url,
                        requestBody = requestBody,
                        files = files
                    ).collect { chunk -> onChunk(chunk) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    repository.fetchStream(
                        url = url,
                        method = "POST",
                        body = jsonString,
                        extraHeaders = mapOf("Content-Type" to "application/json")
                    ).collect { chunk -> onChunk(chunk) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Final sync to StateFlow
            withContext(Dispatchers.Main) {
                _messagesState.update {
                    it.copy(
                        messages = localMessages.toList(),
                        inProgressAssistantMessageId = null
                    )
                }
            }
        }
    }
}

fun parseReferencesHtml(html: String): List<Citation> =
    Jsoup.parse(html)
        .select("ol[data-ref-list] > li > a[href]")
        .map { a -> Citation(url = a.attr("abs:href"), title = a.text()) }

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeDataUriToBitmap(): android.graphics.Bitmap {
    val afterPrefix = substringAfter("base64,")
    require(afterPrefix != this) { "Malformed data-URI: missing 'base64,' segment" }

    val decodedBytes = Base64.decode(afterPrefix, 0)
    return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        ?: error("Failed to decode bytes into Bitmap (invalid image data)")
}
