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
    val id: String,
    val title: String,
    val url: String,
    var author: String? = null,
    val source: String,
    var description: String? = null,
    val coverAsset: String? = null,
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
            val chaptersFile = File(folder, "chapters.json")

            if (!infoFile.exists()) continue

            val infoJson = JSONObject(infoFile.readText())
            val id = folder.name

            val coverPath = infoJson.optString("coverAsset", "").takeIf { it.isNotEmpty() }
                ?: File(folder, "cover.jpg").takeIf { it.exists() }?.absolutePath

            val titlesList = mutableListOf<String>()
            if (chaptersFile.exists()) {
                val chapArray = JSONArray(chaptersFile.readText())
                for (i in 0 until chapArray.length()) {
                    titlesList.add(chapArray.getJSONObject(i).getString("name"))
                }
            }

            val lastChapter = prefs.getInt("last_chapter_$id", 0)
            val lastPos = prefs.getInt("last_pos_$id", 0)
            val lastTime = prefs.getLong("last_time_$id", 0L)

            val defaultCategory = infoJson.optString("category", "None")
            val savedCategory = prefs.getString("category_$id", defaultCategory) ?: "None"

            val novel = Novel(
                id = id,
                title = infoJson.optString("title", "Unknown Title"),
                author = infoJson.optString("author", "Unknown Author"),
                source = infoJson.optString("source", "Unknown Source"),
                description = infoJson.optString("description", "No description available."),
                category = savedCategory,
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
            e.printStackTrace()
            continue
        }
    }
    return novels.sortedByDescending { it.lastReadTime }
}