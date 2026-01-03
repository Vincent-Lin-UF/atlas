package com.atlas.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.atlas.app.data.AppDatabase
import com.atlas.app.data.ChapterData
import com.atlas.app.data.Novel
import com.atlas.app.screens.ChaptersScreen
import com.atlas.app.screens.ReaderScreen
import com.atlas.app.screens.home.HomeScreen
import com.atlas.app.screens.home.LibraryManager
import com.atlas.app.ui.theme.AtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force portrait mode
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Initialize db
        val db = AppDatabase.getDatabase(applicationContext)
        val novelDao = db.novelDao()
        val chapterDao = db.chapterDao()

        // Initialize preferences
        val sharedPref = getSharedPreferences("atlas_prefs", MODE_PRIVATE)
        val lastScreenType = sharedPref.getString("last_screen_type", "home")
        val lastActiveNovelId = sharedPref.getString("last_active_novel_id", "")

        setContent {
            AtlasTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                // Get library and automatically update when DB changes
                val libraryNovels by novelDao.getLibraryNovels()
                    .collectAsState(initial = emptyList())

                var currentHomeTab by rememberSaveable { mutableIntStateOf(0) }

                // Restore last session & clear unused preview novels
                LaunchedEffect(Unit) {
                    if (lastScreenType == "reader" && !lastActiveNovelId.isNullOrEmpty()) {
                        // Retrieve last novel info
                        val lastNovel = withContext(Dispatchers.IO) {
                            novelDao.getNovel(lastActiveNovelId)
                        }

                        // Restore novel reader if applicable
                        if (lastNovel != null) {
                            navController.navigate(
                                "details/$lastActiveNovelId"
                            ) {
                                launchSingleTop = true
                            }
                            navController.navigate(
                                "reader/$lastActiveNovelId/${lastNovel.lastReadChapter}"
                            ) {
                                launchSingleTop = true
                            }
                        }
                    }

                    novelDao.clearUnusedPreviews()
                }

                // Update last screen in prefs
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val route = entry.destination.route
                        val args = entry.arguments
                        when (route) {
                            "home" -> {
                                sharedPref.edit { putString("last_screen_type", "home") }
                            }

                            "reader/{novelId}/{chapterIndex}" -> {
                                val currentId = args?.getString("novelId") ?: ""
                                sharedPref.edit {
                                    putString("last_screen_type", "reader")
                                    putString("last_active_novel_id", currentId)
                                }
                            }
                        }
                    }
                }

                // Helper to save reading progress in DB
                fun saveReadProgress(novelId: String, chapterIndex: Int, scrollPos: Int) {
                    scope.launch(Dispatchers.IO) {
                        val novel = novelDao.getNovel(novelId)
                        if (novel != null) {
                            val updated = novel.copy(
                                lastReadChapter = chapterIndex,
                                lastReadPosition = scrollPos,
                                lastReadTime = System.currentTimeMillis()
                            )
                            novelDao.updateNovel(updated)
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    // --- HOME SCREEN ---
                    composable("home") {
                        HomeScreen(
                            novels = libraryNovels,
                            selectedTab = currentHomeTab,
                            onTabChange = { currentHomeTab = it },
                            onNovelSelect = { novelId ->
                                navController.navigate("details/$novelId")
                            },
                            onRemoveFromHistory = { novelId ->
                                scope.launch(Dispatchers.IO) {
                                    val novel = novelDao.getNovel(novelId) ?: return@launch

                                    // Delete novels from db entirely if not in library
                                    if (novel.category == "None") {
                                        novelDao.deleteNovel(novelId)
                                    } else { // Delete novel progress and chapters if in library
                                        val resetNovel = novel.copy(
                                            lastReadChapter = 1,
                                            lastReadPosition = 0,
                                            lastReadTime = 0L
                                        )
                                        novelDao.updateNovel(resetNovel)
                                        chapterDao.deleteChaptersForNovel(novelId)
                                    }
                                }
                            },
                            onRefreshLibrary = {},
                            onBrowseNovelSelect = { novelFromSearch ->
                                val generatedId =
                                    (novelFromSearch.title + novelFromSearch.source).hashCode()
                                        .toString()

                                scope.launch {
                                    val existing =
                                        withContext(Dispatchers.IO) { novelDao.getNovel(generatedId) }

                                    // Load novel from DB if exists
                                    if (existing != null) {
                                        navController.navigate("details/$generatedId")
                                    } else { // Create novel if not in DB
                                        val previewNovel = novelFromSearch.copy(
                                            id = generatedId,
                                            category = "None"
                                        )
                                        withContext(Dispatchers.IO) {
                                            novelDao.insertNovel(previewNovel)
                                        }
                                        // Fetch details novel details and update DB
                                        launch(Dispatchers.IO) {
                                            try {
                                                val detailed =
                                                    LibraryManager.getNovelDetails(previewNovel)
                                                val toUpdate = detailed.copy(
                                                    id = generatedId,
                                                    category = "None"
                                                )
                                                novelDao.updateNovel(toUpdate)
                                            } catch (_: Exception) {
                                            }
                                        }
                                        navController.navigate("details/$generatedId")
                                    }
                                }
                            }
                        )
                    }

                    // --- DETAILS SCREEN ---
                    composable("details/{novelId}") { backStackEntry ->
                        // Fetch novelId
                        val novelId = backStackEntry.arguments?.getString("novelId") ?: ""

                        // Fetch novel details
                        val novelState = produceState<Novel?>(initialValue = null, key1 = novelId) {
                            value = withContext(Dispatchers.IO) { novelDao.getNovel(novelId) }
                        }

                        if (novelId.isNotEmpty()) {
                            ChaptersScreen(
                                novelId = novelId,
                                initialNovel = novelState.value, // Can be null initially
                                onBack = { navController.popBackStack() },
                                onChapterClick = { chapterId ->
                                    navController.navigate("reader/$novelId/$chapterId")
                                },
                                onCategoryChange = { newCategory ->
                                    scope.launch(Dispatchers.IO + NonCancellable) {
                                        val current = novelDao.getNovel(novelId) ?: return@launch
                                        val updated = current.copy(category = newCategory)
                                        novelDao.updateNovel(updated)
                                        if (newCategory != "None") {
                                            LibraryManager.addToLibrary(applicationContext, updated)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // --- READER SCREEN ---
                    composable(
                        route = "reader/{novelId}/{chapterIndex}",
                        arguments = listOf(
                            navArgument("novelId") { type = NavType.StringType },
                            navArgument("chapterIndex") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        // Fetch novelId and chapter to show
                        val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                        val startChapterIndex =
                            backStackEntry.arguments?.getInt("chapterIndex") ?: 1

                        var currentNovel by remember { mutableStateOf<Novel?>(null) }
                        val loadedChapters =
                            remember(startChapterIndex) { mutableStateListOf<ChapterData>() }
                        val fetchingIds = remember { mutableStateListOf<Int>() }

                        LaunchedEffect(novelId) {
                            currentNovel =
                                withContext(Dispatchers.IO) { novelDao.getNovel(novelId) }
                        }

                        // TODO: rewrite this function
                        suspend fun fetchChapterData(
                            index: Int,
                            forceRefresh: Boolean = false
                        ): ChapterData =
                            withContext(Dispatchers.IO + NonCancellable) { // TODO: find a way to not use NonCancellable
                                var chapter = chapterDao.getChapter(novelId, index)

                                // Fetch chapter from DB if already downloaded
                                if (chapter != null && !chapter.body.isNullOrEmpty() && !forceRefresh) {
                                    return@withContext ChapterData(
                                        chapter.index,
                                        chapter.name,
                                        chapter.body
                                    )
                                }

                                var retries = 0
                                while (currentNovel == null && retries < 10) {
                                    kotlinx.coroutines.delay(100)
                                    retries++
                                }

                                val novel = currentNovel ?: return@withContext ChapterData(
                                    index,
                                    "Loading...",
                                    ""
                                )

                                // If the chapter entry doesn't exist at all, sync the list from the web.
                                if (chapter == null) {
                                    try {
                                        LibraryManager.syncChapters(applicationContext, novel)
                                        chapter = chapterDao.getChapter(novelId, index)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                // If we have the entry but no body, download the content.
                                if (chapter != null) {
                                    try {
                                        val updatedChapter = LibraryManager.getChapterContent(
                                            applicationContext,
                                            chapter,
                                            forceRefresh = forceRefresh
                                        )
                                        return@withContext ChapterData(
                                            updatedChapter.index,
                                            updatedChapter.name,
                                            updatedChapter.body ?: ""
                                        )
                                    } catch (_: Exception) {
                                        return@withContext ChapterData(
                                            index,
                                            "",
                                            ""
                                        )
                                    }
                                }

                                return@withContext ChapterData(index, "", "")
                            }

                        LaunchedEffect(startChapterIndex) {
                            if (loadedChapters.none { it.id == startChapterIndex }) {
                                if (!fetchingIds.contains(startChapterIndex)) {
                                    fetchingIds.add(startChapterIndex)

                                    val data = fetchChapterData(startChapterIndex)

                                    loadedChapters.clear()
                                    loadedChapters.add(data)
                                    fetchingIds.remove(startChapterIndex)

                                    // Prefetch next chapter
                                    val total = currentNovel?.chapterCount ?: Int.MAX_VALUE
                                    if (startChapterIndex < total) {
                                        scope.launch(Dispatchers.IO) {
                                            fetchChapterData(
                                                startChapterIndex + 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val initialScroll =
                            if (currentNovel?.lastReadChapter == startChapterIndex) {
                                currentNovel?.lastReadPosition ?: 0
                            } else {
                                0
                            }

                        ReaderScreen(
                            novelTitle = currentNovel?.title ?: "",
                            chapters = loadedChapters,
                            totalChapters = currentNovel?.chapterCount ?: 0,
                            initialChapterId = startChapterIndex,
                            initialScroll = initialScroll,
                            onBack = { navController.popBackStack() },
                            onLoadNextChapter = {
                                val lastLoaded = loadedChapters.lastOrNull()?.id ?: 0
                                val nextId = lastLoaded + 1
                                val total = currentNovel?.chapterCount ?: Int.MAX_VALUE

                                if (nextId <= total && !fetchingIds.contains(nextId)) {
                                    scope.launch {
                                        fetchingIds.add(nextId)
                                        val data = fetchChapterData(nextId)
                                        if (loadedChapters.none { it.id == data.id }) {
                                            loadedChapters.add(data)
                                        }
                                        fetchingIds.remove(nextId)
                                        if (nextId < total) {
                                            launch(Dispatchers.IO) { fetchChapterData(nextId + 1) }
                                        }
                                    }
                                }
                            },
                            onLoadPreviousChapter = {
                                val firstLoaded = loadedChapters.firstOrNull()?.id ?: 0
                                val prevId = firstLoaded - 1
                                if (prevId >= 1 && !fetchingIds.contains(prevId)) {
                                    scope.launch {
                                        fetchingIds.add(prevId)
                                        val data = fetchChapterData(prevId)
                                        if (loadedChapters.none { it.id == data.id }) {
                                            loadedChapters.add(0, data)
                                        }
                                        fetchingIds.remove(prevId)
                                        if (prevId > 1) {
                                            launch(Dispatchers.IO) { fetchChapterData(prevId - 1) }
                                        }
                                    }
                                }
                            },
                            onRefresh = { chapterIdToRefresh ->
                                scope.launch {
                                    val refreshedData =
                                        fetchChapterData(chapterIdToRefresh, forceRefresh = true)
                                    val index =
                                        loadedChapters.indexOfFirst { it.id == chapterIdToRefresh }
                                    if (index != -1) {
                                        loadedChapters[index] = refreshedData
                                    }

                                    loadedChapters.removeAll { chapter ->
                                        chapter.id != chapterIdToRefresh && (chapter.body == "")
                                    }
                                }
                            },
                            onSaveProgress = { chapterId, offset ->
                                saveReadProgress(novelId, chapterId, offset)
                            }
                        )
                    }
                }
            }
        }
    }
}