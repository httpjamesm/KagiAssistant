package space.httpjames.kagiassistantmaterial.ui.message

import android.net.Uri
import android.provider.OpenableColumns
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.core.net.toUri

@Composable
fun AttachmentPreview(uri: String, onRemove: (uri: String) -> Unit, readOnly: Boolean = false) {
    val parsedUri = remember(uri) { uri.toUri() }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    val fileName = rememberFileName(parsedUri)

    val mime = ctx.contentResolver.getType(parsedUri)          // e.g. "image/webp"

    if (mime?.startsWith("image") ?: false) {
        Box(modifier = Modifier.size(84.dp)) {
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

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .clickable {
                        onRemove(uri)
                    }
                    .background(Color.Gray),
            )
        }
    } else {
        Box(modifier = Modifier.width(200.dp).height(84.dp).background(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        )) {
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(text = fileName ?: "Unknown", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = mime ?: "Unknown", style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.8f))
                }
            }

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                     .clickable {
                        onRemove(uri)
                    }
                    .align(
                        Alignment.TopEnd
                    )
                    .background(Color.Gray),
            )
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
