package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.squareup.moshi.Moshi
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
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.copyToTempFile
import space.httpjames.kagiassistantmaterial.ui.message.getFileName
import space.httpjames.kagiassistantmaterial.ui.message.to84x84ThumbFile
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.KagiPromptRequestFocus
import space.httpjames.kagiassistantmaterial.KagiPromptRequestProfile
import space.httpjames.kagiassistantmaterial.KagiPromptRequestThreads
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.toObject
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import java.io.File
import java.io.FileOutputStream
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

/**
 * ViewModel for the Main screen.
 * Manages thread, message, and message center state using separate StateFlows.
 */
class MainViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences
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
            repository.deleteChat(threadId) { newChat() }
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
        prefs.edit().remove("savedThreadId").apply()
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
        prefs.edit().putString("savedThreadId", threadId).apply()

        viewModelScope.launch {
            try {
                _messagesState.update { it.copy(callState = DataFetchingState.FETCHING) }
                repository.fetchStream(
                    streamId = "8ce77b1b-35c5-4262-8821-af3b33d1cf0f",
                    url = "https://kagi.com/assistant/thread_open",
                    method = "POST",
                    body = """{"focus":{"thread_id":"$threadId"}}""",
                    extraHeaders = mapOf("Content-Type" to "application/json"),
                    onChunk = { chunk ->
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
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _messagesState.update { it.copy(callState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun restoreThread() {
        val savedId = prefs.getString("savedThreadId", null)
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

    fun setThreadMessages(messages: List<AssistantThreadMessage>) {
        _messagesState.update { it.copy(messages = messages) }
    }

    fun setCurrentThreadId(id: String?) {
        _threadsState.update { it.copy(currentThreadId = id) }
    }

    fun getEditingMessageId(): String? = _messagesState.value.editingMessageId
    fun getThreadMessages(): List<AssistantThreadMessage> = _messagesState.value.messages
    fun getCurrentThreadId(): String? = _threadsState.value.currentThreadId
    fun getIsTemporaryChat(): Boolean = _messagesState.value.isTemporaryChat
    fun getCurrentThreadTitle(): String? = _messagesState.value.currentThreadTitle

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
        get() = prefs.getBoolean("open_keyboard_automatically", false)

    fun restoreText() {
        _messageCenterState.update {
            it.copy(text = prefs.getString("savedText", "") ?: "")
        }
    }

    private fun saveText(newText: String) {
        prefs.edit().putString("savedText", newText).apply()
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
        val key = prefs.getString("profile", null) ?: return null
        return _messageCenterState.value.profiles.find { it.key == key }
    }

    fun addAttachmentUri(context: Context, uri: String) {
        val totalSize = _messageCenterState.value.attachmentUris.sumOf { getFileSize(context, Uri.parse(it)) }
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

    fun onDismissAttachmentBottomSheet() {
        _messageCenterState.update { it.copy(showAttachmentBottomSheet = false) }
    }

    fun showAttachmentBottomSheet() {
        _messageCenterState.update { it.copy(showAttachmentBottomSheet = true) }
    }

    fun sendMessage(context: Context) {
        var messageId = UUID.randomUUID().toString()
        _messagesState.update { it.copy(inProgressAssistantMessageId = messageId + ".reply") }

        // Local accumulator - the source of truth during streaming
        var localMessages = _messagesState.value.messages

        val assistantMessages = localMessages.filter { it.role == AssistantThreadMessageRole.USER }
        val latestAssistantMessageId = assistantMessages.takeLast(1).firstOrNull()?.id
        val branchIdContext = localMessages.takeLast(1).firstOrNull()?.branchIds ?: emptyList()

        // Add user message
        localMessages = localMessages + AssistantThreadMessage(
            id = messageId,
            content = _messageCenterState.value.text,
            role = AssistantThreadMessageRole.USER,
            citations = emptyList(),
            branchIds = branchIdContext,
        ) + AssistantThreadMessage(
            id = _messagesState.value.inProgressAssistantMessageId!!,
            content = "",
            role = AssistantThreadMessageRole.ASSISTANT,
            citations = emptyList(),
            branchIds = branchIdContext,
            markdownContent = "",
            metadata = emptyMap(),
        )
        _messagesState.update { it.copy(messages = localMessages) }

        viewModelScope.launch {
            val streamId = UUID.randomUUID().toString()
            var lastTokenUpdateTime = 0L

            // branch lineage is determined as: last message ID's branch ID
            val branchId = localMessages.takeLast(1).firstOrNull()?.branchIds?.lastOrNull()

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

            val moshi = Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val jsonAdapter = moshi.adapter(KagiPromptRequest::class.java)
            val jsonString = jsonAdapter.toJson(requestBody)

            fun onChunk(chunk: StreamChunk) {
                if (chunk.done) {
                    // set finishedGenerating to true
                    localMessages = localMessages.map {
                        if (it.id == _messagesState.value.inProgressAssistantMessageId) {
                            it.copy(finishedGenerating = true)
                        } else {
                            it
                        }
                    }
                }
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
                            // update the inProgressAssistant message with the new branch ID by appending if it doesn't already have it
                            localMessages = localMessages.map {
                                if (it.id == _messagesState.value.inProgressAssistantMessageId && newBranchId !in it.branchIds) {
                                    it.copy(branchIds = it.branchIds + newBranchId)
                                } else {
                                    it
                                }
                            }
                        }
                    }

                    "new_message.json" -> {
                        val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()

                        // update the local message (which will have the old id) with the new id
                        localMessages = localMessages.map {
                            if (it.id == messageId) it.copy(id = dto.id) else it
                        }

                        val newInProgressId = dto.id + ".reply"
                        _messagesState.update { it.copy(inProgressAssistantMessageId = newInProgressId) }

                        localMessages = localMessages.map {
                            if (it.id == "$messageId.reply") it.copy(id = newInProgressId) else it
                        }

                        messageId = dto.id

                        val preparedCitations = parseReferencesHtml(dto.references_html)

                        // Update local accumulator
                        localMessages =
                            localMessages.map {
                                if (it.id == newInProgressId) it.copy(
                                    content = dto.reply,
                                    citations = preparedCitations,
                                    markdownContent = dto.md,
                                    metadata = parseMetadata(dto.metadata)
                                ) else it
                            }

                        _messagesState.update { it.copy(messages = localMessages) }
                    }

                    "tokens.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        val incomingId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        // Always update local accumulator immediately
                        localMessages = localMessages.map {
                            if (it.id == incomingId + ".reply") it.copy(content = newText)
                            else it
                        }

                        // Throttle parent sync for performance
                        val currentTime = System.currentTimeMillis()
                        if ((currentTime - lastTokenUpdateTime) >= 32) {
                            lastTokenUpdateTime = currentTime
                            _messagesState.update { it.copy(messages = localMessages) }
                        }
                    }
                }
            }

            if (_messageCenterState.value.attachmentUris.isNotEmpty()) {
                val files = mutableListOf<MultipartAssistantPromptFile>()

                for (uriStr in _messageCenterState.value.attachmentUris) {
                    val uri = uriStr.toUri()
                    val fileName = context.getFileName(uri) ?: "Unknown"
                    val file = uri.copyToTempFile(context, "." + fileName.substringAfterLast("."))
                    val thumbnail =
                        if (fileName.endsWith(".webp") || fileName.endsWith(".jpg") || fileName.endsWith(
                                ".png"
                            )
                        ) {
                            file.to84x84ThumbFile()
                        } else null
                    var promptFile = MultipartAssistantPromptFile(
                        file,
                        thumbnail,
                        context.contentResolver.getType(uri) ?: "application/octet-stream"
                    )
                    files += promptFile

                    // update the user message to have the document
                    localMessages = localMessages.map {
                        if (it.id == messageId) {
                            it.copy(
                                documents = it.documents + AssistantThreadMessageDocument(
                                    id = UUID.randomUUID().toString(),
                                    name = fileName,
                                    mime = promptFile.mime,
                                    data = if (thumbnail != null) BitmapFactory.decodeFile(thumbnail.absolutePath) else null,
                                )
                            )
                        } else {
                            it
                        }
                    }
                }

                _messageCenterState.update { it.copy(attachmentUris = emptyList()) }

                try {
                    repository.sendMultipartRequest(
                        streamId = streamId,
                        url = url,
                        requestBody = requestBody,
                        files = files,
                        onChunk = ::onChunk
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    repository.fetchStream(
                        streamId = streamId,
                        url = url,
                        method = "POST",
                        body = jsonString,
                        extraHeaders = mapOf("Content-Type" to "application/json"),
                        onChunk = { chunk -> onChunk(chunk) }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Final sync to catch any remaining updates
            withContext(Dispatchers.Main) {
                _messagesState.update { it.copy(messages = localMessages, inProgressAssistantMessageId = null) }
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
