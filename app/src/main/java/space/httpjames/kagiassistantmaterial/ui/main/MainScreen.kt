package space.httpjames.kagiassistantmaterial.ui.main

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.chat.ChatArea
import space.httpjames.kagiassistantmaterial.ui.message.MessageCenter
import space.httpjames.kagiassistantmaterial.ui.shared.Header

@Composable
fun MainScreen(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier
) {
    val state = rememberMainState(assistantClient)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Track keyboard visibility
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

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
                isLoading = state.threadsLoading
            )
        }) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = { Header(threadTitle = state.currentThreadTitle, onMenuClick = { scope.launch { drawerState.open() } }, onNewChatClick = { state.newChat() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ChatArea(
                    assistantClient = assistantClient,
                    threadMessages = state.threadMessages,
                    modifier = Modifier
                        .padding(innerPadding)
                        .weight(1f),
                    isLoading = state.threadMessagesLoading,
                    currentThreadId = state.currentThreadId,
                    onEdit = { it ->
                        state.editMessage(it)
                    }
                )
                MessageCenter(
                    threadId = state.currentThreadId,
                    assistantClient = assistantClient,

                    threadMessages = state.threadMessages,
                    setThreadMessages = { state.threadMessages = it },
                    coroutineScope = state.coroutineScope,
                    setCurrentThreadId = { it -> state._setCurrentThreadId(it) },

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
