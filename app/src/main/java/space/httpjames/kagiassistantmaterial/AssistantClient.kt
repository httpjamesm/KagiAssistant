package space.httpjames.kagiassistantmaterial

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.message.toObject
import space.httpjames.kagiassistantmaterial.utils.JsonLenient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    val metadata: String = "",
    val state: String = "waiting",
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

@Serializable
data class KagiPromptRequest(
    val focus: KagiPromptRequestFocus,
    val profile: KagiPromptRequestProfile,
    val threads: List<KagiPromptRequestThreads>? = null,
)

@Serializable
data class KagiPromptRequestFocus(
    val thread_id: String?,
    val message_id: String?,
    val prompt: String,
    val branch_id: String?,
)

@Serializable
data class KagiPromptRequestProfile(
    val id: String?,
    val internet_access: Boolean,
    val lens_id: String?,
    val model: String,
    val personalizations: Boolean,
)

@Serializable
data class KagiPromptRequestThreads(
    val tag_ids: List<String>,
    val saved: Boolean,
    val shared: Boolean,
)

data class ThreadListResponse(
    val threads: Map<String, MutableList<AssistantThread>>,
    val nextCursor: JsonElement?,
    val hasMore: Boolean,
    val count: Int,
    val totalCounts: JsonElement?,
)

@Serializable
data class ThreadSearchResult(
    val thread_id: String,
    val title: String = "",
    val snippet: String = "",
)

data class StreamChunk(
    val header: String,
    val data: String,
    val done: Boolean,
)

data class ThreadPageData(
    val title: String?,
    val messagesJson: String,
)

data class QrRemoteSessionDetails(
    val csrfToken: String,
    val token: String,
)

data class KagiCompanion(
    val id: String,
    val name: String,
    val data: String,
)

data class MultipartAssistantPromptFile(
    val file: File,
    val thumbnail: File?,
    val mime: String,
)

/**
 * Simple in-memory CookieJar for session management.
 */
private class SimpleCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies[url.host] ?: emptyList()
    }

    fun getCookieValue(url: HttpUrl, name: String): String? {
        return cookies[url.host]?.firstOrNull { it.name == name }?.value
    }

    fun setCookie(host: String, cookie: Cookie) {
        cookies[host] = (cookies[host] ?: emptyList()) + cookie
    }
}

