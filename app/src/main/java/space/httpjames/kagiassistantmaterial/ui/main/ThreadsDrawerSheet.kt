package space.httpjames.kagiassistantmaterial.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.httpjames.kagiassistantmaterial.AssistantThread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsDrawerSheet(
    isLoading: Boolean,
    threads: Map<String, List<AssistantThread>>,
    onThreadSelected: (String) -> Unit,
    modifier: Modifier = Modifier
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
            if (isLoading && threads.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                ThreadList(
                    threads = filteredThreads,
                    onItemClick = onThreadSelected
                )
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
        threads.forEach { (category, threadList) ->
            item {
                Text(
                    text = category,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
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