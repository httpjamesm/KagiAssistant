package space.httpjames.kagiassistantmaterial.ui.main

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.Screens
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey
import space.httpjames.kagiassistantmaterial.ui.chat.ChatArea
import space.httpjames.kagiassistantmaterial.ui.message.MessageCenter
import space.httpjames.kagiassistantmaterial.ui.shared.Header
import space.httpjames.kagiassistantmaterial.ui.viewmodel.AssistantViewModelFactory
import space.httpjames.kagiassistantmaterial.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    navController: NavController,
    sharedUris: List<Uri>? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs =
        context.getSharedPreferences("assistant_prefs", android.content.Context.MODE_PRIVATE)
    val cacheDir = context.cacheDir.absolutePath

    val viewModel: MainViewModel = viewModel(
        factory = AssistantViewModelFactory(
            assistantClient,
            prefs,
            cacheDir,
            onTokenReceived = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            }
        )
    )
    val threadsState by viewModel.threadsState.collectAsState()
    val messagesState by viewModel.messagesState.collectAsState()
    val generatingThreads by viewModel.generatingThreadsState.collectAsState()

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

    // Process shared URIs from intent
    LaunchedEffect(sharedUris) {
        sharedUris?.let { uris ->
            viewModel.addSharedAttachmentUris(context, uris)
        }
    }

    // Track keyboard visibility
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val clipboard = LocalClipboard.current

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = drawerState.isOpen) { backEvents ->
        backEvents.collect { event ->
            predictiveBackProgress = event.progress
        }
        scope.launch {
            drawerState.close()
            predictiveBackProgress = 0f
        }
    }

    BackHandler(
        enabled = !drawerState.isOpen
                && messagesState.isTemporaryChat
                && threadsState.currentThreadId == null
                && !imeVisible
    ) {
        viewModel.toggleIsTemporaryChat()
    }


    // Only handle back for "clear chat" when keyboard is NOT visible
    BackHandler(
        enabled = !drawerState.isOpen
                && threadsState.currentThreadId != null
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
                    threads = threadsState.threads,
                    onThreadSelected = {
                        scope.launch {
                            viewModel.onThreadSelected(it)
                            drawerState.close()
                        }
                    },
                    callState = threadsState.callState,
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
                    },
                    predictiveBackProgress = predictiveBackProgress,
                    currentThreadId = threadsState.currentThreadId,
                    generatingThreadIds = generatingThreads,
                )

        }) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Header(
                    threadTitle = messagesState.currentThreadTitle,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChatClick = { viewModel.newChat() },
                    onCopyClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "Thread title",
                                        messagesState.currentThreadTitle
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
                    isTemporaryChat = messagesState.isTemporaryChat
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ChatArea(
                    threadMessages = messagesState.messages,
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
                    threadMessagesCallState = messagesState.callState,
                    currentThreadId = threadsState.currentThreadId,
                    onEdit = {
                        viewModel.editMessage(it)
                    },
                    onRetryClick = {
                        scope.launch {
                            viewModel.onThreadSelected(threadsState.currentThreadId!!)
                        }
                    },
                    isTemporaryChat = messagesState.isTemporaryChat,
                    isGenerating = messagesState.inProgressAssistantMessageId != null,
                    stickyScrollEnabled = prefs.getBoolean(
                        PreferenceKey.STICKY_SCROLL.key,
                        PreferenceKey.DEFAULT_STICKY_SCROLL
                    )
                )
                MessageCenter(
                    threadId = threadsState.currentThreadId,
                    assistantClient = assistantClient,
                    viewModel = viewModel,
                    coroutineScope = scope,
                    prefs = prefs,
                    cacheDir = cacheDir
                )
            }
        }
    }
}
