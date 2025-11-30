package space.httpjames.kagiassistantmaterial.ui.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.parseMetadata
import space.httpjames.kagiassistantmaterial.toObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun rememberMainState(
    assistantClient: AssistantClient,
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MainState = remember(assistantClient, drawerState, coroutineScope) {
    MainState(assistantClient, drawerState, coroutineScope)
}

class MainState(
    private val assistantClient: AssistantClient,
    val drawerState: DrawerState,
    val coroutineScope: CoroutineScope
) {
    var threadsLoading by mutableStateOf(false)
        private set
    var threads by mutableStateOf<Map<String, List<AssistantThread>>>(emptyMap())
        private set
    var currentThreadId by mutableStateOf<String?>(null)
        private set
    var threadMessagesLoading by mutableStateOf(false)
        private set
    var threadMessages by mutableStateOf<List<AssistantThreadMessage>>(emptyList())
    var currentThreadTitle by mutableStateOf<String?>(null)
        private set
    var editingMessageId by mutableStateOf<String?>(null)
        private set

    var messageCenterText by mutableStateOf<String>("")
        private set

    fun editMessage(messageId: String) {
        // find the message in the threadMessages and delete that message + every message after it, but keep all previous ones
        val index = threadMessages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            // if it's the first message in the convo, we'll just clear and set the text to the message
            val oldContent = threadMessages[index].content
            if (index == 0) {
                newChat()
                messageCenterText = oldContent
                return
            }

            editingMessageId = messageId
            threadMessages = threadMessages.subList(0, index)

            // set the text field to the old content now
            messageCenterText = oldContent
        }
    }

    fun _setEditingMessageId(id: String?) {
        editingMessageId = id
    }

    fun _setMessageCenterText(text: String) {
        messageCenterText = text
    }

    fun fetchThreads() {
        threadsLoading = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                threads = assistantClient.getThreads()
                threadsLoading = false
            }
        }
    }

    fun newChat() {
        editingMessageId = null
        currentThreadId = null
        currentThreadTitle = null
        threadMessages = emptyList()
        coroutineScope.launch {
            drawerState.close()
        }
    }

    fun _setCurrentThreadTitle(title: String) {
        currentThreadTitle = title
    }

    fun onThreadSelected(threadId: String) {
        currentThreadId = threadId
        currentThreadTitle = null
        coroutineScope.launch {
            try {
                threadMessagesLoading = true
                assistantClient.fetchStream(
                    streamId = "8ce77b1b-35c5-4262-8821-af3b33d1cf0f",
                    url = "https://kagi.com/assistant/thread_open",
                    method = "POST",
                    body = """{"focus":{"thread_id":"$threadId"}}""",
                    extraHeaders = mapOf("Content-Type" to "application/json"),
                    onChunk = { chunk ->
                        if (chunk.header == "thread.json") {
                            val thread = Json.parseToJsonElement(chunk.data)
                            currentThreadTitle = thread.jsonObject["title"]?.jsonPrimitive?.content
                        }

                        if (chunk.header == "messages.json") {
                            val dtoList =
                                Json.parseToJsonElement(chunk.data).toObject<List<MessageDto>>()

                            threadMessages = dtoList.flatMap { dto ->
                                val docs = dto.documents.map { d ->
                                    AssistantThreadMessageDocument(
                                        id = d.id,
                                        name = d.name,
                                        mime = d.mime,
                                        data = if (d.mime.startsWith("image")) d.data?.decodeDataUriToBitmap() else null
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

                            threadMessagesLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                println("Error fetching thread: ${e.message}")
                e.printStackTrace()
            }

            drawerState.close()
        }
    }

    fun _setCurrentThreadId(id: String?) {
        currentThreadId = id
    }

    fun openDrawer() {
        coroutineScope.launch {
            drawerState.open()
        }
    }
}

fun parseReferencesHtml(html: String): List<Citation> =
    Jsoup.parse(html)
        .select("ol[data-ref-list] > li > a[href]")
        .map { a -> Citation(url = a.attr("abs:href"), title = a.text()) }


@OptIn(ExperimentalEncodingApi::class)
fun String.decodeDataUriToBitmap(): Bitmap {
    val afterPrefix = substringAfter("base64,")
    require(afterPrefix != this) { "Malformed data-URI: missing 'base64,' segment" }

    val decodedBytes = Base64.decode(afterPrefix, 0)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        ?: error("Failed to decode bytes into Bitmap (invalid image data)")
}



