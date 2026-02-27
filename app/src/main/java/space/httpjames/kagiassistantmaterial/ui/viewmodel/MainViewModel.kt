package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import space.httpjames.kagiassistantmaterial.ThreadSearchResult
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.parseThreadListHtml
import space.httpjames.kagiassistantmaterial.parseThreadListJsonWrapper
import space.httpjames.kagiassistantmaterial.toObject
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.copyToTempFile
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
    val currentThreadId: String? = null,
    val nextCursor: JsonElement? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ThreadSearchResult>? = null,
    val isSearching: Boolean = false,
    val isLoadingSearchPages: Boolean = false,
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

private data class ThreadSession(
    val threadId: String?,
    val messages: MutableList<AssistantThreadMessage> = mutableListOf(),
    val currentThreadTitle: String? = null,
    val callState: DataFetchingState = DataFetchingState.OK,
    val isTemporaryChat: Boolean = false,
    val inProgressAssistantMessageId: String? = null,
    val streamingJob: Job? = null
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
    onTokenReceived: () -> Unit = {}
) : ViewModel() {

    private var _onTokenReceived: () -> Unit = onTokenReceived

    fun setOnTokenReceived(callback: () -> Unit) {
        _onTokenReceived = callback
    }

    // Threads state
    private val _threadsState = MutableStateFlow(ThreadsUiState())
    val threadsState: StateFlow<ThreadsUiState> = _threadsState.asStateFlow()

    // Per-thread sessions
    private val threadSessions = mutableMapOf<String, ThreadSession>()

    // Messages state
    private val _messagesState = MutableStateFlow(MessagesUiState())
    val messagesState: StateFlow<MessagesUiState> = _messagesState.asStateFlow()

    private val _generatingThreadsState = MutableStateFlow<Set<String>>(emptySet())
    val generatingThreadsState: StateFlow<Set<String>> = _generatingThreadsState.asStateFlow()

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
                val response = repository.getThreads()
                _threadsState.update {
                    it.copy(
                        threads = response.threads,
                        nextCursor = response.nextCursor,
                        hasMore = response.hasMore,
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

    fun loadMoreThreads() {
        val state = _threadsState.value
        if (state.isLoadingMore || !state.hasMore || state.nextCursor == null) return

        viewModelScope.launch {
            try {
                _threadsState.update { it.copy(isLoadingMore = true) }
                val response = repository.getThreads(cursor = state.nextCursor)

                _threadsState.update { current ->
                    val mergedThreads = current.threads.toMutableMap()
                    for ((category, newThreads) in response.threads) {
                        val existing = mergedThreads[category]?.toMutableList() ?: mutableListOf()
                        existing.addAll(newThreads)
                        mergedThreads[category] = existing
                    }
                    current.copy(
                        threads = mergedThreads,
                        nextCursor = response.nextCursor,
                        hasMore = response.hasMore,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _threadsState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private var searchJob: Job? = null

    fun searchThreads(query: String) {
        _threadsState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()

        if (query.isBlank()) {
            _threadsState.update { it.copy(searchResults = null, isSearching = false, isLoadingSearchPages = false) }
            return
        }

        // For short queries, do client-side filtering only
        if (query.length < 3) {
            _threadsState.update { it.copy(searchResults = null, isSearching = false, isLoadingSearchPages = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _threadsState.update { it.copy(isSearching = true, isLoadingSearchPages = false) }
            try {
                // Fire search API call immediately
                val results = repository.searchThreads(query)
                _threadsState.update { it.copy(
                    searchResults = results,
                    isSearching = false,
                    isLoadingSearchPages = _threadsState.value.hasMore
                ) }

                // Load remaining pages in background so more matches appear progressively
                while (_threadsState.value.hasMore && _threadsState.value.nextCursor != null) {
                    val cursor = _threadsState.value.nextCursor
                    val response = repository.getThreads(cursor = cursor)
                    _threadsState.update { current ->
                        val merged = current.threads.toMutableMap()
                        for ((category, newThreads) in response.threads) {
                            val existing = merged[category]?.toMutableList() ?: mutableListOf()
                            existing.addAll(newThreads)
                            merged[category] = existing
                        }
                        current.copy(
                            threads = merged,
                            nextCursor = response.nextCursor,
                            hasMore = response.hasMore,
                            isLoadingSearchPages = response.hasMore,
                        )
                    }
                }

                _threadsState.update { it.copy(isLoadingSearchPages = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                _threadsState.update { it.copy(searchResults = emptyList(), isSearching = false, isLoadingSearchPages = false) }
            }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            val threadId = _threadsState.value.currentThreadId ?: return@launch
            val result = repository.deleteChat(threadId)
            if (result.isSuccess) {
                threadSessions.entries.firstOrNull { it.value.threadId == threadId }?.let { entry ->
                    entry.value.streamingJob?.cancel()
                    threadSessions.remove(entry.key)
                    updateGeneratingThreadsState()
                }
                newChat()
            } else {
                result.exceptionOrNull()?.printStackTrace()
            }
        }
    }

    fun newChat() {
        val currentSession = currentSessionKey?.let { threadSessions[it] }
        if (currentSession?.isTemporaryChat == true) {
            updateSession(currentSessionKey) { it.copy(isTemporaryChat = false) }
            deleteChat()
            return
        }
        val tempId = "temp:${UUID.randomUUID()}"
        threadSessions[tempId] = ThreadSession(threadId = null)
        updateGeneratingThreadsState()
        setActiveSession(tempId)
        _threadsState.update { it.copy(currentThreadId = null) }
        prefs.edit().remove(PreferenceKey.SAVED_THREAD_ID.key).apply()
    }

    fun toggleIsTemporaryChat() {
        val sessionKey = currentSessionKey ?: run {
            val tempId = "temp:${UUID.randomUUID()}"
            threadSessions[tempId] = ThreadSession(threadId = null)
            updateGeneratingThreadsState()
            setActiveSession(tempId)
            tempId
        }
        updateSession(sessionKey) { session ->
            session.copy(isTemporaryChat = !session.isTemporaryChat)
        }
    }

    fun editMessage(messageId: String) {
        val sessionKey = currentSessionKey
        val currentMessages = sessionKey?.let { threadSessions[it]?.messages } ?: _messagesState.value.messages
        val index = currentMessages.indexOfFirst { it.id == messageId }

        if (index != -1) {
            val oldContent = currentMessages[index].content
            if (index == 0) {
                newChat()
                _messageCenterState.update { it.copy(text = oldContent) }
                return
            }

            if (sessionKey != null) {
                val trimmedMessages = currentMessages.subList(0, index).toMutableList()
                updateSession(sessionKey) { it.copy(messages = trimmedMessages) }
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
        val existingKey = threadSessions.entries.firstOrNull { it.value.threadId == threadId }?.key
        if (existingKey != null) {
            setActiveSession(existingKey)
            prefs.edit().putString(PreferenceKey.SAVED_THREAD_ID.key, threadId).apply()
            return
        }

        val sessionKey = threadId
        threadSessions[sessionKey] = ThreadSession(threadId = threadId, callState = DataFetchingState.FETCHING)
        setActiveSession(sessionKey)
        prefs.edit().putString(PreferenceKey.SAVED_THREAD_ID.key, threadId).apply()

        viewModelScope.launch {
            try {
                val pageData = repository.fetchThreadPage(threadId)

                updateSession(sessionKey) { it.copy(currentThreadTitle = pageData.title) }

                val dtoList = Json.parseToJsonElement(pageData.messagesJson)
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
                updateSession(sessionKey) {
                    it.copy(
                        messages = messages.toMutableList(),
                        callState = DataFetchingState.OK
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateSession(sessionKey) { it.copy(callState = DataFetchingState.ERRORED) }
            }
        }
    }

    fun restoreThread() {
        val savedId = prefs.getString(PreferenceKey.SAVED_THREAD_ID.key, null)
        if (savedId != null && savedId != _threadsState.value.currentThreadId) {
            onThreadSelected(savedId)
        } else if (activeSessionKey == null && threadSessions.isNotEmpty()) {
            setActiveSession(threadSessions.keys.first())
        }
    }

    // ========== Message state getters/setters ==========

    fun setEditingMessageId(id: String?) {
        _messagesState.update { it.copy(editingMessageId = id) }
    }

    private var activeSessionKey: String? = null

    private val currentSessionKey: String?
        get() = activeSessionKey

    private fun setActiveSession(sessionKey: String) {
        val session = threadSessions[sessionKey] ?: return
        activeSessionKey = sessionKey
        val threadId = session.threadId
        _threadsState.update { it.copy(currentThreadId = threadId) }
        _messagesState.update {
            it.copy(
                messages = session.messages.toList(),
                currentThreadTitle = session.currentThreadTitle,
                callState = session.callState,
                isTemporaryChat = session.isTemporaryChat,
                inProgressAssistantMessageId = session.inProgressAssistantMessageId
            )
        }
    }

    private fun updateMessagesStateFromSession(sessionKey: String) {
        if (activeSessionKey != sessionKey) {
            return
        }
        val session = threadSessions[sessionKey] ?: return
        _messagesState.update {
            it.copy(
                messages = session.messages.toList(),
                currentThreadTitle = session.currentThreadTitle,
                callState = session.callState,
                isTemporaryChat = session.isTemporaryChat,
                inProgressAssistantMessageId = session.inProgressAssistantMessageId
            )
        }
    }

    private fun updateSession(sessionKey: String?, update: (ThreadSession) -> ThreadSession) {
        if (sessionKey == null) return
        val session = threadSessions[sessionKey] ?: return
        threadSessions[sessionKey] = update(session)
        updateMessagesStateFromSession(sessionKey)
        updateGeneratingThreadsState()
    }

    private fun migrateSession(oldKey: String, newThreadId: String): String {
        val session = threadSessions[oldKey] ?: return oldKey
        threadSessions.remove(oldKey)
        threadSessions[newThreadId] = session.copy(threadId = newThreadId)
        updateGeneratingThreadsState()
        if (activeSessionKey == oldKey) {
            setActiveSession(newThreadId)
        }
        return newThreadId
    }

    private fun setCurrentThreadTitle(title: String) {
        updateSession(currentSessionKey) { it.copy(currentThreadTitle = title) }
        if (currentSessionKey != null) {
            _messagesState.update { it.copy(currentThreadTitle = title) }
        }
    }

    private fun setCurrentThreadId(id: String?) {
        _threadsState.update { it.copy(currentThreadId = id) }
        if (currentSessionKey != null) {
            updateSession(currentSessionKey) { it.copy(threadId = id) }
        }
    }

    private fun shouldPlayTokenHaptic(sessionKey: String): Boolean {
        val session = threadSessions[sessionKey] ?: return false
        return sessionKey == activeSessionKey && session.inProgressAssistantMessageId != null
    }

    fun getEditingMessageId(): String? = _messagesState.value.editingMessageId

    private fun updateGeneratingThreadsState() {
        _generatingThreadsState.value = threadSessions.values
            .filter { it.inProgressAssistantMessageId != null && it.threadId != null }
            .mapNotNull { it.threadId }
            .toSet()
    }

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
        val sessionKey = currentSessionKey ?: run {
            val tempId = "temp:${UUID.randomUUID()}"
            threadSessions[tempId] = ThreadSession(threadId = null)
            updateGeneratingThreadsState()
            setActiveSession(tempId)
            tempId
        }
        val session = threadSessions[sessionKey] ?: return
        var messageId = UUID.randomUUID().toString()
        val inProgressId = "$messageId.reply"

        val localMessages = session.messages
        val assistantMessages = localMessages.filter { it.role == AssistantThreadMessageRole.USER }
        val latestAssistantMessageId = assistantMessages.lastOrNull()?.id
        val lastMessage = localMessages.lastOrNull()
        val branchIdContext = lastMessage?.branchIds ?: emptyList()

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
        updateSession(sessionKey) { it.copy(inProgressAssistantMessageId = inProgressId) }
        updateMessagesStateFromSession(sessionKey)

        val streamingJob = viewModelScope.launch(Dispatchers.IO) {
            var lastStateUpdateTime = 0L
            var activeSessionKey = sessionKey
            val branchId = localMessages.lastOrNull()?.branchIds?.lastOrNull()

            val focus = KagiPromptRequestFocus(
                session.threadId,
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
                        saved = !session.isTemporaryChat,
                        shared = false
                    )
                )
            )

            val url =
                if (!getEditingMessageId().isNullOrBlank()) "https://kagi.com/assistant/message_regenerate" else "https://kagi.com/assistant/prompt"

            setEditingMessageId(null)

            val jsonString = Json.encodeToString(KagiPromptRequest.serializer(), requestBody)

            var currentInProgressId = inProgressId

            fun updateMessageById(
                targetId: String,
                update: (AssistantThreadMessage) -> AssistantThreadMessage
            ) {
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

            suspend fun maybeUpdateState(force: Boolean = false) {
                val currentTime = System.currentTimeMillis()
                if (force || currentTime - lastStateUpdateTime >= STATE_UPDATE_THROTTLE_MS) {
                    lastStateUpdateTime = currentTime
                    withContext(Dispatchers.Main) {
                        updateMessagesStateFromSession(activeSessionKey)
                        if (shouldPlayTokenHaptic(activeSessionKey)) {
                            _onTokenReceived()
                        }
                    }
                }
            }

            suspend fun onChunk(chunk: StreamChunk) {
                when (chunk.header) {
                    "thread.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val id = json.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                        if (id != null) {
                            if (activeSessionKey.startsWith("temp:")) {
                                activeSessionKey = migrateSession(activeSessionKey, id)
                            }
                            setCurrentThreadId(id)
                        }
                        val title = json.jsonObject["title"]?.jsonPrimitive?.contentOrNull
                        if (title != null && id != null) {
                            updateSession(activeSessionKey) { it.copy(currentThreadTitle = title) }
                            _threadsState.update { state ->
                                state.copy(
                                    threads = state.threads.mapValues { (_, threads) ->
                                        threads.map { thread ->
                                            if (thread.id == id) {
                                                thread.copy(title = title)
                                            } else {
                                                thread
                                            }
                                        }
                                    }
                                )
                            }
                        }
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
                        val response = parseThreadListJsonWrapper(chunk.data)
                        _threadsState.update {
                            it.copy(
                                threads = response.threads,
                                nextCursor = response.nextCursor,
                                hasMore = response.hasMore,
                            )
                        }
                    }

                    "new_message.json" -> {
                        val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()

                        updateMessageById(messageId) { it.copy(id = dto.id) }

                        val newInProgressId = "${dto.id}.reply"
                        currentInProgressId = newInProgressId

                        updateMessageById(inProgressId) { it.copy(id = newInProgressId) }

                        messageId = dto.id

                        val preparedCitations = parseReferencesHtml(dto.references_html)

                        if (dto.state == "done") {
                            updateMessageById(newInProgressId) { msg ->
                                msg.copy(
                                    content = dto.reply,
                                    citations = preparedCitations,
                                    markdownContent = dto.md,
                                    metadata = parseMetadata(dto.metadata)
                                )
                            }
                        }

                        updateSession(activeSessionKey) { it.copy(inProgressAssistantMessageId = newInProgressId) }
                        maybeUpdateState(force = true)
                    }

                    "tokens.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        val incomingId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        val targetId = "$incomingId.reply"
                        updateMessageById(targetId) { it.copy(content = newText) }

                        maybeUpdateState()
                    }
                }

                if (chunk.done) {
                    updateMessageById(currentInProgressId) { it.copy(finishedGenerating = true) }
                }
            }

            if (_messageCenterState.value.attachmentUris.isNotEmpty()) {
                val files = withContext(Dispatchers.IO) {
                    _messageCenterState.value.attachmentUris.mapNotNull { uriStr ->
                        try {
                            val uri = uriStr.toUri()
                            val mimeType =
                                context.contentResolver.getType(uri) ?: "application/octet-stream"

                            val extension =
                                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                                    ?: "tmp"
                            val file = uri.copyToTempFile(context, ".$extension")

                            val thumbnail = if (mimeType.startsWith("image/", ignoreCase = true)) {
                                file.to84x84ThumbFile()
                            } else {
                                null
                            }

                            MultipartAssistantPromptFile(file, thumbnail, mimeType)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }

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

            withContext(Dispatchers.Main) {
                updateSession(activeSessionKey) { it.copy(inProgressAssistantMessageId = null) }
                updateMessagesStateFromSession(activeSessionKey)
            }
        }

        updateSession(sessionKey) { it.copy(streamingJob = streamingJob) }
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
