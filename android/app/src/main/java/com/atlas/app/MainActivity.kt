package com.atlas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.atlas.app.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPref = getSharedPreferences("atlas_prefs", MODE_PRIVATE)
        val lastScreenType = sharedPref.getString("last_screen_type", "home")
        val lastActiveNovelId = sharedPref.getString("last_active_novel_id", "")

        setContent {
            AtlasTheme {
                val navController = rememberNavController()
                var allNovels by remember {
                    mutableStateOf(loadNovelsFromAssets(this, sharedPref))
                }
                var currentHomeTab by rememberSaveable { mutableIntStateOf(0) }

                // Restore app state only if user was reading
                LaunchedEffect(Unit) {
                    if (!lastActiveNovelId.isNullOrEmpty()) {
                        if (lastScreenType == "reader") {
                            navController.navigate("details/$lastActiveNovelId") {
                                launchSingleTop = true
                            }
                            val lastChapter =
                                sharedPref.getInt("last_chapter_$lastActiveNovelId", 1)
                            navController.navigate("reader/$lastActiveNovelId/$lastChapter")
                        }
                    }
                }

                // Save app state if user was reading or on home page
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val route = entry.destination.route
                        val args = entry.arguments
                        when (route) {
                            "home" -> sharedPref.edit { putString("last_screen_type", "home") }
                            "reader/{novelId}/{chapterIndex}" -> {
                                val currentId = args?.getString("novelId") ?: ""
                                sharedPref.edit {
                                    putString(
                                        "last_screen_type",
                                        "reader"
                                    ).putString("last_active_novel_id", currentId)
                                }
                            }
                        }
                    }
                }

                // Helper to save reading progress
                fun saveReadProgress(novelId: String, chapterIndex: Int, scrollPos: Int) {
                    val currentTime = System.currentTimeMillis()

                    sharedPref.edit {
                        putInt("last_chapter_$novelId", chapterIndex).putInt(
                                "last_pos_$novelId",
                                scrollPos
                            ).putLong("last_time_$novelId", currentTime)
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
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                    // Home screen
                    composable("home") {
                        HomeScreen(
                            novels = allNovels,
                            selectedTab = currentHomeTab,
                            onTabChange = { currentHomeTab = it },
                            onNovelSelect = { novelId -> navController.navigate("details/$novelId") },
                            onRemoveFromHistory = { novelId ->
                                saveReadProgress(novelId, 0, 0)
                            })
                    }

                    // Details Screen
                    composable("details/{novelId}") { backStackEntry ->
                        val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                        val novel = allNovels.find { it.id == novelId }

                        if (novel != null) {
                            NovelScreen(
                                novel = novel,
                                onBack = { navController.popBackStack() },
                                onChapterClick = { chapterId ->
                                    navController.navigate("reader/$novelId/$chapterId")
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
                                        putString("category_$novelId", newCategory)
                                    }
                                })
                        }
                    }

                    // Reader Screen
                    composable(
                        route = "reader/{novelId}/{chapterIndex}",
                        arguments = listOf(
                            navArgument("novelId") { type = NavType.StringType },
                            navArgument("chapterIndex") {
                                type = NavType.IntType
                            })) { backStackEntry ->
                        val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                        val startChapterIndex =
                            backStackEntry.arguments?.getInt("chapterIndex") ?: 1
                        val novel = allNovels.find { it.id == novelId }
                        val totalChapters = novel?.chapterCount ?: 0

                        // Helper to load chapter
                        fun loadChapterContent(id: Int): ChapterData {
                            val rawContent = try {
                                assets.open("$novelId/$id.txt").bufferedReader()
                                    .use { it.readText() }
                                    .replace(Regex("(?<!\n)\n(?!\n)"), "\n\n") + "\n"
                            } catch (_: Exception) {
                                ""
                            }

                            val (title, body) = if (rawContent.isBlank()) {
                                "Chapter $id" to ""
                            } else {
                                val firstLineEnd = rawContent.indexOf('\n')
                                if (firstLineEnd != -1) {
                                    rawContent.take(firstLineEnd).trim() to rawContent.substring(
                                        firstLineEnd + 1
                                    )
                                } else {
                                    rawContent to ""
                                }
                            }
                            return ChapterData(id, title, body)
                        }

                        val loadedChapters = remember { mutableStateListOf<ChapterData>() }

                        // Wipe stale data
                        LaunchedEffect(startChapterIndex, novelId) {
                            if (loadedChapters.isEmpty() || loadedChapters.none { it.id == startChapterIndex }) {
                                loadedChapters.clear()
                                loadedChapters.add(loadChapterContent(startChapterIndex))
                            }
                        }

                        // Determine scroll position
                        val initialScroll =
                            if (novel != null && novel.lastReadChapter == startChapterIndex) {
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
                                if (lastLoaded < totalChapters) {
                                    val nextId = lastLoaded + 1
                                    if (loadedChapters.none { it.id == nextId }) {
                                        loadedChapters.add(loadChapterContent(nextId))
                                    }
                                }
                            },
                            onLoadPreviousChapter = {
                                val firstLoaded = loadedChapters.firstOrNull()?.id ?: 0
                                if (firstLoaded > 1) {
                                    val prevId = firstLoaded - 1
                                    if (loadedChapters.none { it.id == prevId }) {
                                        loadedChapters.add(0, loadChapterContent(prevId))
                                    }
                                }
                            },
                            onSaveProgress = { chapterId, offset ->
                                saveReadProgress(novelId, chapterId, offset)
                            })
                    }
                }
            }
        }
    }
}