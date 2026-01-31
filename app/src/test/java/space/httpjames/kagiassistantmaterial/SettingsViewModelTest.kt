package space.httpjames.kagiassistantmaterial

import android.content.SharedPreferences
import android.speech.tts.Voice
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.ui.settings.VoiceOption
import space.httpjames.kagiassistantmaterial.ui.viewmodel.SettingsViewModel
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockRepository: AssistantRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockEditor = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        every { mockPrefs.getBoolean(PreferenceKey.AUTO_SPEAK_REPLIES.key, any()) } returns true
        every { mockPrefs.getBoolean(PreferenceKey.OPEN_KEYBOARD_AUTOMATICALLY.key, any()) } returns false
        every { mockPrefs.getBoolean(PreferenceKey.USE_MINI_OVERLAY.key, any()) } returns true
        every { mockPrefs.getBoolean(PreferenceKey.STICKY_SCROLL.key, any()) } returns true
        every { mockPrefs.getString(PreferenceKey.ASSISTANT_MODEL.key, any()) } returns null
        every { mockPrefs.getString(PreferenceKey.TTS_VOICE.key, any()) } returns null

        viewModel = SettingsViewModel(mockRepository, mockPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveTtsVoice_persistsToPreferences() {
        val voiceName = "en-au-x-aua-local"

        viewModel.saveTtsVoice(voiceName)

        verify { mockEditor.putString(PreferenceKey.TTS_VOICE.key, voiceName) }
        verify { mockEditor.apply() }
    }

    @Test
    fun saveTtsVoice_updatesUiState() {
        val voices = listOf(
            createVoiceOption("en-au-x-aua-local", "English (Australia) - High"),
            createVoiceOption("en-us-x-sfg-local", "English (US) - High")
        )
        viewModel.setAvailableVoices(voices)

        viewModel.saveTtsVoice("en-au-x-aua-local")

        assertEquals("en-au-x-aua-local", viewModel.uiState.value.selectedTtsVoice)
        assertEquals("English (Australia) - High", viewModel.uiState.value.selectedTtsVoiceDisplayName)
    }

    @Test
    fun saveTtsVoice_emptyName_setsDeviceDefault() {
        viewModel.saveTtsVoice("")

        assertEquals("", viewModel.uiState.value.selectedTtsVoice)
        assertNull(viewModel.uiState.value.selectedTtsVoiceDisplayName)
    }

    @Test
    fun setAvailableVoices_updatesUiState() {
        val voices = listOf(
            createVoiceOption("voice1", "Voice 1"),
            createVoiceOption("voice2", "Voice 2")
        )

        viewModel.setAvailableVoices(voices)

        assertEquals(2, viewModel.uiState.value.availableVoices.size)
        assertEquals("voice1", viewModel.uiState.value.availableVoices[0].name)
        assertEquals("voice2", viewModel.uiState.value.availableVoices[1].name)
    }

    @Test
    fun setAvailableVoices_withSelectedVoice_updatesDisplayName() {
        val voices = listOf(
            createVoiceOption("en-au-x-aua-local", "English (Australia) - High")
        )
        viewModel.saveTtsVoice("en-au-x-aua-local")

        viewModel.setAvailableVoices(voices)

        assertEquals("English (Australia) - High", viewModel.uiState.value.selectedTtsVoiceDisplayName)
    }

    @Test
    fun showVoiceChooser_setsModalVisible() {
        viewModel.showVoiceChooser()

        assertTrue(viewModel.uiState.value.showVoiceChooserModal)
    }

    @Test
    fun hideVoiceChooser_setsModalHidden() {
        viewModel.showVoiceChooser()
        viewModel.hideVoiceChooser()

        assertFalse(viewModel.uiState.value.showVoiceChooserModal)
    }

    @Test
    fun initialState_loadsFromPreferences() {
        every { mockPrefs.getString(PreferenceKey.TTS_VOICE.key, any()) } returns "saved-voice"

        val vm = SettingsViewModel(mockRepository, mockPrefs)

        assertEquals("saved-voice", vm.uiState.value.selectedTtsVoice)
    }

    @Test
    fun initialState_defaultVoice_whenNoSavedPreference() {
        every { mockPrefs.getString(PreferenceKey.TTS_VOICE.key, any()) } returns null

        val vm = SettingsViewModel(mockRepository, mockPrefs)

        assertEquals("", vm.uiState.value.selectedTtsVoice)
    }

    @Test
    fun selectedTtsVoice_isEmpty_whenDeviceDefaultSelected() {
        // When device default is selected, selectedTtsVoice should be empty.
        // This is important because the UI uses this to determine whether to call
        // setVoice(voiceName) or resetToDefaultVoice() when dismissing the modal
        // after previewing a different voice.
        val voices = listOf(
            createVoiceOption("en-au-x-aua-local", "English (Australia) - High"),
            createVoiceOption("en-us-x-sfg-local", "English (US) - High")
        )
        viewModel.setAvailableVoices(voices)

        // Select a specific voice first
        viewModel.saveTtsVoice("en-au-x-aua-local")
        assertEquals("en-au-x-aua-local", viewModel.uiState.value.selectedTtsVoice)

        // Then select device default (empty string)
        viewModel.saveTtsVoice("")

        // selectedTtsVoice must be empty so UI knows to call resetToDefaultVoice()
        assertTrue(viewModel.uiState.value.selectedTtsVoice.isEmpty())
        assertNull(viewModel.uiState.value.selectedTtsVoiceDisplayName)
        verify { mockEditor.putString(PreferenceKey.TTS_VOICE.key, "") }
    }

    private fun createVoiceOption(name: String, displayName: String): VoiceOption {
        return VoiceOption(
            name = name,
            displayName = displayName,
            locale = Locale.US,
            quality = Voice.QUALITY_HIGH,
            requiresNetwork = false
        )
    }
}
