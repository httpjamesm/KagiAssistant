package space.httpjames.kagiassistantmaterial

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.viewmodel.parseMessageProfileKey
import space.httpjames.kagiassistantmaterial.ui.viewmodel.resolveInternetAccessForSelection
import space.httpjames.kagiassistantmaterial.ui.viewmodel.resolveThreadProfileSelection
import space.httpjames.kagiassistantmaterial.ui.viewmodel.ThreadProfileSelectionSource
import space.httpjames.kagiassistantmaterial.utils.JsonLenient

class ThreadProfileSyncUnitTest {

    @Test
    fun assistantProfile_deserializesWithoutShortcut() {
        val profile = JsonLenient.decodeFromString<AssistantProfile>(
            """
            {
              "id": null,
              "model": "sonar",
              "model_provider": "kagi",
              "name": "Kagi Sonar",
              "model_name": "Kagi Sonar"
            }
            """.trimIndent()
        )

        assertEquals("sonar", profile.key)
        assertNull(profile.shortcut)
    }

    @Test
    fun parseAssistantBootstrapData_extractsAutoSaveAndInitialProfile() {
        val html = """
            <html>
              <body>
                <div id="initial-profile">assistant-default</div>
                <script>window.AUTO_SAVE = false;</script>
              </body>
            </html>
        """.trimIndent()

        val bootstrapData = parseAssistantBootstrapData(html)

        assertFalse(bootstrapData.autoSave)
        assertEquals("assistant-default", bootstrapData.initialProfileKey)
    }

    @Test
    fun parseMessageProfileKey_prefersIdOverModel() {
        val dto = MessageDto(
            id = "message-1",
            profile = KagiPromptRequestProfile(
                id = "custom-123",
                model = "assistant-default"
            )
        )

        assertEquals("custom-123", parseMessageProfileKey(dto))
    }

    @Test
    fun parseMessageProfileKey_fallsBackToModelWhenIdMissing() {
        val dto = MessageDto(
            id = "message-1",
            profile = KagiPromptRequestProfile(model = "sonar-pro")
        )

        assertEquals("sonar-pro", parseMessageProfileKey(dto))
    }

    @Test
    fun resolveThreadProfileSelection_usesThreadProfileKey() {
        val profiles = listOf(
            stockProfile(),
            customProfile()
        )
        val messages = listOf(
            AssistantThreadMessage(
                id = "message-1.reply",
                content = "Hello",
                role = AssistantThreadMessageRole.ASSISTANT,
                profileKey = "custom-123"
            )
        )

        val selection = resolveThreadProfileSelection(
            messages = messages,
            profiles = profiles,
            initialProfileKey = "assistant-default"
        )

        assertEquals("custom-123", selection?.profile?.key)
        assertFalse(selection?.thinkEnabled ?: true)
        assertNull(selection?.internetAccessOverride)
        assertEquals(ThreadProfileSelectionSource.THREAD_MESSAGE, selection?.source)
    }

    @Test
    fun resolveThreadProfileSelection_fallsBackToBootstrapShortcutWhenThreadProfileMissing() {
        val profiles = listOf(stockProfile(), customProfile())
        val messages = listOf(
            AssistantThreadMessage(
                id = "message-1",
                content = "Hello",
                role = AssistantThreadMessageRole.USER
            )
        )

        val selection = resolveThreadProfileSelection(
            messages = messages,
            profiles = profiles,
            initialProfileKey = "assistant-default"
        )

        assertEquals("sonar", selection?.profile?.key)
        assertFalse(selection?.thinkEnabled ?: true)
        assertEquals(ThreadProfileSelectionSource.BOOTSTRAP, selection?.source)
    }

    @Test
    fun resolveThreadProfileSelection_usesLastAssistantInternetAccessOverride() {
        val selection = resolveThreadProfileSelection(
            messages = listOf(
                AssistantThreadMessage(
                    id = "message-1.reply",
                    content = "Hello",
                    role = AssistantThreadMessageRole.ASSISTANT,
                    profileKey = "custom-123",
                    internetAccess = true
                )
            ),
            profiles = listOf(stockProfile(), customProfile()),
            initialProfileKey = null
        )

        assertTrue(selection?.internetAccessOverride == true)
        assertTrue(resolveInternetAccessForSelection(selection!!, currentValue = false) == true)
    }

    @Test
    fun resolveThreadProfileSelection_mapsReasoningVariantBackToBaseProfileAndEnablesThink() {
        val baseProfile = AssistantProfile(
            id = null,
            model = "claude-sonnet-4",
            family = "anthropic",
            name = "Claude Sonnet 4",
            modelName = "Claude Sonnet 4"
        )
        val reasoningProfile = AssistantProfile(
            id = null,
            model = "claude-sonnet-4-reasoning",
            family = "anthropic",
            name = "Claude Sonnet 4 (reasoning)",
            modelName = "Claude Sonnet 4 (reasoning)"
        )

        val selection = resolveThreadProfileSelection(
            messages = listOf(
                AssistantThreadMessage(
                    id = "message-1.reply",
                    content = "Hello",
                    role = AssistantThreadMessageRole.ASSISTANT,
                    profileKey = reasoningProfile.key
                )
            ),
            profiles = listOf(baseProfile, reasoningProfile),
            initialProfileKey = null
        )

        assertEquals(baseProfile.key, selection?.profile?.key)
        assertTrue(selection?.thinkEnabled == true)
    }

    @Test
    fun resolveInternetAccessForSelection_fallsBackToCurrentValueWhenAutoToggleDisabled() {
        val selection = resolveThreadProfileSelection(
            messages = emptyList(),
            profiles = listOf(stockProfile()),
            initialProfileKey = "assistant-default"
        )!!

        assertFalse(
            resolveInternetAccessForSelection(
                selection = selection,
                currentValue = false,
                autoToggleInternet = false
            )
        )
    }

    @Test
    fun resolveInternetAccessForSelection_bootstrapClearsThreadOverrideWhenAutoToggleDisabled() {
        val selection = resolveThreadProfileSelection(
            messages = emptyList(),
            profiles = listOf(stockProfile()),
            initialProfileKey = "assistant-default"
        )!!

        assertFalse(
            resolveInternetAccessForSelection(
                selection = selection,
                currentValue = true,
                autoToggleInternet = false,
                previousOverride = true
            )
        )
    }

    private fun stockProfile() = AssistantProfile(
        id = null,
        model = "sonar",
        family = "kagi",
        name = "Kagi Sonar",
        modelName = "Kagi Sonar",
        shortcut = "assistant-default"
    )

    private fun customProfile() = AssistantProfile(
        id = "custom-123",
        model = "assistant-default",
        family = "custom",
        name = "My Assistant",
        modelName = "My Assistant"
    )
}
