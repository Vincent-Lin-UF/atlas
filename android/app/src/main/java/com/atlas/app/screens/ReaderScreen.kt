package com.atlas.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.zIndex
import com.atlas.app.data.ChapterData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // --- TEXT SETTINGS ---
    val fontSize = 14.sp
    val lineHeight = 20.sp
    val paragraphPadding = 8.dp
    // ---------------------

    var isTopBarVisible by remember { mutableStateOf(false) }
    var isPillBarVisible by remember { mutableStateOf(true) }

    var shouldShowSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        shouldShowSpinner = true
    }

    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }

    val uniqueChapters = chapters.distinctBy { it.id }

    val currentChaptersState = rememberUpdatedState(uniqueChapters)
    val currentSaveLambda = rememberUpdatedState(onSaveProgress)

    var currentChapterId by remember { mutableIntStateOf(initialChapterId) }
    var currentOffset by remember { mutableIntStateOf(initialScroll) }

    val currentVisibleChapter by remember(uniqueChapters) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            if (index in uniqueChapters.indices) uniqueChapters[index] else null
        }
    }

    // Restore Position
    LaunchedEffect(uniqueChapters.size) {
        if (!initialScrollDone && uniqueChapters.isNotEmpty()) {
            val startIndex = uniqueChapters.indexOfFirst { it.id == initialChapterId }
            if (startIndex >= 0) {
                listState.scrollToItem(startIndex, initialScroll)
                initialScrollDone = true
            }
        }
    }

    // Infinite Scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collectLatest { layoutInfo ->
            if (layoutInfo.totalItemsCount == 0) return@collectLatest
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            if (firstVisibleIndex == 0) {
                val firstOffset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                if (firstOffset > -50) onLoadPreviousChapter()
            }
            if (lastVisibleIndex >= layoutInfo.totalItemsCount - 1) {
                onLoadNextChapter()
            }
        }
    }

    // Tracking Logic
    LaunchedEffect(listState) {
        snapshotFlow {
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collectLatest { (index, offset) ->
            val chaptersList = currentChaptersState.value
            if (index in chaptersList.indices) {
                val chapter = chaptersList[index]
                currentChapterId = chapter.id
                currentOffset = offset
            }
        }
    }

    // Saving Logic
    LaunchedEffect(currentChapterId, currentOffset) {
        delay(500)
        currentSaveLambda.value(currentChapterId, currentOffset)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentSaveLambda.value(currentChapterId, currentOffset)
        }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                BottomAppBar(
                    modifier = Modifier.height(52.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                        Spacer(modifier = Modifier.width(20.dp))

                        if (currentVisibleChapter != null) {
                            Text(text = "${currentVisibleChapter!!.id}/$totalChapters", fontSize = 10.sp)
                        }
                    }
                }
            }
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        isPillBarVisible = !isPillBarVisible
                    },
                    onLongClick = {
                        isTopBarVisible = !isTopBarVisible
                    }
                )
        ) {

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (uniqueChapters.isEmpty()) {
                    if (shouldShowSpinner) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + statusBarHeight + 10.dp,
                            bottom = innerPadding.calculateBottomPadding()
                        )
                    ) {
                        itemsIndexed(
                            items = uniqueChapters,
                            key = { _, item -> item.id }
                        ) { index, chapter ->
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .padding(vertical = paragraphPadding)
                            ) {
                                val paragraphs = remember(chapter.body) {
                                    chapter.body.split("\n").filter { it.isNotBlank() }
                                }

                                paragraphs.forEachIndexed { i, paragraph ->
                                    val bottomPadding = if (i == paragraphs.lastIndex) 0.dp else paragraphPadding

                                    Text(
                                        text = paragraph.trim(),
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(bottom = bottomPadding)
                                    )
                                }

                                if (index < uniqueChapters.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .padding(top = 20.dp, bottom = 4.dp)
                                            .fillMaxWidth(),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.fillParentMaxHeight(0.975f))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(1f)
            )

            AnimatedVisibility(
                visible = isTopBarVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
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
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { /* Menu Action */ }) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            AnimatedVisibility(
                visible = isPillBarVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 20.dp)
                    .zIndex(2f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(52.dp)
                ) {
                    // Swaps content based on Top Bar (Settings mode vs Audio mode)
                    Crossfade(
                        targetState = isTopBarVisible,
                        label = "ControlsCrossfade"
                    ) { showMenuTools ->

                        Row(
                            modifier = Modifier.padding(horizontal = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            if (!showMenuTools) {
                                // Audio Controls
                                IconButton(onClick = { }) { Icon(Icons.Default.FastRewind, contentDescription = "Speak Last Chapter") }
                                IconButton(onClick = { }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Speak Last Paragraph") }
                                IconButton(onClick = { }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause") }
                                IconButton(onClick = { }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
                                IconButton(onClick = { }) { Icon(Icons.Default.SkipNext, contentDescription = "Speak Next Paragraph") }
                                IconButton(onClick = { }) { Icon(Icons.Default.FastForward, contentDescription = "Speak Next Chapter") }
                            } else {
                                // Settings Controls
                                IconButton(onClick = { }) { Icon(Icons.Default.FastRewind, contentDescription = "Last Chapter") }
                                IconButton(onClick = { }) { Icon(Icons.Default.FormatPaint, contentDescription = "Change Theme") }
                                IconButton(onClick = { }) { Icon(Icons.Default.Timer, contentDescription = "Speech Timer") }
                                IconButton(onClick = { }) { Icon(Icons.Default.FindReplace, contentDescription = "Replacement") }
                                IconButton(onClick = { }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                                IconButton(onClick = { }) { Icon(Icons.Default.FastForward, contentDescription = "Next Chapter") }
                            }
                        }
                    }
                }
            }
        }
    }
}