package com.atlas.app.screens.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.atlas.app.Novel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelAdded: () -> Unit,
    onNovelSelect: (Novel) -> Unit
) {
    val scope = rememberCoroutineScope()
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
            onNovelAdded = onNovelAdded,
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
    onNovelAdded: () -> Unit,
    onNovelSelect: (Novel) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var results by remember { mutableStateOf(emptyList<Novel>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }
    var currentlyAddingUrl by remember { mutableStateOf<String?>(null) }

    // Run search whenever query or source changes
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

    // Pagination
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layout.totalItemsCount - 2
        }.collect { atBottom ->
            if (
                atBottom &&
                !isLoadingMore &&
                canLoadMore &&
                results.isNotEmpty()
            ) {
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
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(results) { novel ->
            val isAdding = currentlyAddingUrl == novel.url

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNovelSelect(novel) } // Trigger View logic
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail Box
                Box(
                    modifier = Modifier
                        .height(70.dp)
                        .width(50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = novel.coverAsset,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = novel.chapterCount?.let { "$it Chapters" } ?: "Unknown Chapters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
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