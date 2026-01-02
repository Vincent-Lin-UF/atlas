package com.atlas.app.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.atlas.app.data.Novel
import com.atlas.app.components.SearchAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelSelect: (Novel) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var textInput by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val sourceNames = remember { LibraryManager.getSourceNames() }

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
                        onSearch = {
                            activeQuery = textInput
                        }
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
                                selectedSourceIndex = index
                                if (textInput.isNotBlank()) {
                                    activeQuery = textInput
                                }
                                focusManager.clearFocus()
                            },
                            text = { Text(name) }
                        )
                    }
                }
            }
        }
    ) { topPadding ->
        BrowseContent(
            modifier = Modifier.padding(top = topPadding.calculateTopPadding()),
            listState = listState,
            query = activeQuery,
            selectedSourceIndex = selectedSourceIndex,
            onNovelSelect = onNovelSelect
        )
    }
}

@Composable
private fun BrowseContent(
    modifier: Modifier,
    listState: LazyListState,
    query: String,
    selectedSourceIndex: Int,
    onNovelSelect: (Novel) -> Unit
) {
    var results by remember { mutableStateOf(emptyList<Novel>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }

    LaunchedEffect(query, selectedSourceIndex) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }

        isLoading = true
        canLoadMore = true
        try {
            results = LibraryManager.search(query, selectedSourceIndex)
            listState.scrollToItem(0)
            if (results.isEmpty()) canLoadMore = false
        } finally {
            isLoading = false
        }
    }

    // Pagination Logic
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layout.totalItemsCount - 2
        }.collect { atBottom ->
            if (atBottom && !isLoadingMore && canLoadMore && results.isNotEmpty()) {
                isLoadingMore = true
                try {
                    val more = LibraryManager.loadNextPage()
                    if (more.isNotEmpty()) {
                        results = results + more
                    } else {
                        canLoadMore = false
                    }
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 80.dp, top = 12.dp)
    ) {
        items(results) { novel ->
            LibraryItem(
                novel = novel,
                subtitle = "${novel.chapterCount} Chapters",
                onDeleteClick = null,
                onClick = { onNovelSelect(novel) }
            )
        }

        if (isLoadingMore) {
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
    }
}