package com.atlas.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    novelTitle: String,
    chapters: List<ChapterData>,
    totalChapters: Int,
    initialChapterId: Int,
    initialScroll: Int,
    onBack: () -> Unit,
    onLoadNextChapter: () -> Unit,
    onLoadPreviousChapter: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit
) {
    var isTopBarVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Identify currently visible chapter
    val currentVisibleChapter by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            if (index in chapters.indices) chapters[index] else null
        }
    }

    // Restore scroll position
    LaunchedEffect(Unit) {
        val startIndex = chapters.indexOfFirst { it.id == initialChapterId }
        if (startIndex >= 0) {
            listState.scrollToItem(startIndex, initialScroll)
        }
    }

    // Infinite Scroll Logic
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collectLatest { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@collectLatest

                val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                // Load previous chapter
                if (firstVisibleIndex == 0) {
                    val firstOffset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                    if (firstOffset > -50) {
                        onLoadPreviousChapter()
                    }
                }

                // Load next chapter
                if (lastVisibleIndex >= layoutInfo.totalItemsCount - 1) {
                    onLoadNextChapter()
                }
            }
    }

    // Auto-save progress
    LaunchedEffect(listState) {
        snapshotFlow {
            Pair(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset
            )
        }.collectLatest { (index, offset) ->
                delay(500)
                if (index in chapters.indices) {
                    val visibleChapter = chapters[index]
                    onSaveProgress(visibleChapter.id, offset)
                }
            }
    }

    // Save on exit
    DisposableEffect(Unit) {
        onDispose {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (index in chapters.indices) {
                onSaveProgress(chapters[index].id, offset)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0.dp), bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(48.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentVisibleChapter?.title ?: "",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${currentVisibleChapter?.id ?: 1} / $totalChapters",
                        fontSize = 14.sp
                    )
                }
            }
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) {
                    isTopBarVisible = !isTopBarVisible
                }) {
            Surface(
                modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
                ) {
                    itemsIndexed(
                        items = chapters, key = { _, item -> item.id }) { index, chapter ->
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(
                                    bottom = 10.dp,
                                    top = 10.dp
                                )
                        ) {

                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                            }

                            Text(
                                text = chapter.body,
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.fillParentMaxHeight(0.9f))
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background) // Or use .surface
                    .align(Alignment.TopCenter)
            )

            AnimatedVisibility(
                visible = isTopBarVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                CenterAlignedTopAppBar(
                    title = { Text(novelTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Menu Action */ }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 20.dp)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { /* Action here */ }) {
                        Icon(
                            Icons.Default.FastRewind, contentDescription = null
                        )
                    }
                    IconButton(onClick = { /* Action here */ }) {
                        Icon(
                            Icons.Default.SkipPrevious, contentDescription = null
                        )
                    }

                    IconButton(onClick = { /* Logic only here */ }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    }
                    IconButton(onClick = { /* Logic only here */ }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                    }

                    IconButton(onClick = { /* Action here */ }) {
                        Icon(
                            Icons.Default.SkipNext, contentDescription = null
                        )
                    }
                    IconButton(onClick = { /* Action here */ }) {
                        Icon(
                            Icons.Default.FastForward, contentDescription = null
                        )
                    }
                }
            }
        }
    }
}