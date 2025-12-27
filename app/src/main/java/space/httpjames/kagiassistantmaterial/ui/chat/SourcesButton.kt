package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun SourcesButton(
    domains: List<String>,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .width(iconSize + (iconSize * 0.6f * (domains.size - 1)))
                .height(iconSize)
        ) {
            domains.forEachIndexed { index, domain ->
                val iconUrl = remember(domain) {
                    "https://icons.duckduckgo.com/ip3/$domain.ico"
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .align(Alignment.CenterStart)
                        .offset(x = iconSize * index * 0.6f) // overlap by ~40 %
                        .zIndex((domains.size - index).toFloat()) // right-most on top
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }

        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
