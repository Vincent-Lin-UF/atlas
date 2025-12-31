package com.atlas.app.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.app.Novel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    novel: Novel,
    onBack: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    val categories = listOf("Reading", "On Hold", "Finished", "None")
    var sortDescending by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }

    val collapsedGroups = remember { mutableStateMapOf<Int, Boolean>() }

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
                    // Delete button
                    IconButton(onClick = { /* Delete Action */ }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = primaryContentColor
                        )
                    }

                    // Download button
                    IconButton(onClick = { /* Download action */ }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download",
                            tint = primaryContentColor
                        )
                    }

                    // Change novel status dropdown
                    Box {
                        IconButton(onClick = {
                            if (novel.category == "None") {
                                onCategoryChange("Reading")
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
                },
            )
        },
        // Start/Resume FAB
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onChapterClick(if (novel.lastReadChapter > 0) novel.lastReadChapter else 1) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (novel.lastReadChapter > 1) Icons.Default.History else Icons.Default.PlayArrow,
                    contentDescription = if (novel.lastReadChapter > 1) "Resume" else "Start"
                )
            }
        }) { innerPadding ->

        val chapterIndices = (1..novel.chapterCount).toList()
        val sortedIndices = if (sortDescending) chapterIndices.reversed() else chapterIndices
        val chunks = remember(sortedIndices) { sortedIndices.chunked(100) }

        LazyColumn(
            contentPadding = innerPadding, modifier = Modifier.fillMaxSize()
        ) {
            // Novel Header
            item(key = "header_${novel.id}_${novel.title}") {
                NovelHeaderSection(
                    title = novel.title,
                    author = novel.author,
                    source = novel.source,
                    category = novel.category,
                    coverAsset = novel.coverAsset
                )
            }
            // Novel Description
            item(key = "desc_${novel.id}") {
                ExpandableDescription(text = novel.description)
            }

            // Chapter controls
            item(key = "controls") {
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

                    Row {
                        // Collapse/Expand All Button
                        val allCollapsed = collapsedGroups.size == chunks.size
                        IconButton(onClick = {
                            if (allCollapsed) {
                                collapsedGroups.clear()
                            } else {
                                chunks.indices.forEach { collapsedGroups[it] = true } // Collapse everything
                            }
                        }) {
                            Icon(
                                imageVector = if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = "Toggle All Groups",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sort Button
                        IconButton(onClick = {
                            sortDescending = !sortDescending
                        }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Reverse Sort Order",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Chapter Groups Loop
            chunks.forEachIndexed { index, chunk ->
                val startChapter = chunk.first()
                val endChapter = chunk.last()
                val isCollapsed = collapsedGroups.containsKey(index)

                // Group Header
                item(key = "group_header_$index") {
                    ChapterGroupHeader(
                        start = startChapter,
                        end = endChapter,
                        isExpanded = !isCollapsed,
                        onToggle = {
                            if (isCollapsed) {
                                collapsedGroups.remove(index)
                            } else {
                                collapsedGroups[index] = true
                            }
                        }
                    )
                }

                if (!isCollapsed) {
                    items(
                        items = chunk,
                        key = { chapterNum -> "chapter_${novel.id}_$chapterNum" }
                    ) { chapterNum ->
                        val displayTitle = novel.chapterTitles.getOrNull(chapterNum - 1) ?: "Chapter $chapterNum"
                        ChapterListItem(
                            name = displayTitle,
                            chapterNum = chapterNum,
                            onClick = { onChapterClick(chapterNum) }
                        )
                    }
                }
            }

            item(key = "footer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ChapterGroupHeader(
    start: Int,
    end: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Chapters $start - $end",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun NovelHeaderSection(
    title: String, author: String?, source: String, category: String, coverAsset: String?
) {
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
            if (coverAsset != null) {
                AsyncImage(
                    model = coverAsset,
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = author ?: "Unknown Author",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ExpandableDescription(text: String?) {
    var isExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val fadeBrush = remember(isExpanded) {
        Brush.verticalGradient(
            colors = listOf(surfaceColor.copy(alpha = 0f), surfaceColor),
            startY = 0f
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (isOverflowing) isExpanded = !isExpanded }
            )
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Text(
                text = text ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        isOverflowing = true
                    }
                }
            )

            if (isOverflowing && !isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(fadeBrush),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Read More",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isExpanded && isOverflowing) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Collapse",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ChapterListItem(name: String, chapterNum: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailingContent = {
            Text(
                text = chapterNum.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    )
}