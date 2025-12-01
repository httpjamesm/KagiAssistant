package space.httpjames.kagiassistantmaterial.ui.message

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.KagiPromptRequestFocus
import space.httpjames.kagiassistantmaterial.KagiPromptRequestProfile
import space.httpjames.kagiassistantmaterial.KagiPromptRequestThreads
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.ui.main.parseReferencesHtml
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun rememberMessageCenterState(
    assistantClient: AssistantClient,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    threadMessages: List<AssistantThreadMessage> = emptyList(),
    setThreadMessages: (List<AssistantThreadMessage>) -> Unit,
    setCurrentThreadId: (String?) -> Unit,
    text: String,
    setText: (String) -> Unit,
    editingMessageId: String?,
    setEditingMessageId: (String?) -> Unit,
    setCurrentThreadTitle: (String) -> Unit,
): MessageCenterState {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

    val currentThreadMessages = rememberUpdatedState(threadMessages)
    val currentSetThreadMessages = rememberUpdatedState(setThreadMessages)

    val currentEditingMessageId = rememberUpdatedState(editingMessageId)
    val currentSetEditingMessageId = rememberUpdatedState(setEditingMessageId)

    val currentText = rememberUpdatedState(text)
    val currentSetText = rememberUpdatedState(setText)

    val currentSetThreadTitle = rememberUpdatedState(setCurrentThreadTitle)

    return remember(assistantClient, coroutineScope, prefs) {
        MessageCenterState(
            haptics,
            prefs,
            assistantClient,
            coroutineScope,
            { currentThreadMessages.value },
            { currentSetThreadMessages.value(it) },
            setCurrentThreadId,
            context,
            { currentText.value },
            { currentSetText.value(it) },
            { currentEditingMessageId.value },
            { currentSetEditingMessageId.value(it) },
            { currentSetThreadTitle.value(it) },
        )
    }
}

