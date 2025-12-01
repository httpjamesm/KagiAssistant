package space.httpjames.kagiassistantmaterial.ui.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.toObject
import space.httpjames.kagiassistantmaterial.utils.TtsManager

class AssistantOverlayState(
    private val prefs: SharedPreferences,
    private val context: Context,
    private val assistantClient: AssistantClient,
    private val coroutineScope: CoroutineScope
) {
    /* exposed immutable snapshots */
    var text by mutableStateOf("")
        private set
    var userMessage by mutableStateOf("")
        private set
    var isWaitingForMessageFirstToken by mutableStateOf(false)
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

    var isTypingMode by mutableStateOf(false)
        private set

    var assistantDone by mutableStateOf(true)
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    var assistantMessageMd by mutableStateOf("")
        private set

    var profiles by mutableStateOf<List<AssistantProfile>>(emptyList())
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

            sendMessage()
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

    fun saveText() {
        prefs.edit().putString("savedText", text).apply()
    }

    fun saveThreadId() {
        println("saving thread id $currentThreadId")
        prefs.edit().putString("savedThreadId", currentThreadId).apply()
    }

    fun onTextChanged(newText: String) {
        text = newText
    }

    fun _setIsTypingMode(isTyping: Boolean) {
        isTypingMode = isTyping
    }

    fun sendMessage() {
        userMessage = text
        onTextChanged("")
        coroutineScope.launch {
            val focus = KagiPromptRequestFocus(
                currentThreadId,
                lastAssistantMessageId,
                userMessage.trim(),
                null,
            )

            val selectedAssistantModelKey =
                prefs.getString("assistant_model", "gemini-2-5-flash-lite")

            val profile = profiles.firstOrNull { it.key == selectedAssistantModelKey }

            if (profile == null) {
                assistantMessage = "Sorry, please try again later."
                return@launch
            }

            val requestBody = KagiPromptRequest(
                focus,
                KagiPromptRequestProfile(
                    profile.key,
                    false,
                    null,
                    profile.model,
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

            assistantMessageMd = ""

            fun onChunk(chunk: StreamChunk) {
                if (chunk.done) {
                    isWaitingForMessageFirstToken = false
                }
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
                        isWaitingForMessageFirstToken = false
                    }

                    "new_message.json" -> {
                        val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()
                        assistantMessage = dto.reply

                        if (dto.md != null) {
                            assistantMessageMd = dto.md
                            val autoSpeakReplies = prefs.getBoolean("auto_speak_replies", true)
                            if (autoSpeakReplies) {
                                ttsManager.speak(text = stripMarkdown(assistantMessageMd))
                                isSpeaking = true
                            }
                            isWaitingForMessageFirstToken = false
                            assistantDone = true
                        }

                        lastAssistantMessageId = dto.id
                    }
                }
            }

            try {
                isWaitingForMessageFirstToken = true
                assistantDone = false
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
                assistantDone = true
                isWaitingForMessageFirstToken = false
                assistantMessage = "Sorry, please try again later."
            }

        }
    }

    init {
        speechRecognizer.setRecognitionListener(listener)
        if (permissionOk) speechRecognizer.startListening(intent)
        coroutineScope.launch {
            try {
                profiles = assistantClient.getProfiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    fun restartFlow() {
        text = ""
        if (permissionOk) speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun restartSpeaking() {
        ttsManager.speak(stripMarkdown(assistantMessageMd))
        isSpeaking = true
    }

    fun stopSpeaking() {
        ttsManager.stop()
        isSpeaking = false
    }

    fun destroy() {
        isSpeaking = false
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
): AssistantOverlayState {
    val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

    return remember(assistantClient, context, prefs) {
        AssistantOverlayState(prefs, context, assistantClient, coroutineScope)
    }
}

fun stripMarkdown(input: String): String =
    input.replace(Regex("""[*_`~#\[\]()|>]+"""), "")
