package com.atlas.app.sources

import com.atlas.app.data.Chapter
import com.atlas.app.data.Novel
import com.atlas.app.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class RoyalRoad : NovelSource {
    override val name = "Royal Road"
    override val baseUrl = "https://www.royalroad.com"
    override var currentPage = 1
    override var currentQuery: String? = null
    override var hasNextPage = false

    override suspend fun search(query: String): SearchResult {
        currentPage = 1
        currentQuery = query
        return fetchResults()
    }

    override suspend fun loadNextPage(): SearchResult {
        currentPage++
        return fetchResults()
    }

    private fun fetchResults(): SearchResult {
        val query = currentQuery ?: return SearchResult(emptyList(), null)
        val url = "$baseUrl/fictions/search?title=$query&page=$currentPage"

        val doc = Jsoup.connect(url)
            .userAgent(userAgent)
            .get()

        val novelElements = doc.select(".fiction-list-item")

        val novels = novelElements.map { element ->
            val chaptersText = element.selectFirst("div.row.stats > div:nth-of-type(5) span")?.text()

            val chapterCount = chaptersText
                ?.filter { it.isDigit() }
                ?.toIntOrNull()

            val title = element.selectFirst("h2.fiction-title a")?.text() ?: "Unknown"
            val sourceName = "Royal Road"

            val id = (title + sourceName).hashCode().toString()

            Novel(
                id = id,
                title = title,
                source = sourceName,
                url = element.selectFirst("h2.fiction-title a")?.attr("abs:href") ?: "",
                coverAsset = element.selectFirst("img")?.attr("abs:src") ?: "",
                chapterCount = chapterCount ?: 0
            )
        }

        hasNextPage = novels.isNotEmpty()

        return SearchResult(novels, if (hasNextPage) currentPage.toString() else null)
    }

    override suspend fun getChapters(novel: Novel): List<Chapter> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(novel.url).userAgent(userAgent).get()

         novel.author = doc.selectFirst("div.fic-title h4 span a")?.text()
         novel.description = doc.selectFirst("div.description")?.text()

        return@withContext doc.select("#chapters tbody tr").mapIndexed { index, element ->
            val link = element.selectFirst("td a")
            Chapter(
                novelId = novel.id,
                index = index + 1,
                name = link?.text() ?: "Unknown Chapter",
                url = link?.attr("abs:href") ?: "",
                body = null
            )
        }
    }

    override suspend fun getChapterBody(chapterUrl: String): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(chapterUrl).userAgent(userAgent).get()
        val contentElement = doc.selectFirst(".chapter-inner")

        contentElement?.select("script, style")?.remove()

        val content = contentElement?.children()?.joinToString("\n") { it.text() }
            ?: contentElement?.text()
            ?: "No content found"

        return@withContext content
    }
}