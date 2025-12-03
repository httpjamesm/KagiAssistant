package space.httpjames.kagiassistantmaterial.ui.overlay

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun AssistantOverlayContent(
    state: AssistantOverlayState,
    continueInApp: () -> Unit,
    lines: Int,
    onLinesChanged: (Int) -> Unit,
    borderAlpha: Float,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                dragDistance += dragAmount
                            },
                            onDragEnd = {
                                if (dragDistance < -40f) {
                                    continueInApp()
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = {
                                dragDistance = 0f
                            }
                        )
                    },
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 700.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                ) {
                    AssistantMessageArea(
                        state = state,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(80.dp))
                }

                AssistantInputArea(
                    state = state,
                    lines = lines,
                    onLinesChanged = onLinesChanged,
                    borderAlpha = borderAlpha,
                    modifier = Modifier
                        .padding(
                            start = 8.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                            top = if (state.assistantMessage.isNotBlank()) 0.dp else 12.dp
                        )
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}
