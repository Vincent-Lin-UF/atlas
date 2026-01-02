package com.atlas.app.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "novels")
data class Novel(
    @PrimaryKey val id: String,
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
    val lastReadTime: Long = 0L
) : Parcelable

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Novel::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["novelId", "index"], unique = true)]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val novelId: String,
    val index: Int,
    val name: String,
    val url: String,
    val body: String? = null
)

// --- UI Models ---
data class ChapterData(
    val id: Int,
    val title: String,
    val body: String
)

data class SearchResult(
    val novels: List<Novel>,
    val nextPage: String?
)