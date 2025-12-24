package space.httpjames.kagiassistantmaterial.ui.message

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage

@Composable
fun AttachmentPreview(uri: String, onRemove: (uri: String) -> Unit, readOnly: Boolean = false) {
    val parsedUri = remember(uri) { uri.toUri() }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val fileName = rememberFileName(parsedUri)
    val mime = ctx.contentResolver.getType(parsedUri)          // e.g. "image/webp"

    fun handleRemove(withHaptic: Boolean = true) {
        if (withHaptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onRemove(uri)
    }

    if (mime?.startsWith("image") ?: false) {
        SwipeToRemoveBox(
            uri = uri,
            modifier = Modifier.size(84.dp),
            onRemove = { handleRemove() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPreview = true }
            ) {
                AsyncImage(
                    model = parsedUri,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            RemoveButton(onClick = { handleRemove(false) })
        }
    } else {
        SwipeToRemoveBox(
            uri = uri,
            modifier = Modifier
                .width(200.dp)
                .height(84.dp)
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background
                ),
            onRemove = { handleRemove() }
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = fileName ?: "Unknown",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mime ?: "Unknown",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.alpha(0.8f)
                )
            }
            RemoveButton(onClick = { handleRemove(false) })
        }
    }

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showPreview = false }
            ) {
                AsyncImage(
                    model = parsedUri,
                    contentScale = ContentScale.Fit,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun rememberFileName(uri: Uri?): String? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            val cursor = context.contentResolver.query(
                it,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        }
    }
}

@Composable
private fun SwipeToRemoveBox(
    uri: String,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.toInt()) }
            .pointerInput(uri) {
                var startY = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY < -100f) {
                            onRemove()
                        }
                        offsetY = 0f
                    },
                    onDragCancel = {
                        offsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Only allow upward movement (negative values)
                        if (dragAmount < 0 || startY + dragAmount < 0) {
                            startY = (startY + dragAmount).coerceAtMost(0f)
                            offsetY = startY
                            if (offsetY < -200f) {
                                onRemove()
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@Composable
private fun BoxScope.RemoveButton(onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        modifier = Modifier
            .padding(4.dp)
            .size(32.dp)
            .align(Alignment.TopEnd)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
