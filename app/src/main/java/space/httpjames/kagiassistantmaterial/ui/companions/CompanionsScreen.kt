package space.httpjames.kagiassistantmaterial.ui.companions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.viewmodel.AssistantViewModelFactory
import space.httpjames.kagiassistantmaterial.ui.viewmodel.CompanionsViewModel
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionsScreen(
    assistantClient: AssistantClient,
    navController: NavController,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("assistant_prefs", android.content.Context.MODE_PRIVATE)
    val cacheDir = context.cacheDir.absolutePath

    val viewModel: CompanionsViewModel = viewModel(
        factory = AssistantViewModelFactory(assistantClient, prefs, cacheDir)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runInit()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Choose a companion")
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.companionsFetchingState == DataFetchingState.FETCHING) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.companionsFetchingState == DataFetchingState.OK) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.companions.size) { item ->
                        CompanionItem(
                            uiState.companions[item],
                            uiState.companions[item].id == uiState.selectedCompanion,
                            onSelect = {
                                if (uiState.companions[item].id == uiState.selectedCompanion) {
                                    viewModel.setCompanion(null)
                                } else {
                                    viewModel.setCompanion(uiState.companions[item].id)
                                }
                            })
                    }
                }
            } else {
                Text("Error fetching companions")
            }
        }

    }
}