package space.httpjames.kagiassistantmaterial.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.MainActivity
import space.httpjames.kagiassistantmaterial.ui.assist.OverlayActionButton

@Composable
fun AssistantOverlayScreen(
    assistantClient: AssistantClient,
    reinvokeFlow: SharedFlow<Bundle?>,
    screenshotFlow: SharedFlow<Bitmap?>,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "border animation")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border alpha"
    )
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val state = rememberAssistantOverlayState(assistantClient, context, coroutineScope)
    val localFocusContext = LocalFocusManager.current
    val screenshot by screenshotFlow.collectAsState(initial = null)
    val haptics = LocalHapticFeedback.current

    fun continueInApp() {
        coroutineScope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    LaunchedEffect(Unit) {
        val useMiniOverlay = prefs.getBoolean("use_mini_overlay", true)
        if (useMiniOverlay) {
            visible = true
        } else {
            continueInApp()
        }
    }

    LaunchedEffect(screenshot) {
        state._setScreenshot(screenshot)
    }

    var lines by rememberSaveable { mutableIntStateOf(1) }


    DisposableEffect(Unit) { onDispose { state.destroy() } }

    LaunchedEffect(Unit) {
        reinvokeFlow.collect {
            state.restartFlow()
            localFocusContext.clearFocus()
        }
    }

    LaunchedEffect(visible) {
        state.reset()
    }

    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        coroutineScope.launch {
                            visible = false
                            awaitFrame()
                            onDismiss()
                        }
                    }
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement
                        .spacedBy(12.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    if (screenshot != null) {
                        OverlayActionButton(
                            onClick = {
                                state.toggleScreenshotAttached()
                            },
                            done = state.screenshotAttached,
                            actionIcon = Icons.Default.Screenshot,
                            actionText = "Attach Screenshot",
                            doneActionText = "Screenshot Attached"
                        )
                    }
                }

                AssistantOverlayContent(
                    state = state,
                    continueInApp = ::continueInApp,
                    lines = lines,
                    onLinesChanged = { lines = it },
                    borderAlpha = borderAlpha,
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}


