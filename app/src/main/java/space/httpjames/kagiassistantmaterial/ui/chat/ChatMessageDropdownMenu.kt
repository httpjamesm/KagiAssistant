package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun ChatMessageDropdownMenu(
    menuExpanded: Boolean,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit = {},
    onEdit: () -> Unit = {},
    isMe: Boolean,
) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(menuExpanded) {
        if (menuExpanded) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Copy") },
            onClick = {
                onCopy()
                onDismissRequest()
            }
        )
        if (isMe) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onEdit()
                    onDismissRequest()
                }
            )
        }
    }

}