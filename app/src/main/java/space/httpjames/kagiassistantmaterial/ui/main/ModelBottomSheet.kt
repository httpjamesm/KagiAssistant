package space.httpjames.kagiassistantmaterial.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import space.httpjames.kagiassistantmaterial.AssistantClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelBottomSheet(
    assistantClient: AssistantClient,
    coroutineScope: CoroutineScope,
    onDismissRequest: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )

    val state = rememberModelBottomSheetState(
        assistantClient = assistantClient,
        coroutineScope = coroutineScope
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LaunchedEffect(Unit) {
            state.fetchProfiles()
        }
        if (state.profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val recentlyUsedProfiles = state.getRecentlyUsedProfiles()
            val remainingProfiles = state.filteredProfiles
            val groupedProfiles = remainingProfiles.groupBy { it.family }

            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (recentlyUsedProfiles.isNotEmpty()) {
                        stickyHeader {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(
                                            bottom = 8.dp,
                                            top = 8.dp,
                                            start = 16.dp,
                                            end = 16.dp
                                        ),
                                ) {
                                    Text(
                                        text = "RECENTLY USED",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        items(recentlyUsedProfiles) { profile ->
                            val selectedProfile = state.getProfile()
                            val isSelected = selectedProfile?.key == profile.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.onProfileSelected(profile)
                                        onDismissRequest()
                                    }
                                    .padding(
                                        start = 16.dp,
                                        top = 16.dp,
                                        bottom = 16.dp,
                                        end = 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    groupedProfiles.forEach { (family, profilesInFamily) ->
                        stickyHeader {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(
                                            bottom = 8.dp,
                                            top = 8.dp,
                                            start = 16.dp,
                                            end = 16.dp
                                        ),
                                ) {
                                    Text(
                                        text = family.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        items(profilesInFamily) { profile ->
                            val selectedProfile = state.getProfile()
                            val isSelected = selectedProfile?.key == profile.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.onProfileSelected(profile)
                                        onDismissRequest()
                                    }
                                    .padding(
                                        start = 16.dp,
                                        top = 16.dp,
                                        bottom = 16.dp,
                                        end = 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
