package space.httpjames.kagiassistantmaterial.ui.message

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
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
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.KagiPromptRequestFocus
import space.httpjames.kagiassistantmaterial.KagiPromptRequestProfile
import space.httpjames.kagiassistantmaterial.KagiPromptRequestThreads
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.ui.main.parseReferencesHtml
import java.io.File
import java.util.UUID
import android.media.ThumbnailUtils
import android.provider.OpenableColumns
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

@Composable
fun rememberMessageCenterState(
    assistantClient: AssistantClient,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    threadMessages: List<AssistantThreadMessage> = emptyList(),
    setThreadMessages: (List<AssistantThreadMessage>) -> Unit,
    setCurrentThreadId: (String?) -> Unit,
): MessageCenterState {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

//    val currentThreadMessages = rememberUpdatedState(threadMessages)
//    val currentSetThreadMessages = rememberUpdatedState(setThreadMessages)

    return remember(assistantClient, coroutineScope, prefs) {
        MessageCenterState(
            haptics,
            prefs,
            assistantClient,
            coroutineScope,
            { threadMessages },
//            { currentSetThreadMessages.value(it) }, ,
            setThreadMessages,
            setCurrentThreadId,
            context
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
    private val context: Context
) {
    var text by mutableStateOf("")
        private set

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
            val streamId = UUID.randomUUID().toString()
            assistantClient.fetchStream(
                streamId = streamId,
                url = "https://kagi.com/assistant/profile_list",
                method = "POST",
                body = """{}""",
                extraHeaders = mapOf("Content-Type" to "application/json"),
                onChunk = { chunk ->
                    if (chunk.header == "profiles.json") {
                        val json = Json.parseToJsonElement(chunk.data)
                        val nest = json.jsonObject
                        val profiles = nest["profiles"]?.jsonArray ?: emptyList()

                        this@MessageCenterState.profiles = emptyList()

                        for (profile in profiles) {
                            val obj = profile.jsonObject
                            this@MessageCenterState.profiles += AssistantProfile(
                                obj["model"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["model_provider"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                obj["model_input_limit"]?.jsonPrimitive?.int ?: 40000,
                            )
                        }
                    }
                }
            )
        }
    }

    fun updateThreadMessages(transform: (List<AssistantThreadMessage>) -> List<AssistantThreadMessage>) {
        setThreadMessages(transform(getThreadMessages()))
    }

    fun onTextChanged(newText: String) {
        text = newText
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
        val id = prefs.getString("profile", null) ?: return null

        return profiles.find { it.id == id }
    }

    fun sendMessage(threadId: String?) {
        var messageId = UUID.randomUUID().toString()
        inProgressAssistantMessageId = messageId

        // Local accumulator - the source of truth during streaming
        var localMessages = getThreadMessages()

        // Add user message
        localMessages = localMessages + AssistantThreadMessage(
            id = messageId,
            content = text,
            role = AssistantThreadMessageRole.USER,
        )
        setThreadMessages(localMessages) // Direct call to the constructor param

        coroutineScope.launch {
            val streamId = UUID.randomUUID().toString()
            var lastTokenUpdateTime = 0L

            val assistantMessages = localMessages.filter { it.role == AssistantThreadMessageRole.USER }
            val latestAssistantMessageId = assistantMessages.takeLast(1).firstOrNull()?.id

            val focus = KagiPromptRequestFocus(
                threadId,
                latestAssistantMessageId,
                text,
                "00000000-0000-4000-0000-000000000000",
            )

            text = ""

            val requestBody = KagiPromptRequest(
                focus,
                KagiPromptRequestProfile(
                    null,
                    isSearchEnabled,
                    null,
                    getProfile()?.id ?: "",
                    false,
                ),
                listOf(KagiPromptRequestThreads(listOf(), true, false))
            )

            val url = "https://kagi.com/assistant/prompt"

            val moshi = Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val jsonAdapter = moshi.adapter(KagiPromptRequest::class.java)
            val jsonString = jsonAdapter.toJson(requestBody)

            fun onChunk(chunk: StreamChunk) {
                when (chunk.header) {
                    "thread.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val id = json.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                        if (id != null) setCurrentThreadId(id)
                    }

                    "new_message.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["reply"]?.jsonPrimitive?.contentOrNull ?: ""
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val citationsHtml = obj["references_html"]?.jsonPrimitive?.contentOrNull ?: ""

                        inProgressAssistantMessageId = id
                        messageId = id

                        val preparedCitations = if (citationsHtml.isNotBlank()) {
                            parseReferencesHtml(citationsHtml)
                        } else emptyList()

                        // Update local accumulator
                        val exists = localMessages.any { it.id == id }
                        localMessages = if (exists) {
                            localMessages.map {
                                if (it.id == id) it.copy(content = newText, citations = preparedCitations)
                                else it
                            }
                        } else {
                            localMessages + AssistantThreadMessage(
                                id = id,
                                content = newText,
                                role = AssistantThreadMessageRole.ASSISTANT,
                                citations = preparedCitations,
                            )
                        }

                        // Always sync immediately for structural changes
                        coroutineScope.launch(Dispatchers.Main.immediate) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            setThreadMessages(localMessages)
                            println("new message.json: $localMessages")
                            println("setting thread messages to localMessages")
                        }
                    }

                    "tokens.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        val incomingId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        // Always update local accumulator immediately
                        localMessages = localMessages.map {
                            if (it.id == incomingId) it.copy(content = newText)
                            else it
                        }

                        println(localMessages)

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
                    val uri = Uri.parse(uriStr)
                    mimeTypes += context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileName = context.getFileName(uri) ?: "Unknown"
                    val file = uri.copyToTempFile(context, "." + fileName.substringAfterLast("."))
                    files += file
                    thumbnails += if (uriStr.endsWith(".webp") || uriStr.endsWith(".jpg")) {
                        file.to84x84ThumbFile()
                    } else null
                }

                assistantClient.sendMultipartRequest(
                    streamId = streamId,
                    url = url,
                    requestBody = requestBody,
                    files = files,
                    thumbnails = thumbnails,
                    mimeTypes = mimeTypes,
                    onChunk = ::onChunk
                )
                attachmentUris = emptyList()
            } else {
                assistantClient.fetchStream(
                    streamId = streamId,
                    url = url,
                    method = "POST",
                    body = jsonString,
                    extraHeaders = mapOf("Content-Type" to "application/json"),
                    onChunk = { chunk -> onChunk(chunk) }
                )
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
