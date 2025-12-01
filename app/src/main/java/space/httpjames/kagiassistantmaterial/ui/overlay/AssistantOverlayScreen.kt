package space.httpjames.kagiassistantmaterial.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.MainActivity
import space.httpjames.kagiassistantmaterial.R
import space.httpjames.kagiassistantmaterial.ui.chat.HtmlCard
import space.httpjames.kagiassistantmaterial.ui.chat.HtmlPreprocessor
import space.httpjames.kagiassistantmaterial.ui.message.ShimmeringMessagePlaceholder

@Composable
fun AssistantOverlayScreen(
    assistantClient: AssistantClient,
    reinvokeFlow: SharedFlow<Bundle?>,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val col = MaterialTheme.colorScheme.primary
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state = rememberAssistantOverlayState(assistantClient, context, coroutineScope)

    var lines by rememberSaveable { mutableIntStateOf(1) }

    val localFocusContext = LocalFocusManager.current

    DisposableEffect(Unit) { onDispose { state.destroy() } }

    LaunchedEffect(Unit) {
        reinvokeFlow.collect { args ->
            state.restartFlow()
            localFocusContext.clearFocus()
        }
    }

    var dragDistance by remember { mutableStateOf(0f) }

    fun continueInApp() {
        coroutineScope.launch {
            localFocusContext.clearFocus()
            state.saveThreadId()
            state.saveText()
            awaitFrame()
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            dragDistance += dragAmount
                        },
                        onDragEnd = {
                            if (dragDistance < -80f) {
                                continueInApp()
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = {
                            dragDistance = 0f
                        }
                    )
                }

                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background,
            shape = if (state.assistantMessage.isBlank() && lines == 1 && !state.isWaitingForMessageFirstToken && state.assistantDone) RoundedCornerShape(
                32.dp
            )
            else RoundedCornerShape(16.dp)
        ) {

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(50))
                            .alpha(0.15f),
                        thickness = 6.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }


                if (state.assistantMessage.isNotEmpty() || state.isWaitingForMessageFirstToken || !state.assistantDone) {
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 0.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 0.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.fetch_ball_icon),
                                contentDescription = "",
                                tint = Color.Unspecified,
                                modifier = Modifier

                                    .size(32.dp),
                            )

                            FilledIconButton(
                                onClick = {
                                    if (state.isSpeaking) {
                                        state.stopSpeaking()
                                    } else {
                                        state.restartSpeaking()
                                    }
                                }, colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (state.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isSpeaking) "Stop speaking" else "Restart speaking",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (state.assistantMessage.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 60.dp)
                                    .fillMaxWidth()
                            ) {
                                HtmlCard(
                                    html = HtmlPreprocessor.preprocess("<p>${state.assistantMessage}</p>"),
                                    onHeightMeasured = {},
                                    minHeight = 60.dp
                                )
                            }
                        } else if (state.isWaitingForMessageFirstToken) {
                            ShimmeringMessagePlaceholder(
                                showNum = 2
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(
                            start = 8.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                            top = if (state.assistantMessage.isNotBlank()) 0.dp else 12.dp
                        )
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            if (state.assistantMessage.isBlank() && lines == 1 && !state.isWaitingForMessageFirstToken && state.assistantDone) RoundedCornerShape(
                                32.dp
                            ) else RoundedCornerShape(
                                16.dp
                            )
                        )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = if (lines > 1) Alignment.Bottom else Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val focusRequester = remember { FocusRequester() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { focusRequester.requestFocus() }   // open keyboard
                        ) {
                            BasicTextField(
                                value = state.text,
                                onValueChange = { state.onTextChanged(it) },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                onTextLayout = { textLayoutResult ->
                                    lines = textLayoutResult.lineCount
                                },
                                maxLines = Int.MAX_VALUE,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.None
                                ),
                                modifier = Modifier
                                    .background(Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .onFocusEvent { event ->
                                        if (event.isFocused) {
                                            state.stopListening()
                                        }

                                        state._setIsTypingMode(event.isFocused)
                                    }
                                    .animateContentSize()
                                    .fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        // placeholder
                                        if (state.text.isEmpty()) {
                                            Text(
                                                text = "Speak or tap to type",
                                                style = LocalTextStyle.current.copy(
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = .5f
                                                    )
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (state.isListening) {
                                    state.stopListening()
                                } else if (!state.isListening && state.text.isEmpty()) {
                                    state.restartFlow()
                                } else {
                                    state.sendMessage()
                                    localFocusContext.clearFocus()
                                    state.onTextChanged("")
                                }
                            },
                            modifier = Modifier
                                .border(
                                    width = 4.dp,
                                    if (state.isListening) col.copy(alpha = borderAlpha)
                                    else Color.Transparent,
                                    CircleShape
                                )
                                .padding(8.dp)
                                .size(48.dp),
                            enabled = state.text.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isListening) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (state.isListening) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isTypingMode) Icons.Default.Send else Icons.Default.Mic,
                                contentDescription = if (state.isTypingMode) "Send message" else null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun Modifier.customImePadding(extraBottom: Int = 0): Modifier {
    val ime = WindowInsets.ime
    val density = LocalDensity.current

    val bottomDp = with(density) {
        // Read the IME bottom inset in px and convert to dp
        ime.getBottom(this).toDp()
    }

    return this.padding(
        bottom = bottomDp + extraBottom.dp
    )
}
