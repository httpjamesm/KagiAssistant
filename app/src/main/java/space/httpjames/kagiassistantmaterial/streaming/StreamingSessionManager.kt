package space.httpjames.kagiassistantmaterial.streaming

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.KagiPromptRequest
import space.httpjames.kagiassistantmaterial.MultipartAssistantPromptFile
import space.httpjames.kagiassistantmaterial.StreamChunk
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository

data class StreamRequest(
    val streamId: String,
    val url: String,
    val jsonBody: String? = null,
    val requestBody: KagiPromptRequest? = null,
    val files: List<MultipartAssistantPromptFile>? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
)

object StreamingSessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val streams = mutableMapOf<String, MutableSharedFlow<StreamChunk>>()
    private val jobs = mutableMapOf<String, Job>()
    val pendingRequests = mutableMapOf<String, StreamRequest>()

    private var repository: AssistantRepository? = null

    fun init(repository: AssistantRepository) {
        this.repository = repository
    }

    fun getStream(streamId: String): SharedFlow<StreamChunk> {
        return streams.getOrPut(streamId) {
            MutableSharedFlow(replay = 64, extraBufferCapacity = 256)
        }
    }

    fun requestStream(context: Context, request: StreamRequest) {
        pendingRequests[request.streamId] = request
        try {
            val intent = Intent(context, PromptStreamingService::class.java).apply {
                action = PromptStreamingService.ACTION_START_STREAM
                putExtra(PromptStreamingService.EXTRA_STREAM_ID, request.streamId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Fallback: run the stream directly without the foreground service
            e.printStackTrace()
            startStream(request.streamId, request)
        }
    }

    fun startStream(streamId: String, request: StreamRequest) {
        val repo = repository ?: return
        val flow = streams.getOrPut(streamId) {
            MutableSharedFlow(replay = 64, extraBufferCapacity = 256)
        }

        val job = scope.launch {
            try {
                val sourceFlow = if (request.files != null && request.requestBody != null) {
                    repo.sendMultipartRequest(
                        url = request.url,
                        requestBody = request.requestBody,
                        files = request.files
                    )
                } else {
                    repo.fetchStream(
                        url = request.url,
                        body = request.jsonBody,
                        method = "POST",
                        extraHeaders = request.extraHeaders.ifEmpty {
                            mapOf("Content-Type" to "application/json")
                        }
                    )
                }

                sourceFlow.collect { chunk ->
                    flow.emit(chunk)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Emit a done chunk so collectors know the stream ended
                flow.emit(StreamChunk(header = "error", data = e.message ?: "Unknown error", done = true))
            }
        }

        jobs[streamId] = job
    }

    fun cancelStream(streamId: String?) {
        if (streamId == null) return
        jobs[streamId]?.cancel()
        jobs.remove(streamId)
        streams.remove(streamId)
        pendingRequests.remove(streamId)
    }

    fun hasActiveStreams(): Boolean = jobs.any { it.value.isActive }

    fun activeStreamCount(): Int = jobs.count { it.value.isActive }

    fun onStreamFinished(streamId: String) {
        jobs.remove(streamId)
        streams.remove(streamId)
        pendingRequests.remove(streamId)
    }
}
