package space.httpjames.kagiassistantmaterial.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import space.httpjames.kagiassistantmaterial.ui.shared.DynamicAssistantIcon

@Composable
fun EmptyChatPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DynamicAssistantIcon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(96.dp)
                    .alpha(0.6f),
            )
            Text(
                "Kagi Assistant",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(0.6f)
            )
        }
    }

}