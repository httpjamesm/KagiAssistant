package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.AssistantThreadMessage
import space.httpjames.kagiassistantmaterial.R

@Composable
fun ChatArea(
    assistantClient: AssistantClient,
    modifier: Modifier = Modifier,
    threadMessages: List<AssistantThreadMessage>,
) {
    val state = rememberChatAreaState(assistantClient)

    val scrollState = rememberScrollState()

    if (threadMessages.isNotEmpty()) {
        Column(
            modifier = modifier.verticalScroll(scrollState).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            threadMessages.forEach { threadMessage ->
                key(threadMessage.id) {
                    ChatMessage(id = threadMessage.id, content = threadMessage.content, role = threadMessage.role, citations = threadMessage.citations, documents = threadMessage.documents)
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
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
                    modifier = Modifier.padding(12.dp).size(96.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Kagi Assistant",
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }


    LaunchedEffect(threadMessages) {
        if (threadMessages.isNotEmpty()) {
            awaitFrame()
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
}