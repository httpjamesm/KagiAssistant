package space.httpjames.kagiassistantmaterial.ui.overlay

import android.os.Bundle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.SharedFlow
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.chat.HtmlCard
import space.httpjames.kagiassistantmaterial.ui.chat.HtmlPreprocessor

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

    DisposableEffect(Unit) { onDispose { state.destroy() } }

    LaunchedEffect(Unit) {
        reinvokeFlow.collect { args ->
            println("Assistant was re-invoked while open")
            state.restartFlow()
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
                .padding(start = 12.dp, end = 12.dp, bottom = 36.dp),
            color = MaterialTheme.colorScheme.background,
            shape = if (state.assistantMessage.isBlank() && lines == 1) RoundedCornerShape(percent = 50)
            else RoundedCornerShape(16.dp)
        ) {
            Column {
                if (state.assistantMessage.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 0.dp)
                            .fillMaxWidth()
                    ) {
                        HtmlCard(
                            html = HtmlPreprocessor.preprocess("<p>${state.assistantMessage}</p>"),
                            onHeightMeasured = {},
                            minHeight = 0.dp
                        )
                    }
                }


                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                            top = if (state.assistantMessage.isNotBlank()) 0.dp else 12.dp
                        )
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            if (lines == 1) RoundedCornerShape(percent = 50) else RoundedCornerShape(
                                16.dp
                            )
                        )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        BasicTextField(
                            value = state.text,
                            onValueChange = { /* handled internally */ },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            onTextLayout = { textLayoutResult ->
                                lines = textLayoutResult.lineCount
                            },
                            maxLines = Int.MAX_VALUE,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.None
                            ),
                            modifier = Modifier
                                .weight(0.8f, fill = false)
                                .background(Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    innerTextField()
                                }
                            }
                        )

                        FilledIconButton(
                            onClick = { /* TODO */ },
                            modifier = Modifier
                                .border(
                                    width = 4.dp,
                                    if (state.isListening) col.copy(alpha = borderAlpha)
                                    else Color.Transparent,
                                    CircleShape
                                ),
                            enabled = state.text.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}



