package com.atlas.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    novels: List<Novel>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onNovelSelect: (String) -> Unit,
    onRemoveFromHistory: (String) -> Unit
) {
    val tabTitles = listOf("Library", "History", "Browse", "Settings")
    val tabIcons = listOf(
        Icons.Default.CollectionsBookmark,
        Icons.Default.History,
        Icons.Default.Explore,
        Icons.Default.Settings
    )

    // Bottom navbar tabs
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabTitles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        label = { Text(title) },
                        icon = { Icon(tabIcons[index], contentDescription = title) })
                }
            }
        }) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> LibraryScreen(novels, onNovelSelect, bottomPadding = innerPadding)
                1 -> HistoryScreen(
                    allNovels = novels,
                    onNovelSelect = onNovelSelect,
                    onRemoveFromHistory = onRemoveFromHistory,
                    bottomPadding = innerPadding
                )

                2 -> CenterText("Browse Screen")
                3 -> CenterText("Settings Screen")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allNovels: List<Novel>, onNovelSelect: (String) -> Unit, bottomPadding: PaddingValues
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Reading", "On Hold", "Completed")
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    // Library categories
    Scaffold(
        topBar = {
            Column {
                if (isSearchActive) {
                    SearchAppBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onCloseClicked = {
                            isSearchActive = false
                            searchQuery = ""
                        })
                } else {
                    TopAppBar(title = { Text("Library") }, actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    })
                }

                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }) {
                    categories.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) })
                    }
                }
            }
        }) { topPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding.calculateTopPadding())
        ) { pageIndex ->
            val currentTabNovels = remember(pageIndex, allNovels) {
                when (pageIndex) {
                    0 -> allNovels.filter { it.category != "None" }
                    1 -> allNovels.filter { it.category == "Reading" }
                    2 -> allNovels.filter { it.category == "On Hold" }
                    3 -> allNovels.filter { it.category == "Completed" }
                    else -> emptyList()
                }
            }

            // Search logic
            val displayList = remember(currentTabNovels, searchQuery) {
                if (searchQuery.isBlank()) {
                    currentTabNovels
                } else {
                    val normalizedQuery = searchQuery.filter { it.isLetterOrDigit() }.lowercase()
                    currentTabNovels.filter { novel ->
                        val normalizedTitle =
                            novel.title.filter { it.isLetterOrDigit() }.lowercase()
                        val matchesFuzzy =
                            normalizedQuery.isNotEmpty() && normalizedTitle.contains(normalizedQuery)
                        val matchesExact = novel.title.contains(searchQuery, ignoreCase = true)
                        matchesFuzzy || matchesExact
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                    bottom = bottomPadding.calculateBottomPadding(), top = 8.dp
                )
            ) {
                if (displayList.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchQuery.isNotEmpty()) "No results found" else "No novels here",
                                color = Color.Gray
                            )
                        }
                    }
                }

                items(displayList) { novel ->
                    LibraryItem(novel = novel, onClick = { onNovelSelect(novel.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    allNovels: List<Novel>,
    onNovelSelect: (String) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    bottomPadding: PaddingValues
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val baseHistoryList = remember(allNovels) {
        allNovels.filter { it.lastReadChapter > 0 }.sortedByDescending { it.lastReadTime }
    }

    // Search logic
    val displayList = remember(baseHistoryList, searchQuery) {
        if (searchQuery.isBlank()) {
            baseHistoryList
        } else {
            val normalizedQuery = searchQuery.filter { it.isLetterOrDigit() }.lowercase()

            baseHistoryList.filter { novel ->
                val normalizedTitle = novel.title.filter { it.isLetterOrDigit() }.lowercase()

                val matchesFuzzy =
                    normalizedQuery.isNotEmpty() && normalizedTitle.contains(normalizedQuery)
                val matchesExact = novel.title.contains(searchQuery, ignoreCase = true)

                matchesFuzzy || matchesExact
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchAppBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onCloseClicked = {
                        isSearchActive = false
                        searchQuery = ""
                    })
            } else {
                TopAppBar(title = { Text("History") }, actions = {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                })
            }
        }) { topPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                top = topPadding.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding()
            )
        ) {
            if (displayList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matches found" else "No history yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(
                items = displayList, key = { novel -> novel.id }) { novel ->
                LibraryItem(
                    novel = novel,
                    subtitle = "Chapter ${novel.lastReadChapter}",
                    onDeleteClick = { onRemoveFromHistory(novel.id) },
                    onClick = { onNovelSelect(novel.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String, onQueryChange: (String) -> Unit, onCloseClicked: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(title = {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
    }, navigationIcon = {
        IconButton(onClick = onCloseClicked) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
        }
    }, actions = {
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear Text")
            }
        }
    })
}

@Composable
fun LibraryItem(
    novel: Novel, subtitle: String? = null, onDeleteClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    val context = LocalContext.current

    // Load bitmap logic
    val coverBitmap = remember(novel.coverAsset) {
        if (novel.coverAsset != null) {
            try {
                context.assets.open(novel.coverAsset).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .height(70.dp)
                .width(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (subtitle != null) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}