class MessageCenterState(
    private val haptics: HapticFeedback,
    private val prefs: SharedPreferences,
    private val assistantClient: AssistantClient,
    private val coroutineScope: CoroutineScope,
    private val getThreadMessages: () -> List<AssistantThreadMessage>,
    private val setThreadMessages: (List<AssistantThreadMessage>) -> Unit,
    private val setCurrentThreadId: (String?) -> Unit,
    private val context: Context,
    private val getText: () -> String,
    private val setText: (String) -> Unit,
    private val getEditingMessageId: () -> String?,
    private val setEditingMessageId: (String?) -> Unit,
    private val setCurrentThreadTitle: (String) -> Unit,
) {
    var isSearchEnabled by mutableStateOf(false)
        private set

    var inProgressAssistantMessageId by mutableStateOf<String?>(null)
        private set

    var showModelBottomSheet by mutableStateOf(false)
        private set

    var profiles by mutableStateOf<List<AssistantProfile>>(emptyList())
        private set

    var showAttachmentBottomSheet by mutableStateOf(false)
        private set

    var attachmentUris by mutableStateOf<List<String>>(emptyList())
        private set

    var showAttachmentSizeLimitWarning by mutableStateOf(false)
        private set

    val showKeyboardAutomatically = prefs.getBoolean("open_keyboard_automatically", false)

    fun addAttachmentUri(uri: String) {
        val totalSize = attachmentUris.sumOf { getFileSize(Uri.parse(it)) }
        val newUriSize = getFileSize(Uri.parse(uri))

        if (totalSize + newUriSize > 16 * 1024 * 1024) { // 16 MB
            showAttachmentSizeLimitWarning = true
            return
        }
        attachmentUris = attachmentUris + uri
    }

    fun dismissAttachmentSizeLimitWarning() {
        showAttachmentSizeLimitWarning = false
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }


    fun removeAttachmentUri(uri: String) {
        attachmentUris = attachmentUris - uri
    }


    init {
        fetchProfiles()
    }

    fun onDismissAttachmentBottomSheet() {
        showAttachmentBottomSheet = false
    }

    fun showAttachmentBottomSheet() {
        showAttachmentBottomSheet = true
    }


    private fun fetchProfiles() {
        coroutineScope.launch {
            try {
                this@MessageCenterState.profiles = assistantClient.getProfiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreText() {
        setText(prefs.getString("savedText", "") ?: "")
    }

    private fun saveText(newText: String) {
        prefs.edit().putString("savedText", newText).apply()
    }


    fun onTextChanged(newText: String) {
        setText(newText)
        saveText(newText)
    }

    fun toggleSearch() {
        isSearchEnabled = !isSearchEnabled
    }

    fun openModelBottomSheet() {
        showModelBottomSheet = true
    }

    fun dismissModelBottomSheet() {
        showModelBottomSheet = false
    }

    fun getProfile(): AssistantProfile? {
        val key = prefs.getString("profile", null) ?: return null

        return profiles.find { it.key == key }
    }

    fun sendMessage(threadId: String?) {

        var messageId = UUID.randomUUID().toString()
        inProgressAssistantMessageId = messageId

        // Local accumulator - the source of truth during streaming
        var localMessages = getThreadMessages()

        val assistantMessages = localMessages.filter { it.role == AssistantThreadMessageRole.USER }
        val latestAssistantMessageId = assistantMessages.takeLast(1).firstOrNull()?.id
        val branchIdContext = localMessages.takeLast(1).firstOrNull()?.branchIds ?: emptyList()

        // Add user message
        localMessages = localMessages + AssistantThreadMessage(
            id = messageId,
            content = getText(),
            role = AssistantThreadMessageRole.USER,
            citations = emptyList(),
            branchIds = branchIdContext,
        )
        setThreadMessages(localMessages) // Direct call to the constructor param

        coroutineScope.launch {

            val streamId = UUID.randomUUID().toString()
            var lastTokenUpdateTime = 0L

            // branch lineage is determined as: last message ID's branch ID
            val branchId = localMessages.takeLast(1).firstOrNull()?.branchIds?.lastOrNull()

            val focus = KagiPromptRequestFocus(
                threadId,
                latestAssistantMessageId,
                getText().trim(),
                branchId,
            )

            onTextChanged("")

            try {
                if (profiles.isEmpty()) {
                    this@MessageCenterState.profiles = assistantClient.getProfiles()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val requestBody = KagiPromptRequest(
                focus,
                KagiPromptRequestProfile(
                    getProfile()?.id,
                    isSearchEnabled,
                    null,
                    getProfile()?.model ?: "",
                    false,
                ),
                if (!getEditingMessageId().isNullOrBlank()) null else listOf(
                    KagiPromptRequestThreads(listOf(), true, false)
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

//            println("json string: $jsonString")

            fun onChunk(chunk: StreamChunk) {
                if (chunk.done) {
                    // set finishedGenerating to true
                    localMessages = localMessages.map {
                        if (it.id == inProgressAssistantMessageId) {
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
                                if (it.id == inProgressAssistantMessageId && newBranchId !in it.branchIds) {
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

                        inProgressAssistantMessageId = dto.id + ".reply"
                        messageId = dto.id

                        val preparedCitations = parseReferencesHtml(dto.references_html)

                        // Update local accumulator
                        val exists = localMessages.any { it.id == inProgressAssistantMessageId }
                        localMessages = if (exists) {
                            localMessages.map {
                                if (it.id == inProgressAssistantMessageId) it.copy(
                                    content = dto.reply,
                                    citations = preparedCitations,
                                    markdownContent = dto.md,
                                    metadata = parseMetadata(dto.metadata)
                                ) else it
                            }
                        } else {
                            localMessages + AssistantThreadMessage(
                                id = inProgressAssistantMessageId!!,
                                content = dto.reply,
                                role = AssistantThreadMessageRole.ASSISTANT,
                                citations = preparedCitations,
                                branchIds = localMessages.takeLast(1).firstOrNull()?.branchIds
                                    ?: emptyList(),
                                markdownContent = dto.md,
                                metadata = parseMetadata(dto.metadata),
                            )
                        }

                        coroutineScope.launch(Dispatchers.Main.immediate) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            setThreadMessages(localMessages)
                        }
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
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                setThreadMessages(localMessages)
                            }
                        }
                    }
                }
            }

            if (attachmentUris.isNotEmpty()) {
                val files = mutableListOf<File>()
                val thumbnails = mutableListOf<File?>()
                val mimeTypes = mutableListOf<String>()

                for (uriStr in attachmentUris) {
                    val uri = uriStr.toUri()
                    mimeTypes += context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileName = context.getFileName(uri) ?: "Unknown"
                    val file = uri.copyToTempFile(context, "." + fileName.substringAfterLast("."))
                    files += file
                    val thumbnail =
                        if (uriStr.endsWith(".webp") || uriStr.endsWith(".jpg") || uriStr.endsWith(
                                ".png"
                            )
                        ) {
                            file.to84x84ThumbFile()
                        } else null
                    thumbnails += thumbnail

                    // update the user message to have the document
                    localMessages = localMessages.map {
                        if (it.id == messageId) {
                            it.copy(
                                documents = it.documents + AssistantThreadMessageDocument(
                                    id = UUID.randomUUID().toString(),
                                    name = fileName,
                                    mime = mimeTypes.last(),
                                    data = if (thumbnail != null) BitmapFactory.decodeFile(thumbnail.absolutePath) else null,
                                )
                            )
                        } else {
                            it
                        }
                    }
                }

                attachmentUris = emptyList()

                try {
                    assistantClient.sendMultipartRequest(
                        streamId = streamId,
                        url = url,
                        requestBody = requestBody,
                        files = files,
                        thumbnails = thumbnails,
                        mimeTypes = mimeTypes,
                        onChunk = ::onChunk
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    assistantClient.fetchStream(
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
                setThreadMessages(localMessages)
                inProgressAssistantMessageId = null
            }
        }
    }

}

fun File.to84x84ThumbFile(): File {
    val thumb = ThumbnailUtils.createImageThumbnail(this, Size(84, 84), null)

    val outFile = createTempFile("thumb_", ".webp")   // /data/local/tmp/…  (world-writable)
    FileOutputStream(outFile).use { out ->
        thumb.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, out)
    }
    return outFile
}

private fun Uri.copyToTempFile(context: Context, ext: String): File {
    val temp = File.createTempFile("attach_", ext, context.cacheDir)
    context.contentResolver.openInputStream(this).use { ins ->
        temp.outputStream().use { outs ->
            ins?.copyTo(outs)
        }
    }
    return temp
}

fun Context.getFileName(uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)

    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }

    return null
}