class AssistantClient(
    private val sessionToken: String,
    private val json: Json = JsonLenient
) {
    private val cookieJar = SimpleCookieJar().apply {
        // Initialize with session token
        setCookie(
            "kagi.com", Cookie.Builder()
                .name("kagi_session")
                .value(extractToken(sessionToken))
                .domain("kagi.com")
                .build()
        )
    }

    private val okHttpClient: OkHttpClient = createDefaultClient(cookieJar)

    private val baseHeaders = Headers.Builder()
        .add("origin", "https://kagi.com")
        .add("referer", "https://kagi.com/assistant")
        .add(
            "user-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        )
        .add("accept", "application/vnd.kagi.stream")
        .add("cache-control", "no-cache")
        .build()

    fun getSessionToken(): String {
        return extractToken(sessionToken)
    }

    suspend fun getQrRemoteSession(): Result<QrRemoteSessionDetails> {
        val request = Request.Builder()
            .url("https://kagi.com/signin")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        return try {
            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to get QR remote session"))
            }

            val body = response.body?.string()
                ?: return Result.failure(Exception("Empty body"))

            val doc = Jsoup.parse(body)

            val token = doc.selectFirst("#qr-code-auth")?.attr("data-token")
            val csrfToken = doc.selectFirst("#qr-code-auth")?.attr("data-csrf")

            if (token == null || csrfToken == null) {
                return Result.failure(Exception("Failed to get QR remote session"))
            }

            Result.success(QrRemoteSessionDetails(csrfToken, token))
        } finally {
            response.close()
        }
    }

    suspend fun getAccountEmailAddress(): String {
        val request = Request.Builder()
            .headers(baseHeaders)
            .url("https://kagi.com/settings/change_email")
            .get()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""

            val html = response.body?.string()
            val doc = Jsoup.parse(html ?: return "")
            val element = doc.selectFirst("._0_pass_field")
            element?.attr("value") ?: ""
        }
    }

    suspend fun deleteSession(): Boolean {
        val request = Request.Builder()
            .headers(baseHeaders)
            .url("https://kagi.com/logout")
            .get()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    suspend fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String> {
        val token = details.token
        val jsonBody = "{\"n\":\"$token\"}"
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://kagi.com/login/qr_remote")
            .post(body)
            .addHeader("x-csrf-token", details.csrfToken)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("Failed to check QR remote session"))
            val responseBody = response.body?.string()
                ?: return Result.failure(Exception("Empty body"))
            if (responseBody != "OK") return Result.failure(Exception("Not authorized yet"))

            // Fallback to manual parsing for Set-Cookie headers
            val cookies: List<String> = response.headers.values("Set-Cookie")
            for (cookie in cookies) {
                if (cookie.startsWith("kagi_session=")) {
                    val cookieValue = cookie.substringBefore(';').trim()
                    return Result.success(cookieValue.replace("kagi_session=", ""))
                }
            }
            return Result.failure(Exception("Failed to extract session cookie"))

        }
    }

    suspend fun deleteChat(threadId: String): Result<Unit> {
        return try {
            fetchStream(
                url = "https://kagi.com/assistant/thread_delete",
                body = """{"threads":[{"id":"$threadId","title":".", "saved": true, "shared": false, "tag_ids": []}]}""",
                extraHeaders = mapOf("Content-Type" to "application/json")
            ).collect { chunk ->
                // Just consume the stream
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getKagiCompanions(): List<KagiCompanion> {
        val request = Request.Builder()
            .url("https://kagi.com/settings/companions")
            .headers(baseHeaders)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()

            val doc = Jsoup.parse(body)
            val cards = doc.select(".friends-card")

            return cards.map { card ->
                val companionId =
                    card.selectFirst("input[name='companion_id']")?.attr("value") ?: ""
                val name = card.selectFirst("h3")?.text() ?: ""
                val data = card.selectFirst("svg")?.outerHtml() ?: "<svg></svg>"

                KagiCompanion(companionId, name, data)
            }
        }
    }

    suspend fun checkAuthentication(): Boolean {
        val request = Request.Builder()
            .url("https://kagi.com/settings/assistant")
            .headers(baseHeaders)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            return "custom_instructions_input" in body
        }
    }

    suspend fun fetchThreadPage(threadId: String): ThreadPageData = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://kagi.com/assistant/$threadId")
            .headers(baseHeaders)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body")
            val doc = Jsoup.parse(body)

            val suffix = " - Kagi Assistant"
            val rawTitle = doc.selectFirst("title")?.text()
            val title = if (rawTitle != null && rawTitle.contains(suffix)) {
                rawTitle.substringBeforeLast(suffix)
            } else {
                rawTitle
            }

            val messageListElement = doc.selectFirst("#json-message-list")
                ?: throw IOException("Could not find #json-message-list element")

            val rawHtml = messageListElement.html()
            // Decode HTML entities; handle " &quot;" (space+entity) that shouldn't be in JSON
            val decoded = rawHtml
                .replace(" &quot;", "\"")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")

            ThreadPageData(title = title, messagesJson = decoded)
        }
    }

    suspend fun getProfiles(): List<AssistantProfile> {
        val profiles = mutableListOf<AssistantProfile>()

        fetchStream(
            url = "https://kagi.com/assistant/profile_list",
            method = "POST",
            body = "{}",
            extraHeaders = mapOf("Content-Type" to "application/json")
        ).collect { chunk ->
            if (chunk.header == "profiles.json") {
                val parsed = Json.parseToJsonElement(chunk.data)
                    .jsonObject["profiles"]?.jsonArray
                    ?.map { it.toObject<AssistantProfile>() }
                    .orEmpty()

                val (kagi, other) = parsed.partition {
                    it.family.equals("kagi", ignoreCase = true)
                }
                profiles.addAll(kagi + other)
            }
        }

        return profiles
    }

    suspend fun getThreads(cursor: JsonElement? = null): ThreadListResponse? {
        val body = if (cursor != null) {
            """{"cursor":$cursor}"""
        } else {
            "{}"
        }

        return fetchStream(
            url = "https://kagi.com/assistant/thread_list",
            method = "POST",
            body = body,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
            .firstOrNull { it.header == "thread_list.html" }
            ?.let { parseThreadListJsonWrapper(it.data) }
    }

    suspend fun searchThreads(query: String): List<ThreadSearchResult> {
        val body = """{"q":"${
            query.uppercase().replace("\"", "\\\"")
        }","saved":null,"shared":null,"tag_id":null}"""
        val request = buildRequest(
            url = "https://kagi.com/assistant/search",
            method = "POST",
            body = body,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                try {
                    val jsonElement = Json.parseToJsonElement(responseBody)
                    val results = jsonElement?.jsonArray
                        ?: return@withContext emptyList()
                    results.map { element ->
                        val obj = element.jsonObject
                        ThreadSearchResult(
                            thread_id = obj["thread_id"]?.jsonPrimitive?.contentOrNull ?: "",
                            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                            snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: "",
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }
    }

    /**
     * Performs a streaming HTTP request and returns a Flow of StreamChunk.
     * This helper handles the common streaming lifecycle: request execution,
     * response validation, streaming chunks, and completion signaling.
     */
    private fun performStreamingRequest(
        request: Request,
    ): Flow<StreamChunk> = flow {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error ${response.code}: ${response.message}")
            }

            val source = response.body?.source()
                ?: throw IOException("Empty response body from ${request.url}")

            try {
                streamLoop(source).collect { chunk ->
                    emit(chunk)
                }
                emit(StreamChunk("", "", true))
            } finally {
                response.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches a streaming response, returning a Flow of StreamChunk.
     * The flow is collected on the IO dispatcher and can be consumed on any dispatcher.
     */
    fun fetchStream(
        url: String,
        body: String? = null,
        method: String = "POST",
        extraHeaders: Map<String, String> = emptyMap(),
    ): Flow<StreamChunk> {
        val request = buildRequest(url, method, body, extraHeaders)
        return performStreamingRequest(request)
    }

    /**
     * Builds a multipart request for file uploads with the given KagiPromptRequest.
     */
    private fun buildMultipartRequest(
        url: String,
        requestBody: KagiPromptRequest,
        files: List<MultipartAssistantPromptFile>,
    ): Request {
        val stateJson = json.encodeToString(KagiPromptRequest.serializer(), requestBody)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("state", stateJson)

        files.forEach { file ->
            val mediaType =
                file.mime?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

            builder.addFormDataPart(
                name = "file",
                filename = file.file.name,
                body = file.file.asRequestBody(mediaType)
            )

            file.thumbnail?.let {
                builder.addFormDataPart(
                    name = "__kagithumbnail",
                    filename = "blob",
                    body = it.asRequestBody("image/webp".toMediaTypeOrNull())
                )
            }
        }

        return Request.Builder()
            .url(url)
            .headers(baseHeaders)
            .post(builder.build())
            .build()
    }

    fun sendMultipartRequest(
        url: String,
        requestBody: KagiPromptRequest,
        files: List<MultipartAssistantPromptFile>,
    ): Flow<StreamChunk> {
        val request = buildMultipartRequest(url, requestBody, files)
        return performStreamingRequest(request)
    }

    private suspend fun streamLoop(
        source: BufferedSource,
    ): Flow<StreamChunk> = flow {
        val nullByte = '\u0000'.code.toByte()
        val colonByte = ':'.code.toByte()

        while (!source.exhausted()) {
            val terminatorIndex = source.indexOf(nullByte)
            if (terminatorIndex == -1L) break

            // Look for the colon within the current chunk only
            val colonIndex = source.indexOf(colonByte, 0, terminatorIndex)

            if (colonIndex != -1L) {
                // Read header and body separately without creating a giant intermediate string
                val header = source.readUtf8(colonIndex).trim()
                source.skip(1) // skip the colon
                val body = source.readUtf8(terminatorIndex - colonIndex - 1)

                emit(StreamChunk(header, body, false))
            } else {
                source.skip(terminatorIndex)
            }
            source.skip(1) // skip the null terminator
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
        private fun createDefaultClient(jar: CookieJar): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // allow indefinite streaming
                .cookieJar(jar)
                .build()
        }

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

fun String.parseThreadListHtml(): MutableMap<String, MutableList<AssistantThread>> {
    val html = this
    var threadMap = mutableMapOf<String, MutableList<AssistantThread>>()

    val doc = Jsoup.parse(html)

    var currentHeader = "Threads"
    for (element in doc.select(".hide-if-no-threads .thread-list-header, .hide-if-no-threads .thread")) {
        when {
            element.hasClass("thread-list-header") -> currentHeader =
                element.text().trim()

            element.hasClass("thread") -> {
                val title = element.selectFirst(".title")?.text()?.trim().orEmpty()
                val excerpt = element.selectFirst(".excerpt")?.text()?.trim().orEmpty()
                val id = element.attr("data-code")
                val list = threadMap.getOrPut(currentHeader) { mutableListOf() }
                list.add(AssistantThread(id, title, excerpt))
            }
        }
    }

    return threadMap
}

fun parseThreadListJsonWrapper(data: String): ThreadListResponse {
    return try {
        val jsonElement = Json.parseToJsonElement(data)
        val obj = jsonElement.jsonObject
        val html = obj["html"]?.jsonPrimitive?.contentOrNull ?: ""
        val nextCursor = obj["next_cursor"]
        val hasMore = obj["has_more"]?.jsonPrimitive?.booleanOrNull ?: false
        val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
        val totalCounts = obj["total_counts"]

        ThreadListResponse(
            threads = html.parseThreadListHtml(),
            nextCursor = nextCursor,
            hasMore = hasMore,
            count = count,
            totalCounts = totalCounts,
        )
    } catch (e: Exception) {
        // Fallback: treat data as raw HTML (backward compatibility)
        ThreadListResponse(
            threads = data.parseThreadListHtml(),
            nextCursor = null,
            hasMore = false,
            count = 0,
            totalCounts = null,
        )
    }
}