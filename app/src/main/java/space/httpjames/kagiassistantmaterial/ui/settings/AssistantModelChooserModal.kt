package space.httpjames.kagiassistantmaterial.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.httpjames.kagiassistantmaterial.ui.message.AssistantProfile

@Composable
fun AssistantModelChooserModal(
    profiles: List<AssistantProfile>,
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
    selectedKey: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a model") },
        text = {
            // Wrapped in Box to avoid height constraints of `AlertDialog`
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    items(profiles) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModelSelected(option.key)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.key == selectedKey,
                                onClick = {
                                    onModelSelected(option.key)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.name)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

}