package com.atlas.app.screens.home

import android.content.Context
import android.content.SharedPreferences
import com.atlas.app.Novel
import com.atlas.app.sources.NovelFire
import com.atlas.app.sources.NovelSource
import com.atlas.app.sources.RoyalRoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LibraryManager {
    // Sources
    private val allSources: List<NovelSource> = listOf(
        NovelFire(),
        RoyalRoad(),
    )
    private var activeSources: List<NovelSource> = emptyList()
    fun getSourceNames(): List<String> = listOf("All Sources") + allSources.map { it.name }

    // Search functionality
    suspend fun search(query: String, sourceIndex: Int): List<Novel> =
        withContext(Dispatchers.IO) {
            activeSources = if (sourceIndex == 0) {
                allSources
            } else {
                listOfNotNull(allSources.getOrNull(sourceIndex - 1))
            }

            activeSources.flatMap { source ->
                try {
                    source.search(query).novels
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }

    // Load next page
    suspend fun loadNextPage(): List<Novel> = withContext(Dispatchers.IO) {
        // Only ask sources that actually reported having a next page
        activeSources.filter { it.hasNextPage }.flatMap { source ->
            try {
                source.loadNextPage().novels
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // Save novel to library
    suspend fun getNovelDetails(novel: Novel): Novel = withContext(Dispatchers.IO) {
        val source = allSources.find {
            novel.url.contains(it.baseUrl.removePrefix("https://").removePrefix("www."))
        }

        val chapters = try {
            source?.getChapters(novel) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        return@withContext novel.copy(
            chapterCount = chapters.size,
            chapterTitles = chapters.map { it.name }
        )
    }

    suspend fun saveNovelToDisk(
        context: Context,
        novel: Novel,
        chapters: List<com.atlas.app.Chapter>? = null
    ): String = withContext(Dispatchers.IO) {
        val novelId = novel.url.hashCode().toString()
        val finalChapters = chapters ?: fetchChapters(novel)

        val novelFolder = File(context.filesDir, "novels/$novelId").apply { if (!exists()) mkdirs() }

        // Save novel info
        val infoJson = JSONObject().apply {
            put("id", novelId)
            put("title", novel.title)
            put("author", novel.author ?: "Unknown Author")
            put("description", novel.description ?: "No description available.")
            put("category", "Reading")
            put("coverAsset", novel.coverAsset)
            put("totalChapters", finalChapters.size)
            put("sourceUrl", novel.url)
        }
        File(novelFolder, "info.json").writeText(infoJson.toString())

        // Save chapters
        val chaptersArray = JSONArray()
        finalChapters.forEachIndexed { index, chapter ->
            chaptersArray.put(JSONObject().apply {
                put("index", index + 1)
                put("name", chapter.name)
                put("url", chapter.url)
            })
        }
        File(novelFolder, "chapters.json").writeText(chaptersArray.toString())

        return@withContext novelId
    }

    suspend fun fetchChapters(novel: Novel): List<com.atlas.app.Chapter> = withContext(Dispatchers.IO) {
        val source = allSources.find {
            novel.url.contains(it.baseUrl.removePrefix("https://").removePrefix("www."))
        }
        return@withContext source?.getChapters(novel) ?: emptyList()
    }

    suspend fun addNovelToLibrary(
        context: Context,
        novel: Novel,
        sharedPref: SharedPreferences
    ) = withContext(Dispatchers.IO) {
        val novelId = saveNovelToDisk(context, novel, null)

        val savedIds = sharedPref.getStringSet("library_ids", mutableSetOf()) ?: mutableSetOf()
        val newIds = savedIds.toMutableSet().apply { add(novelId) }

        sharedPref.edit().apply {
            putStringSet("library_ids", newIds)
            if (!sharedPref.contains("category_$novelId")) {
                putString("category_$novelId", "Reading")
            }
        }.apply()
    }

    suspend fun downloadChapterContent(novel: Novel, chapterUrl: String): String = withContext(Dispatchers.IO) {
        val source = allSources.find {
            novel.url.contains(it.baseUrl.removePrefix("https://").removePrefix("www."))
        }
        return@withContext source?.getChapterContent(chapterUrl)?.body ?: "Error downloading content."
    }
}

