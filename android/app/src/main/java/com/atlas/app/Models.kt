package com.atlas.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class Novel(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val category: String,
    val chapterCount: Int,
    val coverAsset: String? = null,
    val lastReadChapter: Int = 1,
    val lastReadPosition: Int = 0,
    val lastReadTime: Long = 0L
)

data class ChapterData(
    val id: Int, val title: String, val body: String
)

fun loadNovelsFromAssets(context: Context, prefs: SharedPreferences): List<Novel> {
    val novels = mutableListOf<Novel>()
    val assetManager = context.assets

    try {
        val rootList = assetManager.list("") ?: return emptyList()

        for (path in rootList) {
            try {
                // Read novel info from JSON
                val inputStream = assetManager.open("$path/info.json")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                reader.close()

                val json = JSONObject(jsonString)
                val filesInFolder = assetManager.list(path) ?: emptyArray()

                // Calculate chapter count
                val chapterCount = json.optInt("totalChapters", 0)

                // Check for cover image
                val hasCover = filesInFolder.contains("cover.jpg")
                val coverPath = if (hasCover) "$path/cover.jpg" else null

                // Read progress and category
                val lastChapter = prefs.getInt("last_chapter_$path", 1)
                val lastPos = prefs.getInt("last_pos_$path", 0)
                val lastTime = prefs.getLong("last_time_$path", 0L)

                val defaultCategory = json.optString("category", "None")
                val savedCategory = prefs.getString("category_$path", defaultCategory) ?: "None"

                val novel = Novel(
                    id = path,
                    title = json.optString("title", "Unknown Title"),
                    author = json.optString("author", "Unknown Author"),
                    description = json.optString("description", "No description available."),
                    category = savedCategory,
                    chapterCount = chapterCount,
                    coverAsset = coverPath,
                    lastReadChapter = lastChapter,
                    lastReadPosition = lastPos,
                    lastReadTime = lastTime
                )
                novels.add(novel)

            } catch (_: Exception) {
                continue
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return novels
}