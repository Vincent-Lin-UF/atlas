package com.atlas.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@Parcelize
data class Novel (
    val id: String, // URL hash or unique ID
    val title: String,
    val url: String, // The source URL (previously sourceUrl in AppModels)
    var author: String? = null,
    var description: String? = null,
    val coverAsset: String? = null, // Path for local or URL for remote
    val category: String = "None",
    val chapterCount: Int = 0,
    val lastReadChapter: Int = 1,
    val lastReadPosition: Int = 0,
    val lastReadTime: Long = 0L,
    val chapterTitles: List<String> = emptyList()
) : Parcelable

data class Chapter(
    val name: String,
    val url: String,
)

data class SearchResult(
    val novels: List<Novel>,
    val nextPage: String?
)

data class ChapterData(
    val id: Int,
    val title: String,
    val body: String
)

fun loadNovelsFromStorage(context: Context, prefs: SharedPreferences): List<Novel> {
    val novels = mutableListOf<Novel>()
    val novelsDir = File(context.filesDir, "novels")

    if (!novelsDir.exists()) return emptyList()

    val novelFolders = novelsDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()

    for (folder in novelFolders) {
        try {
            val infoFile = File(folder, "info.json")
            val chaptersFile = File(folder, "chapters.json") // Reference separate file

            if (!infoFile.exists()) continue

            val infoJson = JSONObject(infoFile.readText())
            val id = folder.name

            // 1. Resolve Cover Path: Prioritize the path saved in JSON
            val coverPath = infoJson.optString("coverAsset", "").takeIf { it.isNotEmpty() }
                ?: File(folder, "cover.jpg").takeIf { it.exists() }?.absolutePath

            // 2. Load Chapter Titles from separate chapters.json
            val titlesList = mutableListOf<String>()
            if (chaptersFile.exists()) {
                val chapArray = JSONArray(chaptersFile.readText())
                for (i in 0 until chapArray.length()) {
                    // Extract names from the objects in chapters.json
                    titlesList.add(chapArray.getJSONObject(i).getString("name"))
                }
            }

            // 3. Sync Preferences (Progress & Category)
            val lastChapter = prefs.getInt("last_chapter_$id", 0)
            val lastPos = prefs.getInt("last_pos_$id", 0)
            val lastTime = prefs.getLong("last_time_$id", 0L)

            val defaultCategory = infoJson.optString("category", "None")
            val savedCategory = prefs.getString("category_$id", defaultCategory) ?: "None"

            val novel = Novel(
                id = id,
                title = infoJson.optString("title", "Unknown Title"),
                author = infoJson.optString("author", "Unknown Author"),
                description = infoJson.optString("description", "No description available."),
                category = savedCategory,
                // Ensure count is based on actual chapters list if JSON is missing it
                chapterCount = infoJson.optInt("totalChapters", titlesList.size),
                coverAsset = coverPath,
                url = infoJson.optString("sourceUrl", ""),
                lastReadChapter = lastChapter,
                lastReadPosition = lastPos,
                lastReadTime = lastTime,
                chapterTitles = titlesList
            )
            novels.add(novel)

        } catch (e: Exception) {
            e.printStackTrace() // Useful for debugging malformed JSON
            continue
        }
    }
    // Return sorted by last read time for the History screen
    return novels.sortedByDescending { it.lastReadTime }
}