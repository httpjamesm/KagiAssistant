package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun ShimmeringText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default
) {
    val transition = rememberInfiniteTransition(label = "shimmer")

    val offset = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColors = listOf(
        Color(0xFF9E9E9E),
        Color(0xFFFFFFFF),
        Color(0xFF9E9E9E)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(
            offset.value * 1500f - 300f,
            0f
        ),
        end = androidx.compose.ui.geometry.Offset(
            offset.value * 1500f - 100f,
            0f
        )
    )

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush)
    )
}
