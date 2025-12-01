package space.httpjames.kagiassistantmaterial.ui.main

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.Screens
import space.httpjames.kagiassistantmaterial.ui.chat.ChatArea
import space.httpjames.kagiassistantmaterial.ui.message.MessageCenter
import space.httpjames.kagiassistantmaterial.ui.shared.Header

@Composable
fun MainScreen(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current

    val state = rememberMainState(assistantClient = assistantClient, context = context)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                state.restoreThread()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }


    // Track keyboard visibility
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val clipboard = LocalClipboard.current

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Only handle back for "clear chat" when keyboard is NOT visible
    BackHandler(
        enabled = !drawerState.isOpen
                && state.currentThreadId != null
                && !imeVisible  // <-- Add this condition
    ) {
        state.newChat()
    }

    if (drawerState.isOpen) {
        LaunchedEffect(Unit) {
            state.fetchThreads()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadsDrawerSheet(
                threads = state.threads,
                onThreadSelected = {
                    scope.launch {
                        state.onThreadSelected(it)
                        drawerState.close()
                    }
                },
                callState = state.threadsCallState,
                onSettingsClick = {
                    scope.launch {
                        navController.navigate(Screens.SETTINGS.route)
                        drawerState.close()
                    }
                },
                onRetryClick = {
                    scope.launch {
                        state.fetchThreads()
                    }
                }
            )
        }) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Header(
                    threadTitle = state.currentThreadTitle,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChatClick = { state.newChat() },
                    onCopyClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "Thread title",
                                        state.currentThreadTitle
                                    )
                                )
                            )
                        }
                    },
                    onDeleteClick = {
                        scope.launch {
                            assistantClient.fetchStream(
                                streamId = "delete_thread",
                                url = "https://kagi.com/assistant/thread_delete",
                                body = """{"threads":[{"id":"${state.currentThreadId}","title":".", "saved": true, "shared": false, "tag_ids": []}]}""",
                                extraHeaders = mapOf("Content-Type" to "application/json"),
                                onChunk = { chunk ->
                                    if (chunk.done) {
                                        state.newChat()
                                    }
                                }
                            )
                        }
                    },
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ChatArea(
                    threadMessages = state.threadMessages,
                    modifier = Modifier
                        .padding(innerPadding)
                        .weight(1f),
                    threadMessagesCallState = state.threadMessagesCallState,
                    currentThreadId = state.currentThreadId,
                    onEdit = {
                        state.editMessage(it)
                    },
                    onRetryClick = {
                        scope.launch {
                            state.onThreadSelected(state.currentThreadId!!)
                        }
                    }
                )
                MessageCenter(
                    threadId = state.currentThreadId,
                    assistantClient = assistantClient,

                    threadMessages = state.threadMessages,
                    setThreadMessages = { state.threadMessages = it },
                    coroutineScope = state.coroutineScope,
                    setCurrentThreadId = { state._setCurrentThreadId(it) },

                    text = state.messageCenterText,
                    setText = { state._setMessageCenterText(it) },

                    editingMessageId = state.editingMessageId,
                    setEditingMessageId = { state._setEditingMessageId(it) },

                    setCurrentThreadTitle = { state._setCurrentThreadTitle(it) }
                )
            }
        }
    }
}
