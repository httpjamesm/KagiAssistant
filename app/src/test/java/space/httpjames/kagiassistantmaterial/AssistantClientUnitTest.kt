package space.httpjames.kagiassistantmaterial

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
    fun assistantClient_authenticated() = runTest {
        val isAuthenticated = assistantClient.checkAuthentication()

        assertTrue(
            isAuthenticated
        )
        println("Authenticated")
    }

//    @Test
//    fun assistantClient_getThreads() = runTest {
//        println("Getting threads...")
//        val threads = assistantClient.getThreads()
//
//        println(threads)
//    }

    @Test
    fun assistantClient_fetchStream_thread() = runTest {
        val threadId = "2a8a6308-0ff4-4414-85f4-70d2667ece88"

        println("Fetching stream...")
        assistantClient.fetchStream(
            url = "https://kagi.com/assistant/thread_open",
            method = "POST",
            body = """{"focus":{"thread_id":"$threadId"}}""",
            extraHeaders = mapOf("Content-Type" to "application/json"),
        ).collect { chunk ->
            println("CHUNK RECEIVED: $chunk")
        }
    }

    @Test
    fun assistantClient_fetchStream_profileList() = runTest {
        assistantClient.fetchStream(
            url = "https://kagi.com/assistant/profile_list",
            method = "POST",
            body = """{}""",
            extraHeaders = mapOf("Content-Type" to "application/json"),
        ).collect { chunk ->
            println("CHUNK RECEIVED: $chunk")
        }
    }

    @Test
    fun assistantClient_fetchStream_prompt() = runTest {
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

        // Use Kotlinx Serialization to encode request
        val jsonString = Json.encodeToString(KagiPromptRequest.serializer(), requestBody)

        assistantClient.fetchStream(
            url = url,
            method = "POST",
            body = jsonString,
            extraHeaders = mapOf("Content-Type" to "application/json"),
        ).collect { chunk ->
            println("CHUNK RECEIVED: $chunk")
        }
    }

    @Test
    fun assistantClient_authorization() = runTest {
        val qrRemoteSession = assistantClient.getQrRemoteSession()
        val data = qrRemoteSession.getOrNull()
        assertNotNull(data)
        val token = data!!.token
        println("https://kagi.com/settings/qr_authorize?t=$token")

        var checkCount = 0
        while (checkCount < 60) { // 60 seconds max
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

            // wait 1s
            Thread.sleep(1000)
            checkCount++
        }
    }

    @Test
    fun assistantClient_getKagiCompanions() = runTest {
        val companions = assistantClient.getKagiCompanions()
        println(companions)
    }
}
