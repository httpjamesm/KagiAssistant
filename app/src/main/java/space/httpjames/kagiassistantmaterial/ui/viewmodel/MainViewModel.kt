package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.toObject
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * UI state for the Main screen
 */
data class MainUiState(
    val threadsCallState: DataFetchingState = DataFetchingState.FETCHING,
    val threads: Map<String, List<AssistantThread>> = emptyMap(),
    val currentThreadId: String? = null,
    val threadMessagesCallState: DataFetchingState = DataFetchingState.OK,
    val threadMessages: List<AssistantThreadMessage> = emptyList(),
    val currentThreadTitle: String? = null,
    val editingMessageId: String? = null,
    val messageCenterText: String = "",
    val isTemporaryChat: Boolean = false
)

/**
 * ViewModel for the Main screen.
 * Manages thread and message state, business logic for chat operations.
 */
class MainViewModel(
    private val repository: AssistantRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        restoreThread()
    }

    fun fetchThreads() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(threadsCallState = DataFetchingState.FETCHING) }
                val threads = repository.getThreads()
                _uiState.update {
                    it.copy(
                        threads = threads,
                        threadsCallState = DataFetchingState.OK
                    )
                }
            } catch (e: Exception) {
                println("Error fetching threads: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(threadsCallState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            val threadId = _uiState.value.currentThreadId ?: return@launch
            repository.deleteChat(threadId) { newChat() }
        }
    }

    fun newChat() {
        val currentState = _uiState.value
        if (currentState.isTemporaryChat) {
            _uiState.update { it.copy(isTemporaryChat = false) }
            deleteChat()
            return
        }
        _uiState.update {
            it.copy(
                editingMessageId = null,
                currentThreadId = null,
                currentThreadTitle = null,
                threadMessages = emptyList()
            )
        }
        prefs.edit().remove("savedThreadId").apply()
    }

    fun toggleIsTemporaryChat() {
        _uiState.update { it.copy(isTemporaryChat = !it.isTemporaryChat) }
    }

    fun editMessage(messageId: String) {
        val currentMessages = _uiState.value.threadMessages
        val index = currentMessages.indexOfFirst { it.id == messageId }

        if (index != -1) {
            val oldContent = currentMessages[index].content
            if (index == 0) {
                newChat()
                _uiState.update { it.copy(messageCenterText = oldContent) }
                return
            }

            _uiState.update {
                it.copy(
                    editingMessageId = messageId,
                    threadMessages = currentMessages.subList(0, index),
                    messageCenterText = oldContent
                )
            }
        }
    }

    fun onThreadSelected(threadId: String) {
        _uiState.update {
            it.copy(
                currentThreadId = threadId,
                currentThreadTitle = null
            )
        }
        prefs.edit().putString("savedThreadId", threadId).apply()

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(threadMessagesCallState = DataFetchingState.FETCHING) }
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
                                _uiState.update { it.copy(currentThreadTitle = title) }
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
                                _uiState.update {
                                    it.copy(
                                        threadMessages = messages,
                                        threadMessagesCallState = DataFetchingState.OK
                                    )
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(threadMessagesCallState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun restoreThread() {
        val savedId = prefs.getString("savedThreadId", null)
        if (savedId != null && savedId != _uiState.value.currentThreadId) {
            onThreadSelected(savedId)
        }
    }

    // Public setters for MessageCenter to update state
    fun setEditingMessageId(id: String?) {
        _uiState.update { it.copy(editingMessageId = id) }
    }

    fun setMessageCenterText(text: String) {
        _uiState.update { it.copy(messageCenterText = text) }
    }

    fun setCurrentThreadId(id: String?) {
        _uiState.update { it.copy(currentThreadId = id) }
    }

    fun setCurrentThreadTitle(title: String) {
        _uiState.update { it.copy(currentThreadTitle = title) }
    }

    fun setThreadMessages(messages: List<AssistantThreadMessage>) {
        _uiState.update { it.copy(threadMessages = messages) }
    }

    fun getEditingMessageId(): String? = _uiState.value.editingMessageId
    fun getMessageCenterText(): String = _uiState.value.messageCenterText
    fun getThreadMessages(): List<AssistantThreadMessage> = _uiState.value.threadMessages
    fun getCurrentThreadId(): String? = _uiState.value.currentThreadId
    fun getIsTemporaryChat(): Boolean = _uiState.value.isTemporaryChat
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
