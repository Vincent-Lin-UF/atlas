package com.atlas.app.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.app.Novel

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