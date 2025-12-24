package space.httpjames.kagiassistantmaterial.ui.main

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.Screens
import space.httpjames.kagiassistantmaterial.ui.chat.ChatArea
import space.httpjames.kagiassistantmaterial.ui.message.MessageCenter
import space.httpjames.kagiassistantmaterial.ui.shared.Header
import space.httpjames.kagiassistantmaterial.ui.viewmodel.AssistantViewModelFactory
import space.httpjames.kagiassistantmaterial.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("assistant_prefs", android.content.Context.MODE_PRIVATE)
    val cacheDir = context.cacheDir.absolutePath

    val viewModel: MainViewModel = viewModel(
        factory = AssistantViewModelFactory(assistantClient, prefs, cacheDir)
    )
    val uiState by viewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.restoreThread()
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

    BackHandler(
        enabled = !drawerState.isOpen
                && uiState.isTemporaryChat
                && uiState.currentThreadId == null
                && !imeVisible
    ) {
        viewModel.toggleIsTemporaryChat()
    }


    // Only handle back for "clear chat" when keyboard is NOT visible
    BackHandler(
        enabled = !drawerState.isOpen
                && uiState.currentThreadId != null
                && !imeVisible
    ) {
        viewModel.newChat()
    }

    if (drawerState.isOpen) {
        LaunchedEffect(Unit) {
            viewModel.fetchThreads()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadsDrawerSheet(
                threads = uiState.threads,
                onThreadSelected = {
                    scope.launch {
                        viewModel.onThreadSelected(it)
                        drawerState.close()
                    }
                },
                callState = uiState.threadsCallState,
                onSettingsClick = {
                    scope.launch {
                        navController.navigate(Screens.SETTINGS.route)
                        drawerState.close()
                    }
                },
                onRetryClick = {
                    scope.launch {
                        viewModel.fetchThreads()
                    }
                }
            )
        }) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Header(
                    threadTitle = uiState.currentThreadTitle,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChatClick = { viewModel.newChat() },
                    onCopyClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "Thread title",
                                        uiState.currentThreadTitle
                                    )
                                )
                            )
                        }
                    },
                    onDeleteClick = {
                        scope.launch {
                            viewModel.deleteChat()
                        }
                    },
                    onTemporaryChatClick = {
                        viewModel.toggleIsTemporaryChat()
                    },
                    isTemporaryChat = uiState.isTemporaryChat
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ChatArea(
                    threadMessages = uiState.threadMessages,
                    modifier = Modifier
                        .padding(
                            PaddingValues(
                                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                                top = innerPadding.calculateTopPadding(),
                                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                                bottom = 0.dp
                            )
                        )
                        .weight(1f),
                    threadMessagesCallState = uiState.threadMessagesCallState,
                    currentThreadId = uiState.currentThreadId,
                    onEdit = {
                        viewModel.editMessage(it)
                    },
                    onRetryClick = {
                        scope.launch {
                            viewModel.onThreadSelected(uiState.currentThreadId!!)
                        }
                    },
                    isTemporaryChat = uiState.isTemporaryChat
                )
                MessageCenter(
                    threadId = uiState.currentThreadId,
                    assistantClient = assistantClient,
                    threadMessages = uiState.threadMessages,
                    setThreadMessages = { viewModel.setThreadMessages(it) },
                    coroutineScope = scope,
                    setCurrentThreadId = { viewModel.setCurrentThreadId(it) },
                    text = uiState.messageCenterText,
                    setText = { viewModel.setMessageCenterText(it) },
                    editingMessageId = uiState.editingMessageId,
                    setEditingMessageId = { viewModel.setEditingMessageId(it) },
                    setCurrentThreadTitle = { viewModel.setCurrentThreadTitle(it) },
                    isTemporaryChat = uiState.isTemporaryChat
                )
            }
        }
    }
}
