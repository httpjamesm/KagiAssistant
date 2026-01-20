package space.httpjames.kagiassistantmaterial.ui.chat

import android.annotation.SuppressLint
import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageDocument
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.Citation
import space.httpjames.kagiassistantmaterial.ui.message.ShimmeringMessagePlaceholder
import space.httpjames.kagiassistantmaterial.ui.shared.DynamicAssistantIcon
import java.net.URI

const val MAX_USER_MESSAGE_LENGTH = 500

sealed class ContentSegment {
    data class Event(
        val title: String,
        val content: String,
        val timestamp: String? = null
    ) : ContentSegment()

    data class HtmlContent(
        val html: String
    ) : ContentSegment()
}

object ContentParser {
    private val detailsRegex = Regex(
        """<details>(.*?)</details>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val summaryRegex = Regex(
        """<summary>(.*?)</summary>""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parseContent(html: String): List<ContentSegment> {
        if (html.isEmpty()) return emptyList()

        val segments = mutableListOf<ContentSegment>()
        var lastIndex = 0

        detailsRegex.findAll(html).forEach { match ->
            // Add content before this <details> block
            if (match.range.first > lastIndex) {
                val htmlBefore = html.substring(lastIndex, match.range.first).trim()
                if (htmlBefore.isNotEmpty()) {
                    segments.add(ContentSegment.HtmlContent(htmlBefore))
                }
            }

            val detailsContent = match.groupValues[1]

            // Extract summary
            val summaryMatch = summaryRegex.find(detailsContent)
            val rawSummary = summaryMatch?.groupValues?.get(1)?.trim()

            // Take only the part before any '<'
            var title = rawSummary
                ?.substringBefore("<")
                ?.trim()
                ?: "Action"

            // strip the trailing colon
            title = title.removeSuffix(":")

            // if contains "key details", remove the trailing "from"
            if (title.contains("key details")) {
                title = title.removeSuffix("from")
            }

            title = title.trim()


            // Extract content after </summary>
            val content = if (summaryMatch != null) {
                detailsContent.substring(summaryMatch.range.last + 1).trim()
            } else {
                detailsContent.trim()
            }

            segments.add(ContentSegment.Event(title, content))
            lastIndex = match.range.last + 1
        }

        // Add remaining content after last <details>
        if (lastIndex < html.length) {
            val htmlAfter = html.substring(lastIndex).trim()
            if (htmlAfter.isNotEmpty()) {
                segments.add(ContentSegment.HtmlContent(htmlAfter))
            }
        }

        return segments
    }
}

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
    var isUserMessageExpanded by remember { mutableStateOf(false) }

    val contentSegments = remember(content, id) {
        ContentParser.parseContent(content)
    }

    val eventCompletionStates = remember(contentSegments, finishedGenerating) {
        contentSegments.mapIndexed { index, segment ->
            when (segment) {
                is ContentSegment.Event -> {
                    // If titleends with a period, NOT completed
                    // Otherwise, completed only if there's a segment after
                    if (segment.title.endsWith(".")) {
                        false
                    } else {
                        index < contentSegments.lastIndex
                    }
                }

                else -> false
            }
        }
    }


    val eventExpandedStates = remember(id) {
        mutableStateMapOf<Int, Boolean>()
    }


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
                        val displayContent =
                            if (!isUserMessageExpanded && content.length > MAX_USER_MESSAGE_LENGTH) {
                                content.take(MAX_USER_MESSAGE_LENGTH) + "..."
                            } else {
                                content
                            }

                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .widthIn(max = this@BoxWithConstraints.maxWidth * 0.7f)
                                .animateContentSize(),
                        ) {
                            Text(
                                text = displayContent.ifBlank { "Empty message" },
                                modifier = Modifier
                                    .alpha(if (content.isBlank()) 0.8f else 1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = if (content.isBlank()) FontStyle.Italic else null,
                            )

                            if (content.length > MAX_USER_MESSAGE_LENGTH) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Text(
                                        text = "Read ${if (isUserMessageExpanded) "less" else "more"}",
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable {
                                                isUserMessageExpanded = !isUserMessageExpanded
                                            }
                                            .padding(4.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Column {
                            DynamicAssistantIcon(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(32.dp),
                            )

                            if (content.isEmpty()) {
                                ShimmeringMessagePlaceholder()
                            } else {
                                var eventIndex = 0

                                contentSegments.forEachIndexed { index, segment ->
                                    key("$id-segment-$index") {
                                        when (segment) {
                                            is ContentSegment.Event -> {
                                                val currentEventIndex = eventIndex
                                                eventIndex++

                                                ChatEvent(
                                                    completed = eventCompletionStates[index],
                                                    displayText = segment.title,
                                                    content = segment.content,
                                                    expanded = eventExpandedStates[currentEventIndex]
                                                        ?: false,
                                                    onExpandRequest = {
                                                        eventExpandedStates[currentEventIndex] =
                                                            !(eventExpandedStates[currentEventIndex]
                                                                ?: false)
                                                    }
                                                )
                                            }

                                            is ContentSegment.HtmlContent -> {
                                                HtmlCard(
                                                    html = HtmlPreprocessor.preprocess(segment.html),
                                                    onHeightMeasured = if (index == contentSegments.lastIndex)
                                                        onHeightMeasured else null
                                                )
                                            }
                                        }
                                    }
                                }

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