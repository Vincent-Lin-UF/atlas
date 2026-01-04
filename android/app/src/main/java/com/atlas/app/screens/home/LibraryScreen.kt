package com.atlas.app.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atlas.app.components.LibraryItem
import com.atlas.app.components.SearchAppBar
import com.atlas.app.data.Novel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allNovels: List<Novel>, onNovelSelect: (String) -> Unit, bottomPadding: PaddingValues
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Reading", "On Hold", "Finished")
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        // Library categories
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
                    3 -> allNovels.filter { it.category == "Finished" }
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

            // Library content
            LazyColumn(
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                    bottom = bottomPadding.calculateBottomPadding(), top = 12.dp
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