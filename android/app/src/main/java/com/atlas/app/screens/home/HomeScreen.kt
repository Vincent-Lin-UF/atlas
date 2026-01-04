package com.atlas.app.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.app.data.Novel

@Composable
fun HomeScreen(
    novels: List<Novel>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onNovelSelect: (String) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onRefreshLibrary: () -> Unit,
    onBrowseNovelSelect: (Novel) -> Unit,
) {
    val navigationItems = listOf("Library", "History", "Browse")
    val bottomPadding = PaddingValues(bottom = 80.dp)

    Scaffold(
        bottomBar = {
            NavigationBar {
                navigationItems.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabChange(index) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.CollectionsBookmark
                                    1 -> Icons.Default.History
                                    else -> Icons.Default.Explore
                                },
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> LibraryScreen(
                    allNovels = novels,
                    onNovelSelect = onNovelSelect,
                    bottomPadding = bottomPadding
                )
                1 -> HistoryScreen(
                    allNovels = novels,
                    onNovelSelect = onNovelSelect,
                    onRemoveFromHistory = onRemoveFromHistory,
                    bottomPadding = bottomPadding
                )
                2 -> BrowseScreen(
                    onNovelSelect = onBrowseNovelSelect,
                )
            }
        }
    }
}