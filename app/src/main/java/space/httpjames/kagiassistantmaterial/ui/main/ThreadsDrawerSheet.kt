package space.httpjames.kagiassistantmaterial.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsDrawerSheet(
    callState: DataFetchingState,
    threads: Map<String, List<AssistantThread>>,
    onThreadSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    val filteredThreads = if (searchQuery.isBlank()) {
        threads
    } else {
        threads.mapValues { (_, threadList) ->
            threadList.filter { thread ->
                thread.title.contains(searchQuery, ignoreCase = true) ||
                        thread.excerpt.contains(searchQuery, ignoreCase = true)
            }
        }.filterValues { it.isNotEmpty() }
    }

    ModalDrawerSheet(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (active) 0.dp else 16.dp, vertical = 8.dp),
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { active = false },
            active = active,
            onActiveChange = { active = it },
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        ) {
            ThreadList(
                threads = filteredThreads,
                onItemClick = { threadId ->
                    onThreadSelected(threadId)
                    active = false
                }
            )
        }

        if (!active) {
            Box(
                modifier = Modifier
                    .weight(1f)          // take remaining space
                    .fillMaxWidth()
            ) {
                when {
                    callState == DataFetchingState.FETCHING && threads.isEmpty() -> Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) { CircularProgressIndicator() }

                    callState == DataFetchingState.ERRORED && threads.isEmpty() -> ThreadListErrored(
                        onRetryClick = onRetryClick
                    )

                    else -> ThreadList(
                        threads = filteredThreads,
                        onItemClick = onThreadSelected
                    )
                }

                // Bottom solid row
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter), // always at bottom
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = onSettingsClick,
                        shape = RectangleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadList(
    threads: Map<String, List<AssistantThread>>,
    onItemClick: (String) -> Unit
) {
    LazyColumn {
        threads.entries.forEachIndexed { index, (category, threadList) ->
            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (index != 0) {
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = NumberFormat.getNumberInstance().format(threadList.size),
                            modifier = Modifier
                                .alpha(0.5f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            items(threadList) { thread ->
                NavigationDrawerItem(
                    label = {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = thread.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = thread.excerpt,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                minLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    selected = false,
                    onClick = { onItemClick(thread.id) },
                    shape = RectangleShape
                )
            }
        }
    }
}

@Composable
private fun ThreadListErrored(
    onRetryClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Failed to fetch threads")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetryClick) {
            Text("Retry")
        }
    }
}