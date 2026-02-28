package space.httpjames.kagiassistantmaterial.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.to84x84ThumbFile
import space.httpjames.kagiassistantmaterial.ui.message.toObject
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import space.httpjames.kagiassistantmaterial.utils.TtsManager
import java.io.File

/**
 * UI state for overlay screen
 */
data class OverlayUiState(
    val text: String = "",
    val userMessage: String = "",
    val isWaitingForMessageFirstToken: Boolean = false,
    val assistantMessage: String = "",
    val isListening: Boolean = false,
    val currentThreadId: String? = null,
    val lastAssistantMessageId: String? = null,
    val isTypingMode: Boolean = false,
    val assistantDone: Boolean = true,
    val isSpeaking: Boolean = false,
    val assistantMessageMd: String = "",
    val profiles: List<AssistantProfile> = emptyList(),
    val screenshotAttached: Boolean = false,
    val screenshot: Bitmap? = null,
    val permissionOk: Boolean = false
)

/**
 * ViewModel for the overlay screen.
 * Manages overlay state using StateFlow.
 * Note: context is applicationContext, passed via OverlayViewModelFactory to avoid leaks.
 */
@SuppressLint("StaticFieldLeak")
class OverlayViewModel(
    private val assistantClient: AssistantClient,
    private val prefs: SharedPreferences,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OverlayUiState(
            permissionOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    )
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val ttsManager = TtsManager(
        context = context,
        prefs = prefs,
        onStart = { _uiState.update { it.copy(isSpeaking = true) } },
        onDone = { _uiState.update { it.copy(isSpeaking = false) } }
    )

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
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            onTextChanged(text)
            sendMessage()
        }

        override fun onPartialResults(b: Bundle?) {
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: _uiState.value.text
            onTextChanged(text)
        }

        override fun onError(e: Int) {
            _uiState.update { it.copy(isListening = false) }
        }

        override fun onReadyForSpeech(b: Bundle?) {
            _uiState.update { it.copy(isListening = true) }
        }

        override fun onEndOfSpeech() {
            _uiState.update { it.copy(isListening = false) }
        }

        override fun onBeginningOfSpeech() {
            // Unused
        }
        override fun onRmsChanged(db: Float) {
            // Unused
        }
        override fun onBufferReceived(b: ByteArray?) {
            // Unused
        }
        override fun onEvent(e: Int, b: Bundle?) {
            // Unused
        }
    }

    init {
        speechRecognizer.setRecognitionListener(listener)
        val useMiniOverlay = prefs.getBoolean(
            PreferenceKey.USE_MINI_OVERLAY.key,
            true
        )
        if (useMiniOverlay) {
            if (_uiState.value.permissionOk) {
                speechRecognizer.startListening(intent)
            }
            viewModelScope.launch {
                try {
                    _uiState.update { it.copy(profiles = assistantClient.getProfiles()) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ========== State getters/setters ==========

    fun setScreenshot(screenshot: Bitmap?) {
        _uiState.update { it.copy(screenshot = screenshot) }
    }

    fun toggleScreenshotAttached() {
        _uiState.update { it.copy(screenshotAttached = !it.screenshotAttached) }
    }

    fun onTextChanged(newText: String) {
        _uiState.update { it.copy(text = newText) }
    }

    fun setIsTypingMode(isTyping: Boolean) {
        _uiState.update { it.copy(isTypingMode = isTyping) }
    }

    // ========== Actions ==========

    fun saveText() {
        prefs.edit { putString(PreferenceKey.SAVED_TEXT.key, _uiState.value.text) }
    }

    fun saveThreadId() {
        println("saving thread id ${_uiState.value.currentThreadId}")
        prefs.edit {
            putString(
                PreferenceKey.SAVED_THREAD_ID.key,
                _uiState.value.currentThreadId
            )
        }
    }

    fun sendMessage() {
        val userMessage = _uiState.value.text
        val screenshot = if (_uiState.value.screenshotAttached) _uiState.value.screenshot else null

        _uiState.update { it.copy(userMessage = userMessage) }

        viewModelScope.launch {
            val focus = KagiPromptRequestFocus(
                _uiState.value.currentThreadId,
                _uiState.value.lastAssistantMessageId,
                userMessage.trim(),
                null,
            )

            val selectedAssistantModelKey = prefs.getString(
                PreferenceKey.ASSISTANT_MODEL.key,
                PreferenceKey.DEFAULT_ASSISTANT_MODEL
            )

            val profile =
                _uiState.value.profiles.firstOrNull { it.key == selectedAssistantModelKey }

            if (profile == null) {
                _uiState.update { it.copy(assistantMessage = "Sorry, please try again later.") }
                return@launch
            }

            val autoSave = try {
                assistantClient.getAutoSave()
            } catch (e: Exception) {
                true
            }

            val requestBody = KagiPromptRequest(
                focus,
                KagiPromptRequestProfile(
                    profile.id,
                    false,
                    null,
                    profile.model,
                    false,
                ),
                listOf(
                    KagiPromptRequestThreads(listOf(), autoSave, false)
                )
            )

            // Use Kotlinx Serialization to encode request
            val jsonString = Json.encodeToString(KagiPromptRequest.serializer(), requestBody)

            _uiState.update { it.copy(assistantMessageMd = "") }

            fun onChunk(chunk: StreamChunk) {
                if (chunk.done) {
                    _uiState.update { it.copy(isWaitingForMessageFirstToken = false) }
                }
                when (chunk.header) {
                    "thread.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val id = json.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                        if (id != null) {
                            _uiState.update { it.copy(currentThreadId = id) }
                        }
                    }

                    "tokens.json" -> {
                        val json = Json.parseToJsonElement(chunk.data)
                        val obj = json.jsonObject
                        val newText = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""

                        _uiState.update {
                            it.copy(
                                assistantMessage = newText,
                                isWaitingForMessageFirstToken = false
                            )
                        }
                    }

                    "new_message.json" -> {
                        val dto = Json.parseToJsonElement(chunk.data).toObject<MessageDto>()

                        val autoSpeakReplies = prefs.getBoolean(
                            PreferenceKey.AUTO_SPEAK_REPLIES.key,
                            PreferenceKey.DEFAULT_AUTO_SPEAK_REPLIES
                        )

                        if (dto.md != null) {
                            if (autoSpeakReplies) {
                                ttsManager.speak(text = stripMarkdown(dto.md))
                            }
                            _uiState.update {
                                it.copy(
                                    assistantMessage = dto.reply,
                                    assistantMessageMd = dto.md,
                                    isWaitingForMessageFirstToken = false,
                                    assistantDone = true,
                                    lastAssistantMessageId = dto.id
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    assistantMessage = dto.reply,
                                    lastAssistantMessageId = dto.id
                                )
                            }
                        }
                    }
                }
            }

            try {
                _uiState.update {
                    it.copy(
                        isWaitingForMessageFirstToken = true,
                        assistantDone = false
                    )
                }

                if (screenshot != null) {
                    val tempFile = File.createTempFile(
                        "temp",
                        ".webp",
                        context.cacheDir
                    )

                    tempFile.outputStream().use { out ->
                        screenshot.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                    }

                    val thumbnail = tempFile.to84x84ThumbFile()

                    val promptFile = MultipartAssistantPromptFile(tempFile, thumbnail, "image/webp")

                    assistantClient.sendMultipartRequest(
                        url = "https://kagi.com/assistant/prompt",
                        requestBody = requestBody,
                        files = listOf(promptFile)
                    ).collect { chunk -> onChunk(chunk) }
                } else {
                    assistantClient.fetchStream(
                        url = "https://kagi.com/assistant/prompt",
                        method = "POST",
                        body = jsonString,
                        extraHeaders = mapOf("Content-Type" to "application/json")
                    ).collect { chunk -> onChunk(chunk) }
                }

                _uiState.update {
                    it.copy(
                        screenshotAttached = false,
                        text = ""
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        assistantDone = true,
                        isWaitingForMessageFirstToken = false,
                        assistantMessage = "Sorry, please try again later."
                    )
                }
            }
        }
    }

    fun restartFlow() {
        onTextChanged("")
        if (_uiState.value.permissionOk) {
            speechRecognizer.startListening(intent)
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                text = "",
                userMessage = "",
                assistantMessage = "",
                currentThreadId = null
            )
        }
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun restartSpeaking() {
        ttsManager.speak(stripMarkdown(_uiState.value.assistantMessageMd))
        _uiState.update { it.copy(isSpeaking = true) }
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.update { it.copy(isSpeaking = false) }
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
        ttsManager.release()
    }
}

fun stripMarkdown(input: String): String =
    input.replace(Regex("""[*_`~#\[\]()|>]+"""), "")

/**
 * Factory for creating OverlayViewModel with dependencies.
 * Uses applicationContext to avoid context leaks in ViewModel.
 */
class OverlayViewModelFactory(
    private val assistantClient: AssistantClient,
    private val prefs: SharedPreferences,
    context: Context
) : androidx.lifecycle.ViewModelProvider.Factory {
    private val applicationContext: Context = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OverlayViewModel::class.java)) {
            return OverlayViewModel(assistantClient, prefs, applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
