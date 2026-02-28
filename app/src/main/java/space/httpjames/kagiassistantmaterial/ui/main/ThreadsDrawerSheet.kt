package space.httpjames.kagiassistantmaterial.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jsoup.Jsoup
import space.httpjames.kagiassistantmaterial.AssistantThread
import space.httpjames.kagiassistantmaterial.ThreadSearchResult
import space.httpjames.kagiassistantmaterial.utils.DataFetchingState
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ThreadsDrawerSheet(
    callState: DataFetchingState,
    threads: Map<String, List<AssistantThread>>,
    onThreadSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onRetryClick: () -> Unit,
    predictiveBackProgress: Float = 0f,
    currentThreadId: String?,
    generatingThreadIds: Set<String>,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    searchResults: List<ThreadSearchResult>? = null,
    isSearching: Boolean = false,
    isLoadingSearchPages: Boolean = false,
    onSearch: (String) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    // Debounced server-side search
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { query ->
                onSearch(query)
            }
    }

    // Determine filtered threads based on search state
    val displayThreads = remember(searchQuery, threads, searchResults) {
        when {
            searchQuery.isBlank() -> threads
            // Short query: client-side filter
            searchQuery.length < 3 || searchResults == null -> {
                threads.mapValues { (_, threadList) ->
                    threadList.filter { thread ->
                        thread.title.contains(searchQuery, ignoreCase = true) ||
                                thread.excerpt.contains(searchQuery, ignoreCase = true)
                    }
                }.filterValues { it.isNotEmpty() }
            }
            // Server-side search: filter to matching thread_ids, replace excerpts with snippets
            else -> {
                val matchingIds = searchResults.map { it.thread_id }.toSet()
                val snippetMap = searchResults.associate { it.thread_id to it.snippet }
                threads.mapValues { (_, threadList) ->
                    threadList.filter { it.id in matchingIds }.map { thread ->
                        val snippet = snippetMap[thread.id]
                        if (snippet != null) {
                            thread.copy(excerpt = Jsoup.parse(snippet).text())
                        } else {
                            thread
                        }
                    }
                }.filterValues { it.isNotEmpty() }
            }
        }
    }

    val density = LocalDensity.current
    val drawerWidthDp = 360.dp

    ModalDrawerSheet(
        modifier = modifier.then(
            Modifier.graphicsLayer {
                val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
                translationX = -drawerWidthPx * predictiveBackProgress
            }
        )
    ) {
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
            when {
                isSearching -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { CircularProgressIndicator() }

                searchQuery.isNotBlank() && displayThreads.values.all { it.isEmpty() } && !isLoadingSearchPages ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No results found", style = MaterialTheme.typography.bodyMedium)
                    }

                else -> ThreadList(
                    threads = displayThreads,
                    onItemClick = { threadId ->
                        onThreadSelected(threadId)
                        active = false
                    },
                    currentThreadId = currentThreadId,
                    generatingThreadIds = generatingThreadIds,
                    hasMore = false,
                    isLoadingMore = isLoadingSearchPages,
                    onLoadMore = {},
                )
            }
        }

        if (!active) {
            Box(
                modifier = Modifier
                    .weight(1f)
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
                        threads = threads,
                        onItemClick = onThreadSelected,
                        currentThreadId = currentThreadId,
                        generatingThreadIds = generatingThreadIds,
                        hasMore = hasMore,
                        isLoadingMore = isLoadingMore,
                        onLoadMore = onLoadMore,
                    )
                }

                // Bottom solid row
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
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
    onItemClick: (String) -> Unit,
    currentThreadId: String?,
    generatingThreadIds: Set<String>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Trigger load more when near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(state = listState) {
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
                ThreadItem(
                    thread = thread,
                    isSelected = thread.id == currentThreadId,
                    isGenerating = thread.id in generatingThreadIds,
                    onClick = { onItemClick(thread.id) }
                )
            }
        }

        if (isLoadingMore) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ThreadItem(
    thread: AssistantThread,
    isSelected: Boolean,
    isGenerating: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = thread.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = thread.excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        minLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp),
                    )
                }

            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RectangleShape
    )
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
