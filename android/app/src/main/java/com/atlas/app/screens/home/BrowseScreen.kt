package com.atlas.app.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.atlas.app.components.LibraryItem
import com.atlas.app.components.SearchAppBar
import com.atlas.app.data.Novel
import kotlinx.coroutines.launch

data class BrowseTabState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelSelect: (Novel) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val sourceNames = remember { LibraryManager.getSourceNames() }
    val pagerState = rememberPagerState(pageCount = { sourceNames.size })

    val tabStates = remember { mutableStateMapOf<Int, BrowseTabState>() }

    // Initialize states if empty
    LaunchedEffect(Unit) {
        sourceNames.indices.forEach { index ->
            if (!tabStates.containsKey(index)) {
                tabStates[index] = BrowseTabState()
            }
        }
    }

    var textInput by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val selectedSourceIndex = pagerState.currentPage

    fun updateTab(index: Int, update: BrowseTabState.() -> BrowseTabState) {
        tabStates[index] = tabStates[index]?.update() ?: BrowseTabState().update()
    }

    // Search Logic
    fun performSearch() {
        if (textInput.isBlank()) return
        activeQuery = textInput
        focusManager.clearFocus()

        scope.launch {
            val currentIndex = pagerState.currentPage

            // Set loading for current tab
            updateTab(currentIndex) { copy(isLoading = true, novels = emptyList()) }

            try {
                val results = LibraryManager.search(activeQuery, currentIndex)

                updateTab(currentIndex) {
                    copy(isLoading = false, novels = results, canLoadMore = results.isNotEmpty())
                }

                // If searching "All", populate child tabs
                if (currentIndex == 0) {
                    val grouped = results.groupBy { it.source }
                    for (i in 1 until sourceNames.size) {
                        val sourceName = sourceNames[i]
                        val specificResults = grouped[sourceName] ?: emptyList()

                        updateTab(i) {
                            copy(
                                novels = specificResults,
                                isLoading = false,
                                canLoadMore = specificResults.isNotEmpty()
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                updateTab(currentIndex) { copy(isLoading = false) }
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isSearchActive) {
                    SearchAppBar(
                        query = textInput,
                        onQueryChange = { textInput = it },
                        onCloseClicked = {
                            isSearchActive = false
                            textInput = ""
                            activeQuery = ""
                        },
                        onSearch = { performSearch() }
                    )
                } else {
                    TopAppBar(
                        title = { Text("Browse") },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    )
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedSourceIndex,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSourceIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    sourceNames.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedSourceIndex == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(name) }
                        )
                    }
                }
            }
        }
    ) { topPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(top = topPadding.calculateTopPadding())
        ) { pageIndex ->
            val listState = rememberLazyListState()
            val currentTabState = tabStates[pageIndex] ?: BrowseTabState()

            BrowseTabContent(
                listState = listState,
                state = currentTabState,
                onLoadMore = {
                    if (!currentTabState.isLoadingMore && currentTabState.canLoadMore) {
                        scope.launch {
                            updateTab(pageIndex) { copy(isLoadingMore = true) }
                            try {
                                val more = LibraryManager.loadNextPage(pageIndex)
                                updateTab(pageIndex) {
                                    copy(
                                        isLoadingMore = false,
                                        novels = novels + more,
                                        canLoadMore = more.isNotEmpty()
                                    )
                                }
                            } catch (_: Exception) {
                                updateTab(pageIndex) { copy(isLoadingMore = false) }
                            }
                        }
                    }
                },
                onNovelSelect = onNovelSelect
            )
        }
    }
}

@Composable
private fun BrowseTabContent(
    listState: LazyListState,
    state: BrowseTabState,
    onLoadMore: () -> Unit,
    onNovelSelect: (Novel) -> Unit
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Pagination Logic: Trigger onLoadMore when near bottom
    LaunchedEffect(listState, state.novels.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (layout.totalItemsCount - 2).coerceAtLeast(0)
        }.collect { atBottom ->
            if (atBottom && state.novels.isNotEmpty()) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 80.dp, top = 12.dp)
    ) {
        items(state.novels) { novel ->
            LibraryItem(
                novel = novel,
                subtitle = "${novel.chapterCount} Chapters • ${novel.source}",
                onDeleteClick = null,
                onClick = { onNovelSelect(novel) }
            )
        }

        if (state.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        if (state.novels.isEmpty() && !state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}