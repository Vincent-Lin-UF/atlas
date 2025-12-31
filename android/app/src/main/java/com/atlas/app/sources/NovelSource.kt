package com.atlas.app.sources

import com.atlas.app.Chapter
import com.atlas.app.ChapterData
import com.atlas.app.Novel
import com.atlas.app.SearchResult

sealed interface NovelSource {
    val name: String
    val baseUrl: String

    var currentPage: Int
    var hasNextPage: Boolean
    var currentQuery: String?

    val userAgent: String
        get() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun search(query: String): SearchResult
    suspend fun loadNextPage(): SearchResult
    suspend fun getChapters(novel: Novel): List<Chapter>
    suspend fun getChapterContent(chapterUrl: String): ChapterData
    fun getNextChapter(chapters: List<Chapter>, currentChapter: Chapter): Chapter?
    fun getPrevChapter(chapters: List<Chapter>, currentChapter: Chapter): Chapter?
}