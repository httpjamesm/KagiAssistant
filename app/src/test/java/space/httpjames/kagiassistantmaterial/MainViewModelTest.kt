package space.httpjames.kagiassistantmaterial

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import space.httpjames.kagiassistantmaterial.data.repository.AssistantRepository
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile
import space.httpjames.kagiassistantmaterial.ui.viewmodel.MainViewModel
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fetchThreads_populatesThreadsState() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            rootThreadListResponse = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(
                        AssistantThread(id = "thread-1", title = "Alpha", excerpt = "First")
                    )
                ),
                nextCursor = null,
                hasMore = false,
                count = 1,
                totalCounts = null
            )
        )
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()

        assertEquals(DataFetchingState.OK, viewModel.threadsState.value.callState)
        assertEquals(listOf("Today"), viewModel.threadsState.value.threads.keys.toList())
        assertEquals("thread-1", viewModel.threadsState.value.threads.getValue("Today").single().id)
    }

    @Test
    fun fetchThreads_whenRepositoryFails_setsErroredState() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository().apply {
            getThreadsException = IllegalStateException("boom")
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()

        assertEquals(DataFetchingState.ERRORED, viewModel.threadsState.value.callState)
    }

    @Test
    fun loadMoreThreads_withoutCursorOrMore_doesNothing() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()
        repo.getThreadsCalls.clear()

        viewModel.loadMoreThreads()
        advanceUntilIdle()

        assertTrue(repo.getThreadsCalls.isEmpty())
    }

    @Test
    fun loadMoreThreads_mergesExistingAndNewCategories() = runTest(testDispatcher) {
        val nextCursor = JsonPrimitive("cursor-1")
        val repo = FakeAssistantRepository(
            rootThreadListResponse = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(AssistantThread("thread-1", "Alpha", "First"))
                ),
                nextCursor = nextCursor,
                hasMore = true,
                count = 1,
                totalCounts = null
            )
        ).apply {
            threadResponsesByCursor[cursorKey(nextCursor)] = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(AssistantThread("thread-2", "Beta", "Second")),
                    "Older" to mutableListOf(AssistantThread("thread-3", "Gamma", "Third"))
                ),
                nextCursor = null,
                hasMore = false,
                count = 2,
                totalCounts = null
            )
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()
        viewModel.loadMoreThreads()
        advanceUntilIdle()

        assertEquals(listOf("thread-1", "thread-2"), viewModel.threadsState.value.threads.getValue("Today").map { it.id })
        assertEquals(listOf("thread-3"), viewModel.threadsState.value.threads.getValue("Older").map { it.id })
        assertFalse(viewModel.threadsState.value.hasMore)
        assertFalse(viewModel.threadsState.value.isLoadingMore)
    }

    @Test
    fun loadMoreThreads_whenRepositoryFails_resetsLoadingFlag() = runTest(testDispatcher) {
        val nextCursor = JsonPrimitive("cursor-1")
        val repo = FakeAssistantRepository(
            rootThreadListResponse = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(AssistantThread("thread-1", "Alpha", "First"))
                ),
                nextCursor = nextCursor,
                hasMore = true,
                count = 1,
                totalCounts = null
            )
        ).apply {
            getThreadsExceptionsByCursor[cursorKey(nextCursor)] = IllegalStateException("load more failed")
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()
        viewModel.loadMoreThreads()
        advanceUntilIdle()

        assertFalse(viewModel.threadsState.value.isLoadingMore)
        assertEquals(listOf("thread-1"), viewModel.threadsState.value.threads.getValue("Today").map { it.id })
    }

    @Test
    fun onThreadSelected_fetchesThreadAndRestoresProfileDerivedState() = runTest(testDispatcher) {
        val baseProfile = profile(id = "base-model", name = "Base Model", internetAccess = true)
        val reasoningProfile = profile(
            id = "reasoning-model",
            name = "Base Model (Reasoning)",
            internetAccess = true
        )
        val repo = FakeAssistantRepository(
            profiles = listOf(baseProfile, reasoningProfile),
            threadPages = mapOf(
                "thread-1" to threadPageData(
                    title = "Reasoning thread",
                    prompt = "Question",
                    reply = "Answer",
                    profile = reasoningProfile
                )
            )
        )
        val prefs = InMemorySharedPreferences()
        val viewModel = MainViewModel(repo, prefs, ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()

        assertEquals("thread-1", viewModel.threadsState.value.currentThreadId)
        assertEquals("Reasoning thread", viewModel.messagesState.value.currentThreadTitle)
        assertEquals(2, viewModel.messagesState.value.messages.size)
        assertEquals(DataFetchingState.OK, viewModel.messagesState.value.callState)
        assertEquals(baseProfile.key, prefs.getString(PreferenceKey.PROFILE.key, null))
        assertTrue(viewModel.messageCenterState.value.isSearchEnabled)
        assertTrue(viewModel.messageCenterState.value.thinkEnabled)
    }

    @Test
    fun onThreadSelected_existingSessionRestoresCachedSessionWithoutRefetching() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            profiles = listOf(profile(id = "model-a", name = "Model A")),
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "P1", reply = "R1"),
                "thread-2" to threadPageData(title = "Thread Two", prompt = "P2", reply = "R2")
            )
        )
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.onThreadSelected("thread-2")
        advanceUntilIdle()

        assertEquals(1, repo.fetchThreadPageCalls.getValue("thread-1"))
        assertEquals(1, repo.fetchThreadPageCalls.getValue("thread-2"))
        assertEquals("Thread Two", viewModel.messagesState.value.currentThreadTitle)

        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()

        assertEquals(1, repo.fetchThreadPageCalls.getValue("thread-1"))
        assertEquals("Thread One", viewModel.messagesState.value.currentThreadTitle)
        assertEquals("P1", viewModel.messagesState.value.messages.first().content)
        assertEquals("R1", viewModel.messagesState.value.messages.last().content)
    }

    @Test
    fun onThreadSelected_whenFetchFails_marksMessagesErrored() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository().apply {
            fetchThreadPageExceptions["thread-1"] = IllegalStateException("no thread")
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()

        assertEquals("thread-1", viewModel.threadsState.value.currentThreadId)
        assertEquals(DataFetchingState.ERRORED, viewModel.messagesState.value.callState)
        assertTrue(viewModel.messagesState.value.messages.isEmpty())
    }

    @Test
    fun toggleIsTemporaryChat_withoutSessionCreatesTemporarySession() = runTest(testDispatcher) {
        val viewModel = MainViewModel(FakeAssistantRepository(), InMemorySharedPreferences())

        advanceUntilIdle()
        assertFalse(viewModel.messagesState.value.isTemporaryChat)
        assertNull(viewModel.threadsState.value.currentThreadId)

        viewModel.toggleIsTemporaryChat()

        assertTrue(viewModel.messagesState.value.isTemporaryChat)
        assertNull(viewModel.threadsState.value.currentThreadId)

        viewModel.toggleIsTemporaryChat()

        assertFalse(viewModel.messagesState.value.isTemporaryChat)
    }

    @Test
    fun restoreThread_reopensSavedThreadOnInit() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            profiles = listOf(profile(id = "model-a", name = "Model A")),
            threadPages = mapOf(
                "saved-thread" to threadPageData(title = "Saved thread", prompt = "Question", reply = "Answer")
            )
        )
        val prefs = InMemorySharedPreferences().apply {
            edit().putString(PreferenceKey.SAVED_THREAD_ID.key, "saved-thread").apply()
        }

        val viewModel = MainViewModel(repo, prefs, ioDispatcher = testDispatcher)
        advanceUntilIdle()

        assertEquals("saved-thread", viewModel.threadsState.value.currentThreadId)
        assertEquals("Saved thread", viewModel.messagesState.value.currentThreadTitle)
        assertEquals(1, repo.fetchThreadPageCalls.getValue("saved-thread"))
    }

    @Test
    fun searchThreads_blankQueryClearsResultsAndFlags() = runTest(testDispatcher) {
        val viewModel = MainViewModel(FakeAssistantRepository(), InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.searchThreads("")

        assertEquals("", viewModel.threadsState.value.searchQuery)
        assertNull(viewModel.threadsState.value.searchResults)
        assertFalse(viewModel.threadsState.value.isSearching)
        assertFalse(viewModel.threadsState.value.isLoadingSearchPages)
    }

    @Test
    fun searchThreads_shortQueryDoesNotHitRepository() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.searchThreads("ab")
        advanceUntilIdle()

        assertEquals("ab", viewModel.threadsState.value.searchQuery)
        assertNull(viewModel.threadsState.value.searchResults)
        assertTrue(repo.searchQueries.isEmpty())
    }

    @Test
    fun searchThreads_successLoadsResultsAndBackgroundPages() = runTest(testDispatcher) {
        val nextCursor = JsonPrimitive("cursor-1")
        val repo = FakeAssistantRepository(
            rootThreadListResponse = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(AssistantThread("thread-1", "Alpha", "First"))
                ),
                nextCursor = nextCursor,
                hasMore = true,
                count = 1,
                totalCounts = null
            )
        ).apply {
            searchResultsByQuery["alp"] = listOf(
                ThreadSearchResult(thread_id = "thread-2", title = "Beta", snippet = "<b>Beta</b>")
            )
            threadResponsesByCursor[cursorKey(nextCursor)] = ThreadListResponse(
                threads = mutableMapOf(
                    "Today" to mutableListOf(AssistantThread("thread-2", "Beta", "Second"))
                ),
                nextCursor = null,
                hasMore = false,
                count = 1,
                totalCounts = null
            )
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.fetchThreads()
        advanceUntilIdle()
        viewModel.searchThreads("alp")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(listOf("alp"), repo.searchQueries)
        assertEquals(listOf("thread-2"), viewModel.threadsState.value.searchResults?.map { it.thread_id })
        assertEquals(listOf("thread-1", "thread-2"), viewModel.threadsState.value.threads.getValue("Today").map { it.id })
        assertFalse(viewModel.threadsState.value.isSearching)
        assertFalse(viewModel.threadsState.value.isLoadingSearchPages)
    }

    @Test
    fun searchThreads_whenRepositoryFails_setsEmptyResults() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository().apply {
            searchException = IllegalStateException("search failed")
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.searchThreads("query")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(emptyList<ThreadSearchResult>(), viewModel.threadsState.value.searchResults)
        assertFalse(viewModel.threadsState.value.isSearching)
        assertFalse(viewModel.threadsState.value.isLoadingSearchPages)
    }

    @Test
    fun searchThreads_newQueryCancelsPreviousPendingSearch() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository().apply {
            searchResultsByQuery["second"] = listOf(ThreadSearchResult(thread_id = "thread-2"))
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.searchThreads("first")
        advanceTimeBy(100)
        viewModel.searchThreads("second")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(listOf("second"), repo.searchQueries)
        assertEquals("second", viewModel.threadsState.value.searchQuery)
    }

    @Test
    fun deleteChat_withoutCurrentThread_doesNothing() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.deleteChat()
        advanceUntilIdle()

        assertTrue(repo.deleteChatCalls.isEmpty())
    }

    @Test
    fun deleteChat_successDeletesCurrentThreadAndStartsFreshChat() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "P1", reply = "R1")
            )
        )
        val prefs = InMemorySharedPreferences()
        val viewModel = MainViewModel(repo, prefs, ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.deleteChat()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repo.deleteChatCalls)
        assertNull(viewModel.threadsState.value.currentThreadId)
        assertTrue(viewModel.messagesState.value.messages.isEmpty())
        assertNull(prefs.getString(PreferenceKey.SAVED_THREAD_ID.key, null))
    }

    @Test
    fun deleteChat_failureLeavesCurrentSessionUntouched() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "P1", reply = "R1")
            )
        ).apply {
            deleteChatResult = Result.failure(IllegalStateException("delete failed"))
        }
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.deleteChat()
        advanceUntilIdle()

        assertEquals("thread-1", viewModel.threadsState.value.currentThreadId)
        assertEquals("Thread One", viewModel.messagesState.value.currentThreadTitle)
        assertEquals(2, viewModel.messagesState.value.messages.size)
    }

    @Test
    fun newChat_onTemporaryPersistedThreadDeletesRemoteThread() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "P1", reply = "R1")
            )
        )
        val prefs = InMemorySharedPreferences()
        val viewModel = MainViewModel(repo, prefs, ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.toggleIsTemporaryChat()
        viewModel.newChat()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repo.deleteChatCalls)
        assertNull(viewModel.threadsState.value.currentThreadId)
        assertFalse(viewModel.messagesState.value.isTemporaryChat)
        assertNull(prefs.getString(PreferenceKey.SAVED_THREAD_ID.key, null))
    }

    @Test
    fun newChat_onTemporaryUnsavedSessionWithPendingTraceStopsGeneration() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.toggleIsTemporaryChat()
        setCurrentSessionState(viewModel, traceId = "trace-1")

        viewModel.newChat()
        advanceUntilIdle()

        assertEquals(listOf("trace-1"), repo.stopGenerationCalls)
        assertNull(viewModel.threadsState.value.currentThreadId)
        assertFalse(viewModel.messagesState.value.isTemporaryChat)
    }

    @Test
    fun editMessage_firstMessageStartsNewChatAndCopiesPromptIntoDraft() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "Question", reply = "Answer")
            )
        )
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.editMessage("message-1")
        advanceUntilIdle()

        assertNull(viewModel.threadsState.value.currentThreadId)
        assertEquals("Question", viewModel.messageCenterState.value.text)
        assertTrue(viewModel.messagesState.value.messages.isEmpty())
    }

    @Test
    fun editMessage_laterMessageTrimsConversationAndSetsEditingText() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(
                    title = "Thread One",
                    dtos = listOf(
                        messageDto(id = "message-1", prompt = "Q1", reply = "R1"),
                        messageDto(id = "message-2", prompt = "Q2", reply = "R2")
                    )
                )
            )
        )
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()
        viewModel.editMessage("message-2")

        assertEquals(listOf("Q1", "R1"), viewModel.messagesState.value.messages.map { it.content })
        assertEquals("Q2", viewModel.messageCenterState.value.text)
    }

    @Test
    fun editMessage_unknownIdLeavesStateUntouched() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository(
            threadPages = mapOf(
                "thread-1" to threadPageData(title = "Thread One", prompt = "Question", reply = "Answer")
            )
        )
        val viewModel = MainViewModel(repo, InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.onThreadSelected("thread-1")
        advanceUntilIdle()

        val initialMessages = viewModel.messagesState.value.messages
        viewModel.editMessage("missing")

        assertEquals(initialMessages, viewModel.messagesState.value.messages)
        assertEquals("", viewModel.messageCenterState.value.text)
    }

    @Test
    fun restoreText_loadsSavedDraftFromPreferences() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString(PreferenceKey.SAVED_TEXT.key, "draft text").apply()
        }
        val viewModel = MainViewModel(FakeAssistantRepository(), prefs)

        advanceUntilIdle()
        viewModel.restoreText()

        assertEquals("draft text", viewModel.messageCenterState.value.text)
    }

    @Test
    fun onMessageCenterTextChanged_updatesStateAndPersistsDraft() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val viewModel = MainViewModel(FakeAssistantRepository(), prefs)

        advanceUntilIdle()
        viewModel.onMessageCenterTextChanged("hello")

        assertEquals("hello", viewModel.messageCenterState.value.text)
        assertEquals("hello", prefs.getString(PreferenceKey.SAVED_TEXT.key, null))
    }

    @Test
    fun toggleSearchAndSetSearchEnabled_updateSearchState() = runTest(testDispatcher) {
        val viewModel = MainViewModel(FakeAssistantRepository(), InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.toggleSearch()
        assertTrue(viewModel.messageCenterState.value.isSearchEnabled)

        viewModel.setSearchEnabled(false)
        assertFalse(viewModel.messageCenterState.value.isSearchEnabled)

        viewModel.setSearchEnabled(false)
        assertFalse(viewModel.messageCenterState.value.isSearchEnabled)
    }

    @Test
    fun modelBottomSheetVisibilityToggles() = runTest(testDispatcher) {
        val viewModel = MainViewModel(FakeAssistantRepository(), InMemorySharedPreferences())

        advanceUntilIdle()
        viewModel.openModelBottomSheet()
        assertTrue(viewModel.messageCenterState.value.showModelBottomSheet)

        viewModel.dismissModelBottomSheet()
        assertFalse(viewModel.messageCenterState.value.showModelBottomSheet)
    }

    @Test
    fun getProfile_returnsNullWhenSelectionDoesNotExist() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences().apply {
            edit().putString(PreferenceKey.PROFILE.key, "missing").apply()
        }
        val viewModel = MainViewModel(FakeAssistantRepository(), prefs)

        advanceUntilIdle()

        assertNull(viewModel.getProfile())
    }

    @Test
    fun getEffectiveProfile_usesReasoningCounterpartOnlyWhenThinkEnabled() = runTest(testDispatcher) {
        val baseProfile = profile(id = "base-model", name = "Base Model")
        val reasoningProfile = profile(id = "reasoning-model", name = "Base Model (Reasoning)")
        val prefs = InMemorySharedPreferences().apply {
            edit().putString(PreferenceKey.PROFILE.key, baseProfile.key).apply()
        }
        val viewModel = MainViewModel(
            FakeAssistantRepository(profiles = listOf(baseProfile, reasoningProfile)),
            prefs
        )

        advanceUntilIdle()

        assertEquals(baseProfile.key, viewModel.getEffectiveProfile()?.key)
        assertTrue(viewModel.hasReasoningCounterpart(viewModel.getProfile()))

        viewModel.toggleThink()

        assertTrue(viewModel.messageCenterState.value.thinkEnabled)
        assertEquals(reasoningProfile.key, viewModel.getEffectiveProfile()?.key)
    }

    @Test
    fun reasoningHelpers_treatReasoningOnlyModelAsForcedThink() = runTest(testDispatcher) {
        val reasoningOnlyProfile = profile(id = "reasoning-model", name = "Solo Model (Reasoning)")
        val prefs = InMemorySharedPreferences().apply {
            edit().putString(PreferenceKey.PROFILE.key, reasoningOnlyProfile.key).apply()
        }
        val viewModel = MainViewModel(FakeAssistantRepository(profiles = listOf(reasoningOnlyProfile)), prefs)

        advanceUntilIdle()

        assertTrue(viewModel.isReasoningOnlyModel(reasoningOnlyProfile))
        assertTrue(viewModel.hasReasoningCounterpart(reasoningOnlyProfile))

        viewModel.toggleThink()

        assertEquals(reasoningOnlyProfile.key, viewModel.getEffectiveProfile()?.key)
    }

    @Test
    fun attachmentState_addRemoveWarningAndBottomSheetBehaveCorrectly() = runTest(testDispatcher) {
        val sizes = mapOf(
            "content://small" to 1024L,
            "content://large" to 17L * 1024 * 1024
        )
        val context = mockk<Context>(relaxed = true)
        val viewModel = MainViewModel(
            FakeAssistantRepository(),
            InMemorySharedPreferences(),
            attachmentSizeProvider = { _, uri -> sizes.getValue(uri) }
        )

        advanceUntilIdle()
        viewModel.addAttachmentUri(context, "content://small")
        assertEquals(listOf("content://small"), viewModel.messageCenterState.value.attachmentUris)

        viewModel.removeAttachmentUri("content://small")
        assertTrue(viewModel.messageCenterState.value.attachmentUris.isEmpty())

        viewModel.addAttachmentUri(context, "content://large")
        assertTrue(viewModel.messageCenterState.value.showAttachmentSizeLimitWarning)
        assertTrue(viewModel.messageCenterState.value.attachmentUris.isEmpty())

        viewModel.dismissAttachmentSizeLimitWarning()
        assertFalse(viewModel.messageCenterState.value.showAttachmentSizeLimitWarning)

        viewModel.showAttachmentBottomSheet()
        assertTrue(viewModel.messageCenterState.value.showAttachmentBottomSheet)

        viewModel.onDismissAttachmentBottomSheet()
        assertFalse(viewModel.messageCenterState.value.showAttachmentBottomSheet)
    }

    @Test
    fun addSharedAttachmentUris_takesPermissionsAndAddsUrisEvenWhenPermissionAlreadyHeld() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        every { uri1.toString() } returns "content://one"
        every { uri2.toString() } returns "content://two"
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.takePersistableUriPermission(uri1, any()) } throws SecurityException("already held")
        every { resolver.takePersistableUriPermission(uri2, any()) } just Runs
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns resolver

        val viewModel = MainViewModel(
            FakeAssistantRepository(),
            InMemorySharedPreferences(),
            attachmentSizeProvider = { _, uri -> if (uri == "content://one") 512L else 1024L }
        )
        advanceUntilIdle()

        viewModel.addSharedAttachmentUris(context, listOf(uri1, uri2))

        assertEquals(listOf(uri1.toString(), uri2.toString()), viewModel.messageCenterState.value.attachmentUris)
        verify(exactly = 1) { resolver.takePersistableUriPermission(uri1, any()) }
        verify(exactly = 1) { resolver.takePersistableUriPermission(uri2, any()) }
    }

    @Test
    fun stopGeneration_withoutActiveGenerationDoesNothing() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.stopGeneration()
        advanceUntilIdle()

        assertTrue(repo.stopGenerationCalls.isEmpty())
    }

    @Test
    fun stopGeneration_clearsSessionStateAndStopsRemoteGenerationWhenTracePresent() = runTest(testDispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = MainViewModel(repo, InMemorySharedPreferences(), ioDispatcher = testDispatcher)

        advanceUntilIdle()
        viewModel.toggleIsTemporaryChat()
        setCurrentSessionState(
            viewModel,
            inProgressAssistantMessageId = "message.reply",
            traceId = "trace-1"
        )

        viewModel.stopGeneration()
        advanceUntilIdle()

        assertNull(viewModel.messagesState.value.inProgressAssistantMessageId)
        assertEquals(listOf("trace-1"), repo.stopGenerationCalls)
    }

    private fun profile(
        id: String,
        name: String,
        internetAccess: Boolean = false,
        model: String = id,
        family: String = "Kagi"
    ) = AssistantProfile(
        id = id,
        model = model,
        family = family,
        name = name,
        modelName = name,
        internetAccess = internetAccess
    )

    private fun messageDto(
        id: String,
        prompt: String,
        reply: String,
        profile: AssistantProfile = profile(id = "default-model", name = "Default Model")
    ) = MessageDto(
        id = id,
        prompt = prompt,
        reply = reply,
        profile = profile,
        state = "done"
    )

    private fun threadPageData(
        title: String,
        prompt: String,
        reply: String,
        profile: AssistantProfile = profile(id = "default-model", name = "Default Model")
    ): ThreadPageData = threadPageData(title, listOf(messageDto("message-1", prompt, reply, profile)))

    private fun threadPageData(
        title: String,
        dtos: List<MessageDto>
    ): ThreadPageData = ThreadPageData(
        title = title,
        messagesJson = Json.encodeToString(dtos)
    )

    private fun cursorKey(cursor: JsonElement?): String? = cursor?.toString()

    private fun setCurrentSessionState(
        viewModel: MainViewModel,
        threadId: String? = currentSessionValue(viewModel, "threadId") as String?,
        messages: MutableList<AssistantThreadMessage> = currentSessionValue(viewModel, "messages") as MutableList<AssistantThreadMessage>,
        currentThreadTitle: String? = currentSessionValue(viewModel, "currentThreadTitle") as String?,
        callState: DataFetchingState = currentSessionValue(viewModel, "callState") as DataFetchingState,
        isTemporaryChat: Boolean = currentSessionValue(viewModel, "isTemporaryChat") as Boolean,
        inProgressAssistantMessageId: String? = currentSessionValue(viewModel, "inProgressAssistantMessageId") as String?,
        editingMessageId: String? = currentSessionValue(viewModel, "editingMessageId") as String?,
        streamingJob: Job? = currentSessionValue(viewModel, "streamingJob") as Job?,
        traceId: String? = currentSessionValue(viewModel, "traceId") as String?,
        activeStreamId: String? = currentSessionValue(viewModel, "activeStreamId") as String?,
    ) {
        val sessionKey = activeSessionKey(viewModel)
        val sessionMap = threadSessions(viewModel)
        val currentSession = sessionMap.getValue(sessionKey)
        val copyMethod = currentSession.javaClass.getDeclaredMethod(
            "copy",
            String::class.java,
            java.util.List::class.java,
            String::class.java,
            DataFetchingState::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Job::class.java,
            String::class.java,
            String::class.java
        )
        copyMethod.isAccessible = true
        val updatedSession = requireNotNull(copyMethod.invoke(
            currentSession,
            threadId,
            messages,
            currentThreadTitle,
            callState,
            isTemporaryChat,
            inProgressAssistantMessageId,
            editingMessageId,
            streamingJob,
            traceId,
            activeStreamId
        ))
        sessionMap[sessionKey] = updatedSession
        val updateMethod = MainViewModel::class.java.getDeclaredMethod("updateMessagesStateFromSession", String::class.java)
        updateMethod.isAccessible = true
        updateMethod.invoke(viewModel, sessionKey)
    }

    private fun currentSessionValue(viewModel: MainViewModel, fieldName: String): Any? {
        val session = threadSessions(viewModel).getValue(activeSessionKey(viewModel))
        val field = session.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(session)
    }

    @Suppress("UNCHECKED_CAST")
    private fun threadSessions(viewModel: MainViewModel): MutableMap<String, Any> {
        val field = MainViewModel::class.java.getDeclaredField("threadSessions")
        field.isAccessible = true
        return field.get(viewModel) as MutableMap<String, Any>
    }

    private fun activeSessionKey(viewModel: MainViewModel): String {
        val field = MainViewModel::class.java.getDeclaredField("activeSessionKey")
        field.isAccessible = true
        return field.get(viewModel) as String
    }

    private class FakeAssistantRepository(
        private val rootThreadListResponse: ThreadListResponse = ThreadListResponse(
            threads = mutableMapOf(),
            nextCursor = null,
            hasMore = false,
            count = 0,
            totalCounts = null
        ),
        val threadPages: Map<String, ThreadPageData> = emptyMap(),
        val profiles: List<AssistantProfile> = emptyList(),
    ) : AssistantRepository {
        val fetchThreadPageCalls = mutableMapOf<String, Int>()
        val fetchThreadPageExceptions = mutableMapOf<String, Exception>()
        val threadResponsesByCursor = mutableMapOf<String?, ThreadListResponse>()
        val getThreadsExceptionsByCursor = mutableMapOf<String?, Exception>()
        val getThreadsCalls = mutableListOf<String?>()
        val searchQueries = mutableListOf<String>()
        val searchResultsByQuery = mutableMapOf<String, List<ThreadSearchResult>>()
        val deleteChatCalls = mutableListOf<String>()
        val stopGenerationCalls = mutableListOf<String>()
        var getThreadsException: Exception? = null
        var searchException: Exception? = null
        var deleteChatResult: Result<Unit> = Result.success(Unit)

        override suspend fun checkAuthentication(): Boolean = true

        override suspend fun getQrRemoteSession(): Result<QrRemoteSessionDetails> =
            Result.failure(UnsupportedOperationException())

        override suspend fun checkQrRemoteSession(details: QrRemoteSessionDetails): Result<String> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteSession(): Boolean = true

        override suspend fun getAccountEmailAddress(): String = "test@example.com"

        override suspend fun getThreads(cursor: JsonElement?): ThreadListResponse {
            val key = cursor?.toString()
            getThreadsCalls += key
            getThreadsException?.let { throw it }
            getThreadsExceptionsByCursor[key]?.let { throw it }
            return threadResponsesByCursor[key] ?: rootThreadListResponse
        }

        override suspend fun searchThreads(query: String): List<ThreadSearchResult> {
            searchQueries += query
            searchException?.let { throw it }
            return searchResultsByQuery[query] ?: emptyList()
        }

        override suspend fun stopGeneration(traceId: String): Result<Unit> {
            stopGenerationCalls += traceId
            return Result.success(Unit)
        }

        override suspend fun deleteChat(threadId: String): Result<Unit> {
            deleteChatCalls += threadId
            return deleteChatResult
        }

        override suspend fun fetchThreadPage(threadId: String): ThreadPageData {
            fetchThreadPageCalls[threadId] = (fetchThreadPageCalls[threadId] ?: 0) + 1
            fetchThreadPageExceptions[threadId]?.let { throw it }
            return threadPages.getValue(threadId)
        }

        override fun fetchStream(
            url: String,
            body: String?,
            method: String,
            extraHeaders: Map<String, String>
        ): Flow<StreamChunk> = emptyFlow()

        override suspend fun getProfiles(): List<AssistantProfile> = profiles

        override suspend fun getKagiCompanions(): List<KagiCompanion> = emptyList()

        override fun sendMultipartRequest(
            url: String,
            requestBody: KagiPromptRequest,
            files: List<MultipartAssistantPromptFile>
        ): Flow<StreamChunk> = emptyFlow()

        override fun getSessionToken(): String = "token"

        override suspend fun getAutoSave(): Boolean = true
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? {
            return values[key] as? String ?: defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>
        ) : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    values.clear()
                }
                pending.forEach { (key, value) ->
                    if (value == null) {
                        values.remove(key)
                    } else {
                        values[key] = value
                    }
                }
            }
        }
    }
}
