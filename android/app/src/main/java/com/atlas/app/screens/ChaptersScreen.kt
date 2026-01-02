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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.app.data.AppDatabase
import com.atlas.app.data.Chapter
import com.atlas.app.data.Novel
import com.atlas.app.screens.home.LibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    novelId: String,
    initialNovel: Novel?,
    onBack: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    val observedNovel by db.novelDao().getNovelFlow(novelId).collectAsState(initial = initialNovel)
    val currentNovel = observedNovel ?: initialNovel

    if (currentNovel == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val categories = listOf("Reading", "On Hold", "Finished", "None")
    var sortDescending by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var displayedChapters by remember(currentNovel.id) { mutableStateOf<List<Chapter>>(emptyList()) }
    val collapsedGroups = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(currentNovel.id, currentNovel.category) {
        withContext(Dispatchers.IO) {
            val dbChapters = db.chapterDao().getChaptersForNovel(currentNovel.id)
            if (dbChapters.isNotEmpty()) {
                displayedChapters = dbChapters
            } else if (currentNovel.category == "None") {
                val webChapters = LibraryManager.fetchChaptersForPreview(currentNovel)
                displayedChapters = webChapters
            } else {
                LibraryManager.syncChapters(context, currentNovel)
                displayedChapters = db.chapterDao().getChaptersForNovel(currentNovel.id)
            }
        }
        isLoading = false
    }

    val primaryContainerColor = MaterialTheme.colorScheme.surface
    val primaryContentColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    IconButton(onClick = { /* Delete Action */ }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = primaryContentColor)
                    }
                    IconButton(onClick = { /* Download action */ }) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = primaryContentColor)
                    }
                    Box {
                        IconButton(onClick = {
                            if (currentNovel.category == "None") {
                                onCategoryChange("Reading")
                            } else {
                                showLibraryMenu = true
                            }
                        }) {
                            Icon(
                                imageVector = if (currentNovel.category != "None") Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Library Status",
                                tint = if (currentNovel.category != "None") MaterialTheme.colorScheme.primary else primaryContentColor
                            )
                        }

                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { showLibraryMenu = false }
                        ) {
                            categories.forEach { category ->
                                val isSelected = category == currentNovel.category
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                            } else {
                                                Spacer(Modifier.width(24.dp))
                                            }
                                            Text(if (category == "None") "Remove from Library" else category)
                                        }
                                    },
                                    onClick = {
                                        onCategoryChange(category)
                                        showLibraryMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (displayedChapters.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val target = if (currentNovel.lastReadChapter > 0) currentNovel.lastReadChapter else 1
                        onChapterClick(target)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = if (currentNovel.lastReadChapter > 1) Icons.Default.History else Icons.Default.PlayArrow,
                        contentDescription = if (currentNovel.lastReadChapter > 1) "Resume" else "Start"
                    )
                }
            }
        }
    ) { innerPadding ->

        val sortedChapters = if (sortDescending) displayedChapters.reversed() else displayedChapters
        val chunks = remember(sortedChapters) { sortedChapters.chunked(100) }

        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                NovelHeaderSection(
                    title = currentNovel.title,
                    author = currentNovel.author,
                    source = currentNovel.source,
                    coverAsset = currentNovel.coverAsset
                )
            }

            item(key = "desc") {
                ExpandableDescription(text = currentNovel.description)
            }

            item(key = "controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = when {
                        isLoading -> ""
                        displayedChapters.isNotEmpty() -> "${displayedChapters.size} chapters"
                        else -> "No chapters"
                    }
                    Text(text = countText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row {
                        if (chunks.size > 1) {
                            val allCollapsed = collapsedGroups.size == chunks.size

                            IconButton(onClick = {
                                if (allCollapsed) {
                                    collapsedGroups.clear()
                                } else {
                                    chunks.indices.forEach { collapsedGroups[it] = true }
                                }
                            }) {
                                Icon(
                                    imageVector = if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                    contentDescription = "Toggle All Groups",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { sortDescending = !sortDescending }) {
                            Icon(Icons.Default.SwapVert, "Reverse Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }

            chunks.forEachIndexed { index, chunk ->
                val startChapter = chunk.first().index
                val endChapter = chunk.last().index
                val isCollapsed = collapsedGroups.containsKey(index)

                if (chunks.size > 1) {
                    item(key = "group_header_$index") {
                        ChapterGroupHeader(
                            start = startChapter,
                            end = endChapter,
                            isExpanded = !isCollapsed,
                            onToggle = { if (isCollapsed) collapsedGroups.remove(index) else collapsedGroups[index] = true }
                        )
                    }
                }

                if (!isCollapsed) {
                    items(items = chunk, key = { ch -> "${currentNovel.id}_${ch.index}" }) { chapter ->
                        ChapterListItem(
                            name = chapter.name,
                            chapterNum = chapter.index,
                            onClick = { onChapterClick(chapter.index) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ChapterGroupHeader(start: Int, end: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
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
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun NovelHeaderSection(title: String, author: String?, source: String, coverAsset: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier.width(100.dp).height(150.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!coverAsset.isNullOrEmpty()) {
                AsyncImage(model = coverAsset, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Book, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(author ?: "Unknown Author", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                text = text ?: "No description.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { textLayoutResult ->
                    if (!isExpanded && textLayoutResult.hasVisualOverflow) {
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
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text(chapterNum.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)) }
    )
}