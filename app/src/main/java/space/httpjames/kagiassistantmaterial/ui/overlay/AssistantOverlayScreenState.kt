package space.httpjames.kagiassistantmaterial.ui.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.KagiPromptRequestFocus
import space.httpjames.kagiassistantmaterial.KagiPromptRequestProfile
import space.httpjames.kagiassistantmaterial.KagiPromptRequestThreads
import space.httpjames.kagiassistantmaterial.MessageDto
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.ui.message.toObject
import space.httpjames.kagiassistantmaterial.utils.TtsManager

class AssistantOverlayState(
    private val context: Context,
    private val assistantClient: AssistantClient,
    private val coroutineScope: CoroutineScope
) {
    /* exposed immutable snapshots */
    var text by mutableStateOf("")
        private set
    var userMessage by mutableStateOf("")
        private set
    var assistantMessage by mutableStateOf("")
        private set
    var isListening by mutableStateOf(false)
        private set
    val permissionOk: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    var currentThreadId by mutableStateOf<String?>(null)
        private set
    var lastAssistantMessageId by mutableStateOf<String?>(null)
        private set

    private val ttsManager = TtsManager(context)

    /* internal helpers */
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    private val listener = object : RecognitionListener {
        override fun onResults(b: Bundle?) {
            text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""

            userMessage = text
            coroutineScope.launch {
                val focus = KagiPromptRequestFocus(
                    currentThreadId,
                    lastAssistantMessageId,
                    userMessage.trim(),
                    null,
                )

                val requestBody = KagiPromptRequest(
                    focus,
                    KagiPromptRequestProfile(
                        "b4afa927-045a-423a-bfea-c9beac186134",
                        false,
                        null,
                        "gemini-2-5-flash-lite",
                        false,
                    ),
                    listOf(
                        KagiPromptRequestThreads(listOf(), true, false)
                    )
                )

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
                            if (id != null) currentThreadId = id
                        }

                        "tokens.json" -> {
                            val json = Json.parseToJsonElement(chunk.data)
                            val obj = json.jsonObject
                            val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""

                            assistantMessage = newText
                        }

                        "new_message.json" -> {
                            val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()
                            assistantMessage = dto.reply

                            if (dto.md != null) {
                                ttsManager.speak(text = stripMarkdown(dto.md))
                            }

                            lastAssistantMessageId = dto.id
                        }
                    }
                }

                try {
                    assistantClient.fetchStream(
                        streamId = "overlay.id",
                        url = "https://kagi.com/assistant/prompt",
                        method = "POST",
                        body = jsonString,
                        extraHeaders = mapOf("Content-Type" to "application/json"),
                        onChunk = { chunk -> onChunk(chunk) }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

        }

        override fun onPartialResults(b: Bundle?) {
            text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: text
        }

        override fun onError(e: Int) {
            isListening = false
        }

        override fun onReadyForSpeech(b: Bundle?) {
            isListening = true
        }

        override fun onEndOfSpeech() {
            isListening = false

        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(db: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEvent(e: Int, b: Bundle?) {}
    }

    init {
        speechRecognizer.setRecognitionListener(listener)
        if (permissionOk) speechRecognizer.startListening(intent)
    }

    fun restartFlow() {
        text = ""
        if (permissionOk) speechRecognizer.startListening(intent)
    }

    fun destroy() {
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
        ttsManager.release()
    }
}

// 2) Composable factory
@Composable
fun rememberAssistantOverlayState(
    assistantClient: AssistantClient,
    context: Context,
    coroutineScope: CoroutineScope
): AssistantOverlayState = remember(assistantClient, context) {
    AssistantOverlayState(context, assistantClient, coroutineScope)
}

fun stripMarkdown(input: String): String =
    input.replace(Regex("""[*_`~#\[\]()|>]+"""), "")
