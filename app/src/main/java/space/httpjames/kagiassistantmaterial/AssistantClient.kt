package space.httpjames.kagiassistantmaterial

import android.graphics.Bitmap
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import okio.IOException
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.utils.JsonLenient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AssistantThread(
    val id: String,
    val title: String,
    val excerpt: String,
)

enum class AssistantThreadMessageRole {
    ASSISTANT,
    USER,
}

data class Citation(
    val url: String,
    val title: String,
)

data class AssistantThreadMessage(
    val id: String,
    val content: String,
    val role: AssistantThreadMessageRole,
    val citations: List<Citation> = emptyList(),
    val documents: List<AssistantThreadMessageDocument> = emptyList(),
    val branchIds: List<String> = listOf("00000000-0000-4000-0000-000000000000"),
    val finishedGenerating: Boolean = false,
    val markdownContent: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class AssistantThreadMessageDocument(
    val id: String,
    val name: String,
    val mime: String,
    val data: Bitmap?,
)

@Serializable
data class MessageDto(
    val id: String,
    val prompt: String = "",
    val reply: String = "",
    val documents: List<DocumentDto> = emptyList(),
    val branch_list: List<String> = emptyList(),
    val references_html: String = "",
    val md: String? = null,
    val metadata: String = ""
)

@Serializable
data class DocumentDto(
    val id: String,
    val name: String,
    val mime: String,
    val data: String? = null
)

inline fun <reified T> JsonElement.toObject(): T =
    JsonLenient.decodeFromJsonElement<T>(this)

@JsonClass(generateAdapter = true)
data class KagiPromptRequest(
    val focus: KagiPromptRequestFocus,
    val profile: KagiPromptRequestProfile,
    val threads: List<KagiPromptRequestThreads>? = null,
)

@JsonClass(generateAdapter = true)
data class KagiPromptRequestFocus(
    val thread_id: String?,
    val message_id: String?,
    val prompt: String,
    val branch_id: String?,
)

@JsonClass(generateAdapter = true)
data class KagiPromptRequestProfile(
    val id: String?,
    val internet_access: Boolean,
    val lens_id: String?,
    val model: String,
    val personalizations: Boolean,
)

@JsonClass(generateAdapter = true)
data class KagiPromptRequestThreads(
    val tag_ids: List<String>,
    val saved: Boolean,
    val shared: Boolean,
)

data class StreamChunk(
    val streamId: String,
    val header: String,
    val data: String,
    val done: Boolean,
)

data class QrRemoteSessionDetails(
    val csrfToken: String,
    val token: String,
)

class AssistantClient(
    sessionToken: String,
) {
    private val baseHeaders = Headers.Builder()
        .add("origin", "https://kagi.com")
        .add("referer", "https://kagi.com/assistant")
        .add(
            "user-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        )
        .add("accept", "application/vnd.kagi.stream")
        .add("cache-control", "no-cache")
        .add("cookie", "kagi_session=${extractToken(sessionToken)}")
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // allow indefinite streaming
        .build()

    private val moshi = Moshi.Builder().build()
    private val promptAdapter = moshi.adapter(KagiPromptRequest::class.java)

    fun getQrRemoteSession(): Result<QrRemoteSessionDetails> {
        val request = Request.Builder().url("https://kagi.com/signin").get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("Failed to get QR remote session"))

            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))

            // html parse the #qr-code-auth and get attrs "data-token" and "data-csrf"
            val doc = Jsoup.parse(body)

            val token = doc.selectFirst("#qr-code-auth")?.attr("data-token")
            val csrfToken = doc.selectFirst("#qr-code-auth")?.attr("data-csrf")

            if (token == null || csrfToken == null) {
                return Result.failure(Exception("Failed to get QR remote session"))
            }

            return Result.success(QrRemoteSessionDetails(csrfToken, token))
        }
    }

    fun getAccountEmailAddress(): String {
        val request = Request.Builder()
            .headers(baseHeaders)
            .url("https://kagi.com/settings/change_email").get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""

            val html = response.body?.string()
            // html parse the first ._0_pass_field's value attr
            val doc = Jsoup.parse(html ?: return "")
            val element = doc.selectFirst("._0_pass_field")
            return element?.attr("value") ?: ""
        }
    }

    suspend fun deleteSession(): Boolean {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .headers(baseHeaders)
                .url("https://kagi.com/logout").get().build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }

    fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String> {
        val token = details.token
        val json = "{\"n\":\"$token\"}"
        val body = json.toRequestBody("application/json".toMediaType())


        val request = Request.Builder().url("https://kagi.com/login/qr_remote").post(
            body
        ).addHeader("x-csrf-token", details.csrfToken).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("Failed to check QR remote session"))
            val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            if (body != "OK") return Result.failure(Exception("Not authorized yet"))

            // if it's ok, we check the Set-Cookie headers (may be more than one) and parse the one with `kagi_session=...`
            val cookies: List<String> = response.headers.values("Set-Cookie")

            for (cookie in cookies) {
                if (cookie.startsWith("kagi_session=")) {
                    val cookieValue = cookie.substringBefore(';').trim()
                    return Result.success(cookieValue.replace("kagi_session=", ""))
                }
            }
        }

        return Result.failure(Exception("Failed to check QR remote session"))
    }

    fun checkAuthentication(): Boolean {
        val request = Request.Builder()
            .url("https://kagi.com/settings/assistant")
            .headers(baseHeaders)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            return "custom_instructions_input" in body
        }
    }

    suspend fun getThreads(): Map<String, List<AssistantThread>> {
        val streamId = UUID.randomUUID().toString()
        var threadMap = mutableMapOf<String, MutableList<AssistantThread>>()

        this.fetchStream(
            streamId = streamId,
            url = "https://kagi.com/assistant/thread_list",
            method = "POST",
            body = """{}""",
            extraHeaders = mapOf("Content-Type" to "application/json"),
            onChunk = { chunk ->
                if (chunk.header == "thread_list.html") {
                    val html = "<html><body>${chunk.data}</body></html>"

                    val doc = Jsoup.parse(html)

                    var currentHeader = "Threads"
                    for (element in doc.select(".hide-if-no-threads .thread-list-header, .hide-if-no-threads .thread")) {
                        when {
                            element.hasClass("thread-list-header") -> currentHeader =
                                element.text().trim()

                            element.hasClass("thread") -> {
                                val title = element.selectFirst(".title")?.text()?.trim().orEmpty()
                                val excerpt =
                                    element.selectFirst(".excerpt")?.text()?.trim().orEmpty()
                                val id = element.attr("data-code")
                                val list = threadMap.getOrPut(currentHeader) { mutableListOf() }
                                list.add(AssistantThread(id, title, excerpt))
                            }
                        }
                    }
                }
            }
        )

        return threadMap
    }

    suspend fun fetchStream(
        streamId: String,
        url: String,
        body: String? = null,
        method: String = "POST",
        extraHeaders: Map<String, String> = emptyMap(),
        onChunk: suspend (StreamChunk) -> Unit,
    ) {
        // Capture the caller's dispatcher BEFORE switching to IO
        val callerDispatcher = coroutineContext[CoroutineDispatcher] ?: Dispatchers.Main

        withContext(Dispatchers.IO) {
            doStreamRequest(
                streamId = streamId,
                request = buildRequest(url, method, body, extraHeaders),
                onChunk = { chunk ->
                    // Dispatch to caller's context for the callback
                    withContext(callerDispatcher) {
                        onChunk(chunk)
                    }
                },
            )
        }
    }


    suspend fun sendMultipartRequest(
        streamId: String,
        url: String,
        requestBody: KagiPromptRequest,
        files: List<File>,
        thumbnails: List<File?> = emptyList(),
        mimeTypes: List<String> = emptyList(),
        onChunk: suspend (StreamChunk) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val stateJson = promptAdapter.toJson(requestBody)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("state", stateJson)

        files.forEachIndexed { index, file ->
            val mime = mimeTypes.getOrNull(index) ?: "application/octet-stream"
            builder.addFormDataPart(
                name = "file",
                filename = file.name,
                body = file.asRequestBody(mime.toMediaTypeOrNull())
            )

            thumbnails.getOrNull(index)?.let { thumb ->
                builder.addFormDataPart(
                    name = "__kagithumbnail",
                    filename = "blob",
                    body = thumb.asRequestBody("image/webp".toMediaTypeOrNull())
                )
            }
        }

        val request = Request.Builder()
            .url(url)
            .headers(baseHeaders)
            .post(builder.build())
            .build()

        doStreamRequest(streamId, request, onChunk)
    }

    private suspend fun doStreamRequest(
        streamId: String,
        request: Request,
        onChunk: suspend (StreamChunk) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val call = client.newCall(request)

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val source = response.body?.source()
                        if (!response.isSuccessful || source == null) {
                            response.close()
                            cont.resumeWithException(IOException("HTTP error ${response.code} from ${request.url}"))
                            return
                        }

                        // Use the current coroutine context instead of creating a new scope
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                streamLoop(streamId, source, onChunk)
                                onChunk(StreamChunk(streamId, "", "", true))
                                cont.resume(Unit)
                            } catch (t: Throwable) {
                                if (cont.isActive) cont.resumeWithException(t)
                            } finally {
                                response.close()
                            }
                        }

                        cont.invokeOnCancellation {
                            println("Job $streamId was cancelled")
                            call.cancel()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            call.cancel()
            throw e
        }
    }

    private suspend fun streamLoop(
        streamId: String,
        source: BufferedSource,
        onChunk: suspend (StreamChunk) -> Unit,
    ) {
        val nullByte = '\u0000'.code.toByte()

        try {
            while (!source.exhausted()) {
                // Find the null terminator and read the entire chunk at once
                val terminatorIndex = source.indexOf(nullByte)
                if (terminatorIndex == -1L) break

                val chunkData = source.readUtf8(terminatorIndex)
                source.skip(1) // skip the null terminator

                // Parse header:body
                val colonIndex = chunkData.indexOf(':')
                if (colonIndex == -1) continue

                val header = chunkData.take(colonIndex).trimStart().trim()

                val body = chunkData.substring(colonIndex + 1)

                onChunk(StreamChunk(streamId, header, body, false))
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun buildRequest(
        url: String,
        method: String,
        body: String?,
        extraHeaders: Map<String, String>,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .headers(baseHeaders.newBuilder().apply {
                extraHeaders.forEach { (k, v) -> set(k, v) }
            }.build())

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        when (method.uppercase()) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(body?.toRequestBody(mediaType))
            "PUT" -> builder.put((body ?: "").toRequestBody(mediaType))
            "PATCH" -> builder.patch((body ?: "").toRequestBody(mediaType))
            else -> builder.post((body ?: "").toRequestBody(mediaType))
        }
        return builder.build()
    }

    companion object {
        private fun extractToken(raw: String): String =
            raw.substringAfter("token=", raw).substringBefore("&")
    }
}

fun parseMetadata(html: String): Map<String, String> {
    val doc = Jsoup.parse(html)
    return doc.select("li").associate { li ->
        val key = li.selectFirst("span.attribute")?.text() ?: ""
        val value = li.selectFirst("span.value")?.text() ?: ""
        key to value
    }
}