package space.httpjames.kagiassistantmaterial.ui.chat

import android.annotation.SuppressLint
import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.R
import space.httpjames.kagiassistantmaterial.ui.message.ShimmeringMessagePlaceholder
import java.net.URI


@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ChatMessage(
    id: String,
    content: String,
    role: AssistantThreadMessageRole,
    citations: List<Citation> = emptyList(),
    documents: List<AssistantThreadMessageDocument> = emptyList(),
    onEdit: () -> Unit,
    onHeightMeasured: (() -> Unit)? = null,
    finishedGenerating: Boolean = true,
    markdownContent: String?,
    metadata: Map<String, String> = emptyMap(),
) {
    val isMe = role == AssistantThreadMessageRole.USER
    val background = if (isMe) MaterialTheme.colorScheme.primary
    else Color.Transparent
    val shape = if (isMe)
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(0.dp)

    var showSourcesSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showMetadataModal by remember { mutableStateOf(false) }


    val documentsScroll = rememberScrollState()

    val clipboard = LocalClipboard.current

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Box {
                Surface(
                    shape = shape,
                    color = background,
                    tonalElevation = 2.dp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                menuExpanded = true
                            }
                        )
                    }
                ) {
                    if (isMe) {
                        Text(
                            text = content,
                            modifier = Modifier
                                .padding(12.dp)
                                .widthIn(max = this@BoxWithConstraints.maxWidth * 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Column {
                            Icon(
                                painter = painterResource(R.drawable.fetch_ball_icon),
                                contentDescription = "",
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(32.dp),
                            )

                            if (content.isEmpty()) {
                                ShimmeringMessagePlaceholder()
                            } else {
                                HtmlCard(
                                    html = HtmlPreprocessor.preprocess(content),
                                    onHeightMeasured = onHeightMeasured
                                )

                                if (finishedGenerating) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = if (citations.isNotEmpty()) Arrangement.SpaceBetween else Arrangement.End
                                    ) {
                                        if (citations.isNotEmpty()) {
                                            SourcesButton(
                                                domains = citations.take(3)
                                                    .map { URI(it.url).host ?: "" },
                                                text = "Sources",
                                                onClick = {
                                                    showSourcesSheet = true
                                                }
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        clipboard.setClipEntry(
                                                            ClipEntry(
                                                                ClipData.newPlainText(
                                                                    "message",
                                                                    markdownContent
                                                                )
                                                            )
                                                        )
                                                    }
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = "Copy message"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    showMetadataModal = true
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = "Show message metadata"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ChatMessageDropdownMenu(
                    menuExpanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    isMe = isMe,
                    onEdit = onEdit,
                    onCopy = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "message",
                                        content
                                    )
                                )
                            )
                        }
                    }
                )
            }

            if (documents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 12.dp)
                        .fillMaxWidth()
                        .horizontalScroll(documentsScroll),
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    key(documents) {
                        documents.forEach { document ->
                            if (document.data != null) {
                                Surface(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .background(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.background
                                        )
                                ) {
                                    Image(
                                        bitmap = document.data.asImageBitmap(),
                                        modifier = Modifier.fillMaxSize(),
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(84.dp)
                                        .background(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = document.name,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = document.mime,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.alpha(0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourcesSheet) {
        SourcesBottomSheet(citations = citations, onDismissRequest = {
            showSourcesSheet = false
        })
    }

    if (showMetadataModal) {
        MetadataModal(metadata = metadata, onDismissRequest = {
            showMetadataModal = false
        })
    }


}