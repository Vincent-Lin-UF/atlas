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
    // --- Sources Configuration ---
    private val allSources: List<NovelSource> = listOf(
        NovelFire(),
        RoyalRoad(),
    )
    private var activeSources: List<NovelSource> = emptyList()

    fun getSourceNames(): List<String> = listOf("All") + allSources.map { it.name }

    private fun findSource(sourceName: String?): NovelSource? {
        return allSources.find { it.name == sourceName }
    }

    // --- Search & Discovery (Web Only) ---
    // These methods strictly query the web for the "Browse" screen.
    // They do not save to the database automatically.
    suspend fun search(query: String, sourceIndex: Int): List<Novel> =
        withContext(Dispatchers.IO) {
            activeSources = if (sourceIndex == 0) {
                allSources
            } else {
                listOfNotNull(allSources.getOrNull(sourceIndex - 1))
            }

            activeSources.flatMap { source ->
                try {
                    // Note: returned novels are not yet in the DB
                    source.search(query).novels.map { it.copy(source = source.name) }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun loadNextPage(): List<Novel> = withContext(Dispatchers.IO) {
        activeSources.filter { it.hasNextPage }.flatMap { source ->
            try {
                source.loadNextPage().novels.map { it.copy(source = source.name) }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // --- Database / Library Operations ---

    /**
     * Gets novel details (chapter count) for the preview screen.
     * Does NOT save to DB yet, just fetches metadata from the web.
     */
    suspend fun getNovelDetails(novel: Novel): Novel = withContext(Dispatchers.IO) {
        val source = findSource(novel.source) ?: return@withContext novel

        val chapters = try {
            source.getChapters(novel)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        return@withContext novel.copy(chapterCount = chapters.size)
    }

    /**
     * Adds a novel to the library (or updates it), then fetches and saves its chapter list.
     */
    suspend fun addToLibrary(context: Context, novel: Novel) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        // 1. Save Novel with "Reading" status
        val novelToSave = novel.copy(category = if (novel.category == "None") "Reading" else novel.category)
        db.novelDao().insertNovel(novelToSave)

        // 2. Sync Chapters
        syncChapters(context, novelToSave)
    }

    /**
     * Fetches the latest chapters from the web and updates the database.
     */
    suspend fun syncChapters(context: Context, novel: Novel) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val source = findSource(novel.source) ?: return@withContext

        try {
            // Fetch raw list from source
            // Note: Source returns basic Chapter objects. We must map them to Entities.
            val webChapters = source.getChapters(novel)

            if (webChapters.isNotEmpty()) {
                val entities = webChapters.mapIndexed { index, rawChapter ->
                    Chapter(
                        novelId = novel.id,
                        index = index + 1, // 1-based index
                        name = rawChapter.name,
                        url = rawChapter.url,
                        body = null // Body is fetched only when reading
                    )
                }

                // Transaction: Update novel count and insert all chapters
                db.novelDao().insertNovelWithChapters(
                    novel.copy(chapterCount = entities.size),
                    entities
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns the list of chapters.
     * Logic: Checks DB first. If empty, syncs from Web, saves to DB, then returns.
     */
    suspend fun getChapters(context: Context, novel: Novel): List<Chapter> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        // 1. Try Local Cache
        val cached = db.chapterDao().getChaptersForNovel(novel.id)
        if (cached.isNotEmpty()) {
            return@withContext cached
        }

        // 2. If missing, Sync from Web
        syncChapters(context, novel)

        // 3. Return newly cached data
        return@withContext db.chapterDao().getChaptersForNovel(novel.id)
    }

    /**
     * Returns the full content (body) of a specific chapter.
     * Logic: Checks DB. If body is null, fetches from Web, updates DB, returns content.
     */
    suspend fun getChapterContent(context: Context, chapter: Chapter): Chapter = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        // 1. Check if we already have the body
        if (!chapter.body.isNullOrBlank()) {
            return@withContext chapter
        }

        // 2. Fetch from Web
        val source = findSource(findSourceForChapter(context, chapter.novelId))
        if (source != null) {
            try {
                // Fetch body string
                // Note: assuming source.getChapterContent returns a wrapper or string
                // We'll assume the source returns a simple data object with 'body'
                val bodyText = source.getChapterBody(chapter.url)

                // 3. Update Database
                db.chapterDao().updateChapterBody(chapter.id, bodyText)

                // 4. Return updated object
                return@withContext chapter.copy(body = bodyText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Return original if failure
        return@withContext chapter.copy(body = "Error loading content.")
    }

    suspend fun fetchChaptersForPreview(novel: Novel): List<Chapter> {
        val source = findSource(novel.source) ?: return emptyList()
        return try {
            val webChapters = source.getChapters(novel)
            // Map raw source chapters to Entity objects (but don't save them)
            webChapters.mapIndexed { index, raw ->
                Chapter(
                    id = 0, // Not persisted
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

    // Helper to find source name for a chapter (by looking up its novel)
    private suspend fun findSourceForChapter(context: Context, novelId: String): String? {
        return AppDatabase.getDatabase(context).novelDao().getNovel(novelId)?.source
    }
}