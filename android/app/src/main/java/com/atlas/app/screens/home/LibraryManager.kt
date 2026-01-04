package com.atlas.app.screens.home

import android.content.Context
import com.atlas.app.data.Chapter
import com.atlas.app.data.Novel
import com.atlas.app.data.AppDatabase
import com.atlas.app.sources.NovelFire
import com.atlas.app.sources.NovelSource
import com.atlas.app.sources.RoyalRoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LibraryManager {
    private val allSources: List<NovelSource> = listOf(
        NovelFire(),
        RoyalRoad(),
    )

    fun getSourceNames(): List<String> = listOf("All") + allSources.map { it.name }

    private fun findSource(sourceName: String?): NovelSource? {
        return allSources.find { it.name == sourceName }
    }

    private fun getSourcesForIndex(index: Int): List<NovelSource> {
        return if (index == 0) {
            allSources
        } else {
            listOfNotNull(allSources.getOrNull(index - 1))
        }
    }

    suspend fun search(query: String, sourceIndex: Int): List<Novel> =
        withContext(Dispatchers.IO) {
            val targets = getSourcesForIndex(sourceIndex)

            targets.flatMap { source ->
                try {
                    source.search(query).novels.map { it.copy(source = source.name) }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun loadNextPage(sourceIndex: Int): List<Novel> = withContext(Dispatchers.IO) {
        val targets = getSourcesForIndex(sourceIndex)

        targets.filter { it.hasNextPage }.flatMap { source ->
            try {
                source.loadNextPage().novels.map { it.copy(source = source.name) }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getNovelDetails(novel: Novel): Novel = withContext(Dispatchers.IO) {
        val source = findSource(novel.source) ?: return@withContext novel

        val chapters = try {
            source.getChapters(novel)
        } catch (_: Exception) {
            emptyList()
        }

        return@withContext novel.copy(chapterCount = chapters.size)
    }

    suspend fun addToLibrary(context: Context, novel: Novel) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        val novelToSave = novel.copy(category = if (novel.category == "None") "Reading" else novel.category)
        db.novelDao().insertNovel(novelToSave)

        syncChapters(context, novelToSave)
    }

    suspend fun syncChapters(context: Context, novel: Novel) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val source = findSource(novel.source) ?: return@withContext

        try {
            val webChapters = source.getChapters(novel)

            if (webChapters.isNotEmpty()) {
                val entities = webChapters.mapIndexed { index, rawChapter ->
                    Chapter(
                        novelId = novel.id,
                        index = index + 1,
                        name = rawChapter.name,
                        url = rawChapter.url,
                        body = null
                    )
                }

                db.novelDao().insertNovelWithChapters(
                    novel.copy(chapterCount = entities.size),
                    entities
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getChapterContent(context: Context, chapter: Chapter, forceRefresh: Boolean = false): Chapter = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        if (!chapter.body.isNullOrBlank() && !forceRefresh) {
            return@withContext chapter
        }

        val source = findSource(findSourceForChapter(context, chapter.novelId))
        if (source != null) {
            try {
                val bodyText = source.getChapterBody(chapter.url)

                db.chapterDao().updateChapterBody(chapter.id, bodyText)

                return@withContext chapter.copy(body = bodyText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext chapter.copy(body = "")
    }

    suspend fun fetchChaptersForPreview(novel: Novel): List<Chapter> {
        val source = findSource(novel.source) ?: return emptyList()
        return try {
            val webChapters = source.getChapters(novel)
            webChapters.mapIndexed { index, raw ->
                Chapter(
                    id = 0,
                    novelId = novel.id,
                    index = index + 1,
                    name = raw.name,
                    url = raw.url,
                    body = null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun findSourceForChapter(context: Context, novelId: String): String? {
        return AppDatabase.getDatabase(context).novelDao().getNovel(novelId)?.source
    }
}