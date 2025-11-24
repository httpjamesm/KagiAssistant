package space.httpjames.kagiassistantmaterial.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    BackHandler(enabled = !drawerState.isOpen && state.currentThreadId != null) {
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
            topBar = { Header(onMenuClick = { scope.launch { drawerState.open() } }, onNewChatClick = { state.newChat() }) }
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
                    isLoading = state.threadMessagesLoading
                )
                MessageCenter(
                    threadId = state.currentThreadId,
                    assistantClient = assistantClient,

                    threadMessages = state.threadMessages,
                    setThreadMessages = { state.threadMessages = it },
                    coroutineScope = state.coroutineScope,
                    setCurrentThreadId = { it -> state._setCurrentThreadId(it) }
                )
            }
        }
    }
}
