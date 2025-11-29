package space.httpjames.kagiassistantmaterial.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun InitialsAvatar(
    char: Char,
    size: Dp = 96.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall,
    modifier: Modifier = Modifier
) {
    val hue = (char.code * 47) % 360
    val bg = Color.hsl(
        hue = hue.toFloat(),
        saturation = 0.65f,
        lightness = 0.55f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = modifier
                .height(size)
                .width(size)
                .clip(CircleShape)
                .background(bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = char.uppercase(),
                    fontSize = textStyle.fontSize,
                    style = textStyle.copy(color = Color.White)
                )
            }
        }
    }
}
