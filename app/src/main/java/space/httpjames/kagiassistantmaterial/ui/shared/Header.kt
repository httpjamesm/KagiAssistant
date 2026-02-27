package space.httpjames.kagiassistantmaterial.ui.shared

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ChatActionIcon {
    NewChat,
    TemporaryChat
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header(
    currentThreadId: String?,
    threadTitle: String?,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTemporaryChatClick: () -> Unit,
    isTemporaryChat: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }

    val iconState = when {
        currentThreadId != null || isTemporaryChat -> ChatActionIcon.NewChat
        else -> ChatActionIcon.TemporaryChat
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Crossfade(
                targetState = threadTitle,
                label = "Thread Title",
                animationSpec = tween(durationMillis = 1200)
            ) {
                if (it != null) {
                    Box {
                        Text(
                            text = threadTitle ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = {
                                    onCopyClick()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDeleteClick()
                                    showMenu = false
                                }
                            )
                        }
                    }
                } else {
                    Spacer(
                        modifier = Modifier.height(1.dp)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (currentThreadId != null) onNewChatClick() else onTemporaryChatClick()
                },
                enabled = true
            ) {
                Crossfade(
                    targetState = iconState,
                    label = "Chat action icon",
                    animationSpec = tween(750)
                ) { state ->
                    Icon(
                        imageVector = when (state) {
                            ChatActionIcon.NewChat -> Icons.Outlined.Add
                            ChatActionIcon.TemporaryChat -> Icons.Outlined.HourglassEmpty
                        },
                        contentDescription = null,
                    )
                }
            }
        }
    )
}
