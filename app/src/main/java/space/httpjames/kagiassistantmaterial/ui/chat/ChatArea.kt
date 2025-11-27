package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.AssistantThreadMessageRole
import space.httpjames.kagiassistantmaterial.R

@Composable
fun ChatArea(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    currentThreadId: String?,
    threadMessages: List<AssistantThreadMessage>,
    onEdit: (String) -> Unit,
) {

    val scrollState = rememberScrollState()
    var pendingMeasurements by remember { mutableIntStateOf(0) }
    var measurementComplete by remember { mutableStateOf(false) }
    var previousThreadId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showButton by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.isScrollInProgress, scrollState.value) {
        if (!scrollState.isScrollInProgress) {
            showButton = scrollState.value < scrollState.maxValue
        } else {
            showButton = false
        }
    }

    Crossfade(
        targetState = threadMessages.isEmpty(),
        modifier = modifier,
        animationSpec = tween(durationMillis = 1200),
        label = "ChatAreaCrossfade"
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else if (!it) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    threadMessages.forEach { threadMessage ->
                        key(threadMessage.id) {
                            ChatMessage(
                                id = threadMessage.id,
                                content = threadMessage.content,
                                role = threadMessage.role,
                                citations = threadMessage.citations,
                                documents = threadMessage.documents,
                                onEdit = {
                                    onEdit(threadMessage.id)
                                },
                                onHeightMeasured = {
                                    pendingMeasurements--
                                    if (pendingMeasurements <= 0) {
                                        measurementComplete = true
                                    }
                                },
                                finishedGenerating = threadMessage.finishedGenerating,
                                markdownContent = threadMessage.markdownContent,
                                metadata = threadMessage.metadata,
                            )
                        }
                    }
                }

                Crossfade(
                    targetState = showButton,
                    animationSpec = tween(durationMillis = 1200),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                ) { show ->
                    if (show) {
                        Row(
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledIconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scrollState.scrollTo(scrollState.maxValue)
                                        showButton = false
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.inverseOnSurface
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "Scroll down",
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(36.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fetch_ball_icon),
                        contentDescription = "",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(96.dp)
                            .alpha(0.6f),
                    )
                    Text(
                        "Kagi Assistant",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.alpha(0.6f)
                    )
                }
            }
        }
    }


    LaunchedEffect(currentThreadId) {
        if (currentThreadId != previousThreadId) {
            pendingMeasurements =
                threadMessages.count { it.role == AssistantThreadMessageRole.ASSISTANT }
            measurementComplete = false
        }
    }

    LaunchedEffect(currentThreadId, measurementComplete) {
        if (threadMessages.isNotEmpty() && measurementComplete && currentThreadId != previousThreadId) {
            awaitFrame()
            scrollState.scrollTo(scrollState.maxValue)
            previousThreadId = currentThreadId  // Update AFTER scroll
            measurementComplete = false
        }
    }


    LaunchedEffect(currentThreadId) {
        pendingMeasurements =
            threadMessages.count { it.role == AssistantThreadMessageRole.ASSISTANT }
        measurementComplete = false
    }
}