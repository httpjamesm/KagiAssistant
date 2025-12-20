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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.ui.chat.cleanup.ChatCleanupManager
import space.httpjames.kagiassistantmaterial.ui.main.ModelBottomSheet

@Composable
fun MessageCenter(
    threadId: String?,
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    threadMessages: List<AssistantThreadMessage>,
    setThreadMessages: (List<AssistantThreadMessage>) -> Unit,
    coroutineScope: CoroutineScope,
    setCurrentThreadId: (String?) -> Unit,
    editingMessageId: String? = null,
    text: String,
    setText: (String) -> Unit,
    setEditingMessageId: (String?) -> Unit,
    setCurrentThreadTitle: (String) -> Unit,
    isTemporaryChat: Boolean,
) {
    val state = rememberMessageCenterState(
        setCurrentThreadTitle = setCurrentThreadTitle,
        editingMessageId = editingMessageId,
        setEditingMessageId = setEditingMessageId,
        text = text,
        setText = setText,
        coroutineScope = coroutineScope,
        assistantClient = assistantClient,
        threadMessages = threadMessages,
        setThreadMessages = setThreadMessages,
        setCurrentThreadId = setCurrentThreadId,
        isTemporaryChat = isTemporaryChat
    )

    val textFieldShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val haptics = LocalHapticFeedback.current

    val attachmentPreviewsScrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val context = LocalContext.current

    LaunchedEffect(threadId, threadMessages.size, isTemporaryChat) {
        if (isTemporaryChat && threadMessages.isNotEmpty() && threadId != null) {
            ChatCleanupManager.schedule(context, threadId, assistantClient.getSessionToken())
        }
    }

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (state.showKeyboardAutomatically) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }

                state.restoreText()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }



    if (state.showAttachmentSizeLimitWarning) {
        AlertDialog(
            onDismissRequest = { state.dismissAttachmentSizeLimitWarning() },
            title = { Text("Attachment Limit Exceeded") },
            text = { Text("The total size of attachments cannot exceed 16 MB.") },
            confirmButton = {
                TextButton(onClick = { state.dismissAttachmentSizeLimitWarning() }) {
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
        if (state.attachmentUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .horizontalScroll(attachmentPreviewsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.attachmentUris.forEach { uri ->
                    key(uri) {
                        AttachmentPreview(
                            uri = uri,
                            onRemove = { uri -> state.removeAttachmentUri(uri) })
                    }
                }
            }
        }
        TextField(
            value = text,
            onValueChange = {
                if (it.length <= (state.getProfile()?.maxInputChars ?: 40000)) {
                    state.onTextChanged(it)
                }
            },
            placeholder = { Text(if (isTemporaryChat) "Temporary chat" else "Ask Assistant") },
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
                    state.showAttachmentBottomSheet()
                }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add attachment")
                }

                val backgroundColor by animateColorAsState(
                    if (state.isSearchEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "Search button background"
                )
                val contentColor by animateColorAsState(
                    if (state.isSearchEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    label = "Search button content"
                )

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .animateContentSize()
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .clickable {
                            state.toggleSearch()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Toggle internet access",
                        tint = contentColor
                    )
                    if (state.isSearchEnabled) {
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
                        state.openModelBottomSheet()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = state.getProfile()?.name?.replace("(preview)", "")
                            ?: "Select a model",
                    )
                }
                FilledIconButton(
                    onClick = {
                        state.sendMessage(threadId)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }

    if (state.showModelBottomSheet) {
        ModelBottomSheet(
            assistantClient = assistantClient,
            coroutineScope = coroutineScope,
            onDismissRequest = { state.dismissModelBottomSheet() }
        )
    }

    if (state.showAttachmentBottomSheet) {
        AttachmentBottomSheet(
            onDismissRequest = { state.onDismissAttachmentBottomSheet() },
            onAttachment = { state.addAttachmentUri(it) })
    }
}
