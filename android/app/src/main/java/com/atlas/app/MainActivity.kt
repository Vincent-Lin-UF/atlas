package com.atlas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.atlas.app.screens.ChaptersScreen
import com.atlas.app.screens.ReaderScreen
import com.atlas.app.screens.home.HomeScreen
import com.atlas.app.screens.home.LibraryManager
import com.atlas.app.ui.theme.AtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("atlas_prefs", MODE_PRIVATE)
        val lastScreenType = sharedPref.getString("last_screen_type", "home")
        val lastActiveNovelId = sharedPref.getString("last_active_novel_id", "")

        setContent {
            AtlasTheme {
                val navController = rememberNavController()

                var allNovels by remember {
                    mutableStateOf(loadNovelsFromStorage(this, sharedPref))
                }

                var currentHomeTab by rememberSaveable {
                    mutableIntStateOf(0)
                }

                LaunchedEffect(Unit) {
                    if (!lastActiveNovelId.isNullOrEmpty() && lastScreenType == "reader") {
                        navController.navigate("details/$lastActiveNovelId") {
                            launchSingleTop = true
                        }
                        val lastChapter =
                            sharedPref.getInt("last_chapter_$lastActiveNovelId", 1)
                        navController.navigate(
                            "reader/$lastActiveNovelId/$lastChapter"
                        )
                    }
                }

                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val route = entry.destination.route
                        val args = entry.arguments

                        when (route) {
                            "home" -> {
                                sharedPref.edit {
                                    putString("last_screen_type", "home")
                                }
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

                fun refreshLibrary() {
                    allNovels = loadNovelsFromStorage(this, sharedPref)
                }

                fun persistNovelIfNeeded(novel: Novel) {
                    val novelDir = File(filesDir, "novels/${novel.id}")
                    if (!novelDir.exists()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            LibraryManager.saveNovelToDisk(this@MainActivity, novel)
                            withContext(Dispatchers.Main) {
                                refreshLibrary()
                            }
                        }
                    }
                }

                fun saveReadProgress(
                    novelId: String,
                    chapterIndex: Int,
                    scrollPos: Int
                ) {
                    val currentTime = System.currentTimeMillis()

                    sharedPref.edit {
                        putInt("last_chapter_$novelId", chapterIndex)
                        putInt("last_pos_$novelId", scrollPos)
                        putLong("last_time_$novelId", currentTime)
                    }

                    allNovels = allNovels.map { novel ->
                        if (novel.id == novelId) {
                            novel.copy(
                                lastReadChapter = chapterIndex,
                                lastReadPosition = scrollPos,
                                lastReadTime = currentTime
                            )
                        } else {
                            novel
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

                    composable("home") {
                        HomeScreen(
                            novels = allNovels,
                            selectedTab = currentHomeTab,
                            onTabChange = { currentHomeTab = it },
                            onNovelSelect = { novelId ->
                                navController.navigate("details/$novelId")
                            },
                            onRemoveFromHistory = { novelId ->
                                saveReadProgress(novelId, 0, 0)
                            },
                            onRefreshLibrary = { refreshLibrary() },
                            onBrowseNovelSelect = { novelFromSearch ->
                                val generatedId = novelFromSearch.url.hashCode().toString()
                                val existing = allNovels.find { it.id == generatedId }

                                if (existing != null) {
                                    navController.navigate("details/$generatedId")
                                } else {
                                    val previewNovel = novelFromSearch.copy(
                                        id = generatedId,
                                        category = "None"
                                    )
                                    allNovels = allNovels + previewNovel
                                    navController.navigate("details/$generatedId")

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            val detailed = LibraryManager.getNovelDetails(previewNovel)
                                            withContext(Dispatchers.Main) {
                                                allNovels = allNovels.map { existingItem ->
                                                    if (existingItem.id == generatedId) {
                                                        detailed.copy(category = existingItem.category)
                                                    } else {
                                                        existingItem
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        )
                    }

                    composable("details/{novelId}") { backStackEntry ->
                        val novelId =
                            backStackEntry.arguments?.getString("novelId") ?: ""
                        val novel =
                            allNovels.find { it.id == novelId }

                        if (novel != null) {
                            ChaptersScreen(
                                novel = novel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onChapterClick = { chapterId ->
                                    persistNovelIfNeeded(novel)
                                    navController.navigate(
                                        "reader/$novelId/$chapterId"
                                    )
                                },
                                onCategoryChange = { newCategory ->
                                    allNovels = allNovels.map { item ->
                                        if (item.id == novelId) {
                                            item.copy(category = newCategory)
                                        } else {
                                            item
                                        }
                                    }
                                    sharedPref.edit {
                                        putString(
                                            "category_$novelId",
                                            newCategory
                                        )
                                    }

                                    if (newCategory != "None") {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            LibraryManager.addNovelToLibrary(
                                                this@MainActivity,
                                                novel.copy(category = newCategory),
                                                sharedPref
                                            )
                                            withContext(Dispatchers.Main) {
                                                refreshLibrary()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    composable(
                        route = "reader/{novelId}/{chapterIndex}",
                        arguments = listOf(
                            navArgument("novelId") { type = NavType.StringType },
                            navArgument("chapterIndex") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                        val startChapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 1
                        val novel = allNovels.find { it.id == novelId }
                        val totalChapters = novel?.chapterCount ?: 0

                        val scope = rememberCoroutineScope()

                        val loadedChapters = remember(startChapterIndex) { mutableStateListOf<ChapterData>() }
                        val fetchingIds = remember { mutableStateListOf<Int>() }

                        suspend fun fetchChapterData(index: Int): ChapterData = withContext(Dispatchers.IO) {
                            val novelDir = File(filesDir, "novels/$novelId")
                            val contentFile = File(novelDir, "$index.txt")
                            val chaptersFile = File(novelDir, "chapters.json")

                            if (contentFile.exists()) {
                                val body = contentFile.readText().replace(Regex("(?<!\n)\n(?!\n)"), "\n\n")
                                var title = "Chapter $index"
                                if (chaptersFile.exists()) {
                                    try {
                                        val json = org.json.JSONArray(chaptersFile.readText())
                                        for (i in 0 until json.length()) {
                                            val obj = json.getJSONObject(i)
                                            if (obj.getInt("index") == index) {
                                                title = obj.getString("name")
                                                break
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                return@withContext ChapterData(index, title, body)
                            }

                            if (novel != null) {
                                if (!chaptersFile.exists()) {
                                    LibraryManager.saveNovelToDisk(this@MainActivity, novel)
                                }

                                if (chaptersFile.exists()) {
                                    try {
                                        val json = org.json.JSONArray(chaptersFile.readText())
                                        var targetUrl = ""
                                        var targetName = "Chapter $index"

                                        for (i in 0 until json.length()) {
                                            val obj = json.getJSONObject(i)
                                            if (obj.getInt("index") == index) {
                                                targetUrl = obj.getString("url")
                                                targetName = obj.getString("name")
                                                break
                                            }
                                        }

                                        if (targetUrl.isNotEmpty()) {
                                            delay(1000)
                                            val content = LibraryManager.downloadChapterContent(novel, targetUrl)
                                            contentFile.writeText(content)
                                            return@withContext ChapterData(index, targetName, content)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            return@withContext ChapterData(index, "Error", "Could not download chapter. Check connection.")
                        }

                        fun prefetchChapter(index: Int) {
                            if (index <= totalChapters) {
                                scope.launch(Dispatchers.IO) {
                                    fetchChapterData(index)
                                }
                            }
                        }

                        LaunchedEffect(startChapterIndex, novelId) {
                            if (loadedChapters.none { it.id == startChapterIndex } && !fetchingIds.contains(startChapterIndex)) {
                                fetchingIds.add(startChapterIndex)
                                val data = fetchChapterData(startChapterIndex)

                                loadedChapters.clear()
                                loadedChapters.add(data)
                                fetchingIds.remove(startChapterIndex)

                                prefetchChapter(startChapterIndex + 1)
                            }
                        }

                        val initialScroll = if (novel != null && novel.lastReadChapter == startChapterIndex) {
                            novel.lastReadPosition
                        } else {
                            0
                        }

                        ReaderScreen(
                            novelTitle = novel?.title ?: "Unknown",
                            chapters = loadedChapters,
                            totalChapters = totalChapters,
                            initialChapterId = startChapterIndex,
                            initialScroll = initialScroll,
                            onBack = { navController.popBackStack() },
                            onLoadNextChapter = {
                                val lastLoaded = loadedChapters.lastOrNull()?.id ?: 0
                                val nextId = lastLoaded + 1

                                if (nextId <= totalChapters && !fetchingIds.contains(nextId)) {
                                    scope.launch {
                                        fetchingIds.add(nextId)
                                        val data = fetchChapterData(nextId)

                                        if (loadedChapters.none { it.id == data.id }) {
                                            loadedChapters.add(data)
                                        }
                                        fetchingIds.remove(nextId)

                                        prefetchChapter(nextId + 1)
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

                                        prefetchChapter(prevId - 1)
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