package space.httpjames.kagiassistantmaterial.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.message.nameWithoutParentheticals
import space.httpjames.kagiassistantmaterial.ui.viewmodel.AssistantViewModelFactory
import space.httpjames.kagiassistantmaterial.ui.viewmodel.ModelBottomSheetViewModel
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelBottomSheet(
    assistantClient: AssistantClient,
    prefs: android.content.SharedPreferences,
    cacheDir: String,
    onDismissRequest: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )

    val viewModel: ModelBottomSheetViewModel = viewModel(
        factory = AssistantViewModelFactory(assistantClient, prefs, cacheDir)
    )
    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LaunchedEffect(Unit) {
            viewModel.fetchProfiles()
        }
        if (uiState.profiles.isEmpty() && uiState.profilesCallState == DataFetchingState.FETCHING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.profiles.isEmpty() && uiState.profilesCallState == DataFetchingState.ERRORED) {
            ModelsLoadingErrored(onRetryClick = { viewModel.fetchProfiles() })
        } else {
            val recentlyUsedProfiles = uiState.recentlyUsedProfiles
            val remainingProfiles = uiState.filteredProfiles
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
                            val selectedProfile = uiState.selectedProfile
                            val isSelected = selectedProfile?.key == profile.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onProfileSelected(profile)
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
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = profile.name!!.nameWithoutParentheticals(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (uiState.profileKeysWithReasoningCapability.contains(profile.key)) {
                                        Icon(
                                            imageVector = Icons.Filled.Lightbulb,
                                            contentDescription = "Reasoning capable",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (profile.modelName.contains("(Experimental)")) {
                                        Icon(
                                            imageVector = Icons.Filled.Science,
                                            contentDescription = "Experimental",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
                            val selectedProfile = uiState.selectedProfile
                            val isSelected = selectedProfile?.key == profile.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onProfileSelected(profile)
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
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = profile.name!!.nameWithoutParentheticals(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (uiState.profileKeysWithReasoningCapability.contains(profile.key)) {
                                        Icon(
                                            imageVector = Icons.Filled.Lightbulb,
                                            contentDescription = "Reasoning capable",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (profile.modelName.contains("(Experimental)")) {
                                        Icon(
                                            imageVector = Icons.Filled.Science,
                                            contentDescription = "Experimental",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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

@Composable
fun ModelsLoadingErrored(onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Failed to fetch models")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetryClick) {
            Text("Retry")
        }
    }
}