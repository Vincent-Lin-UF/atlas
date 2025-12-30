package com.atlas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelScreen(
    novel: Novel,
    onBack: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    val categories = listOf("All", "Reading", "On Hold", "Completed", "None")
    var sortDescending by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }

    val chapterList = remember(novel.chapterCount) {
        (1..novel.chapterCount).toList()
    }

    val primaryContainerColor = MaterialTheme.colorScheme.surface
    val primaryContentColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        modifier = Modifier.fillMaxSize(), topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = primaryContainerColor,
                    titleContentColor = primaryContentColor,
                    navigationIconContentColor = primaryContentColor,
                    actionIconContentColor = primaryContentColor,
                    scrolledContainerColor = primaryContainerColor
                ),
                actions = {
                    // Change novel status dropdown
                    Box {
                        IconButton(onClick = {
                            if (novel.category == "None") {
                                onCategoryChange("All")
                            } else {
                                showLibraryMenu = true
                            }
                        }) {
                            Icon(
                                imageVector = if (novel.category != "None") Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Library Status",
                                tint = if (novel.category != "None") MaterialTheme.colorScheme.primary else primaryContentColor
                            )
                        }

                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { showLibraryMenu = false }) {
                            categories.forEach { category ->
                                val isSelected = category == novel.category
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Spacer(modifier = Modifier.width(24.dp))
                                        }
                                        Text(if (category == "None") "Remove from Library" else category)
                                    }
                                }, onClick = {
                                    onCategoryChange(category)
                                    showLibraryMenu = false
                                })
                            }
                        }
                    }

                    // Download button
                    IconButton(onClick = { /* Download action */ }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download",
                            tint = primaryContentColor
                        )
                    }

                    // Delete button
                    IconButton(onClick = { /* Delete Action */ }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = primaryContentColor
                        )
                    }
                },
            )
        },
        // Start/Resume FAB
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onChapterClick(1) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start")
            }
        }) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding, modifier = Modifier.fillMaxSize()
        ) {
            // Novel Header
            item {
                NovelHeaderSection(
                    title = novel.title,
                    author = novel.author,
                    category = novel.category,
                    coverAsset = novel.coverAsset // Passed here
                )
            }
            // Novel Description
            item {
                ExpandableDescription(text = novel.description)
            }
            // Chapter sort controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${novel.chapterCount} chapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { sortDescending = !sortDescending }) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Reverse Sort Order",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Chapter List
            val chaptersToShow = if (sortDescending) chapterList.reversed() else chapterList
            items(chaptersToShow) { chapterNum ->
                ChapterListItem(
                    name = "Chapter $chapterNum", onClick = { onChapterClick(chapterNum) })
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun NovelHeaderSection(
    title: String, author: String, category: String, coverAsset: String?
) {
    val context = LocalContext.current

    // Load bitmap from assets
    val coverBitmap = remember(coverAsset) {
        if (coverAsset != null) {
            try {
                context.assets.open(coverAsset).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = "Cover Image",
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
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExpandableDescription(text: String) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChapterListItem(name: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(name) }, modifier = Modifier.clickable { onClick() })
}