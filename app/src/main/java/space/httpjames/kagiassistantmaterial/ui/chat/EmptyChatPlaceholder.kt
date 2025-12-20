package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.ui.unit.sp
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
                        "This chat expires automatically.",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .alpha(0.6f)
                            .padding(top = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(0.6f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("15 min inactivity", fontSize = 14.sp)
                        }
                        Text("/", fontSize = 24.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("On chat clear", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

}