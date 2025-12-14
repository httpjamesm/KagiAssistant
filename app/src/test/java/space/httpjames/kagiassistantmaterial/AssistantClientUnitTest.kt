package space.httpjames.kagiassistantmaterial

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

const val SESSION_TOKEN =
    ""

class AssistantClientUnitTest {
    val assistantClient = AssistantClient(SESSION_TOKEN)

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun assistantClient_authenticated() {

        val isAuthenticated = assistantClient.checkAuthentication()

        assertTrue(
            isAuthenticated
        )
        println("Authenticated")
    }

//    @Test
//    fun assistantClient_getThreads() {
//        println("Getting threads...")
//        val threads = assistantClient.getThreads()
//
//        println(threads)
//    }

    @Test
    fun assistantClient_fetchStream_thread() = runTest {
        val threadId = "2a8a6308-0ff4-4414-85f4-70d2667ece88"
        val streamId = UUID.randomUUID().toString()

        println("Fetching stream...")
        assistantClient.fetchStream(
            streamId = streamId,
            url = "https://kagi.com/assistant/thread_open",
            method = "POST",
            body = """{"focus":{"thread_id":"$threadId"}}""",
            extraHeaders = mapOf("Content-Type" to "application/json"),
            onChunk = { chunk ->
                println("CHUNK RECEIVED: $chunk")
            }
        )
    }

    @Test
    fun assistantClient_fetchStream_profileList() = runTest {
        val streamId = UUID.randomUUID().toString()
        assistantClient.fetchStream(
            streamId = streamId,
            url = "https://kagi.com/assistant/profile_list",
            method = "POST",
            body = """{}""",
            extraHeaders = mapOf("Content-Type" to "application/json"),
            onChunk = { chunk ->
                println("CHUNK RECEIVED: $chunk")
            }
        )
    }

    @Test
    fun assistantClient_fetchStream_prompt() = runTest {
        val streamId = UUID.randomUUID().toString()

        val url = "https://kagi.com/assistant/prompt"

        val focus = KagiPromptRequestFocus(
            null,
            null,
            "Describe your identity",
            "00000000-0000-4000-0000-000000000000",
        )

        val requestBody = KagiPromptRequest(
            focus,
            KagiPromptRequestProfile(
                null,
                false,
                null,
                "gemini-2-5-flash-lite",
                false,
            ),
            listOf(
                KagiPromptRequestThreads(
                    listOf(),
                    true,
                    false
                )
            )
        )

        val moshi = Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter = moshi.adapter(KagiPromptRequest::class.java)
        val jsonString = jsonAdapter.toJson(requestBody)

        assistantClient.fetchStream(
            streamId = streamId,
            url = url,
            method = "POST",
            body = jsonString,
            extraHeaders = mapOf("Content-Type" to "application/json"),
            onChunk = { chunk ->
                println("CHUNK RECEIVED: $chunk")
            }
        )
    }

    @Test
    fun assistantClient_authorization() {
        val qrRemoteSession = assistantClient.getQrRemoteSession()
        val data = qrRemoteSession.getOrNull()
        assertNotNull(data)
        val token = data!!.token
        println("https://kagi.com/settings/qr_authorize?t=$token")

        while (true) {
            val check = assistantClient.checkQrRemoteSession(data)
            if (check.isSuccess) {
                println("Authorized")
                val token = check.getOrNull()
                assertNotNull(token)
                println("Session token: $token")
                break
            } else {
                println("Not authorized yet")
            }

//            wait 1s
            Thread.sleep(1000)
        }
    }

    @Test
    fun assistantClient_getKagiCompanions() {
        val companions = assistantClient.getKagiCompanions()
        println(companions)
    }
}