package com.atlas.app.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.app.Novel

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
                                    0 -> Icons.Default.Collections
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
                    onNovelAdded = onRefreshLibrary,
                    onNovelSelect = onBrowseNovelSelect,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseClicked: () -> Unit,
    onSearch: () -> Unit = {}
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
            keyboardActions = KeyboardActions(onSearch = {
                onSearch()
                focusManager.clearFocus()
            })
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
            if (!novel.coverAsset.isNullOrEmpty()) {
                AsyncImage(
                    model = novel.coverAsset,
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
                style = MaterialTheme.typography.bodyMedium,
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