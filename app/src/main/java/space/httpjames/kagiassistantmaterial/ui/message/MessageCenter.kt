package space.httpjames.kagiassistantmaterial.ui.message

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.chat.cleanup.ChatCleanupManager
import space.httpjames.kagiassistantmaterial.ui.main.ModelBottomSheet
import space.httpjames.kagiassistantmaterial.ui.viewmodel.MainViewModel
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey

@Composable
fun MessageCenter(
    threadId: String?,
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    prefs: android.content.SharedPreferences,
    cacheDir: String,
) {
    val threadsState by viewModel.threadsState.collectAsState()
    val messagesState by viewModel.messagesState.collectAsState()
    val messageCenterState by viewModel.messageCenterState.collectAsState()
    val haptics = LocalHapticFeedback.current

    val textFieldShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    val attachmentPreviewsScrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val context = LocalContext.current

    LaunchedEffect(threadId, messagesState.messages.size, messagesState.isTemporaryChat) {
        if (messagesState.isTemporaryChat && messagesState.messages.isNotEmpty() && threadId != null) {
            ChatCleanupManager.schedule(context, threadId, assistantClient.getSessionToken())
        }
    }

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (viewModel.showKeyboardAutomatically) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }

                viewModel.restoreText()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // set the web search default based on the profile's returned default
    LaunchedEffect(viewModel.getProfile()) {
        val autoToggleInternet = prefs.getBoolean(
            PreferenceKey.AUTO_TOGGLE_INTERNET.key,
            PreferenceKey.DEFAULT_AUTO_TOGGLE_INTERNET
        )
        if (!autoToggleInternet) {
            return@LaunchedEffect
        }

        val internetAccess = viewModel.getProfile()?.internetAccess ?: return@LaunchedEffect

        if (internetAccess != messageCenterState.isSearchEnabled) {
            viewModel.toggleSearch()
        }
    }

    if (messageCenterState.showAttachmentSizeLimitWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAttachmentSizeLimitWarning() },
            title = { Text("Attachment Limit Exceeded") },
            text = { Text("The total size of attachments cannot exceed 16 MB.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAttachmentSizeLimitWarning() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = textFieldShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = textFieldShape
            )
            .padding(bottom = 16.dp)
            .fillMaxWidth()
    ) {
        if (messageCenterState.attachmentUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .horizontalScroll(attachmentPreviewsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messageCenterState.attachmentUris.forEach { uri ->
                    key(uri) {
                        AttachmentPreview(
                            uri = uri,
                            onRemove = { uri -> viewModel.removeAttachmentUri(uri) })
                    }
                }
            }
        }
        TextField(
            value = messageCenterState.text,
            onValueChange = {
                if (it.length <= (viewModel.getProfile()?.maxInputChars ?: 40000)) {
                    viewModel.onMessageCenterTextChanged(it)
                }
            },
            placeholder = { Text(if (messagesState.isTemporaryChat) "Temporary chat" else "Ask Assistant") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .animateContentSize()
                .focusRequester(focusRequester),
            maxLines = 16,
            minLines = 1,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.showAttachmentBottomSheet()
                }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add attachment")
                }

                val backgroundColor by animateColorAsState(
                    if (messageCenterState.isSearchEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "Internet button background"
                )
                val contentColor by animateColorAsState(
                    if (messageCenterState.isSearchEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    label = "Internet button content"
                )

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .animateContentSize()
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .clickable {
                            viewModel.toggleSearch()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = "Toggle internet access",
                        tint = contentColor
                    )
                    if (messageCenterState.isSearchEnabled) {
                        Text("Internet", color = contentColor)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.openModelBottomSheet()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = viewModel.getProfile()?.name?.replace("(preview)", "")?.trim()
                            ?: "Select a model",
                    )
                }
                val isGenerating = messagesState.inProgressAssistantMessageId != null
                FilledIconButton(
                    onClick = {
                        if (isGenerating) {
                            viewModel.stopGeneration()
                        } else {
                            viewModel.sendMessage(context)
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = isGenerating || messageCenterState.text.isNotBlank() || messageCenterState.attachmentUris.isNotEmpty(),
                    modifier = Modifier.size(56.dp),
                ) {
                    if (isGenerating) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generation")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }

    if (messageCenterState.showModelBottomSheet) {
        ModelBottomSheet(
            assistantClient = assistantClient,
            prefs = prefs,
            cacheDir = cacheDir,
            onDismissRequest = { viewModel.dismissModelBottomSheet() }
        )
    }

    if (messageCenterState.showAttachmentBottomSheet) {
        AttachmentBottomSheet(
            onDismissRequest = { viewModel.onDismissAttachmentBottomSheet() },
            onAttachment = { viewModel.addAttachmentUri(context, it) },
            isTemporaryChat = messagesState.isTemporaryChat,
        )
    }
}
