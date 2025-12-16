package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import space.httpjames.kagiassistantmaterial.R
import space.httpjames.kagiassistantmaterial.ui.shared.DynamicAssistantIcon

@Composable
fun EmptyChatPlaceholder(
    isTemporaryChat: Boolean,
) {
    val iconModifier = Modifier
        .padding(12.dp)
        .alpha(0.6f)


    Crossfade(
        targetState = isTemporaryChat,
        label = "Empty Chat Placeholder",
        animationSpec = tween(750),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!it) {
                    DynamicAssistantIcon(
                        modifier = iconModifier.size(96.dp),
                    )
                    Text(
                        "Kagi Assistant",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.alpha(0.6f)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.privacy_doggo_icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = iconModifier.height(96.dp),
                    )
                    Text(
                        "Temporary Chat",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.alpha(0.6f)
                    )
                    Text(
                        "This chat will be automatically deleted.",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .alpha(0.6f)
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    }

}