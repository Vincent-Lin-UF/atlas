package sources

import Chapter
import ChapterContent
import Novel
import SearchResult
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
            Novel(
                title = element.selectFirst("h2.fiction-title a")?.text() ?: "Unknown",
                url = element.selectFirst("h2.fiction-title a")?.attr("abs:href") ?: "",
                imageUrl = element.selectFirst("img")?.attr("abs:src") ?: ""
            )
        }

        hasNextPage = novels.isNotEmpty()

        return SearchResult(novels, if (hasNextPage) currentPage.toString() else null)
    }

    override suspend fun getChapters(novelUrl: String): List<Chapter> {
        val doc = Jsoup.connect(novelUrl).userAgent(userAgent).get()

        return doc.select("#chapters tbody tr").map { element ->
            val link = element.selectFirst("td a")
            Chapter(
                name = link?.text() ?: "Unknown Chapter",
                url = link?.attr("abs:href") ?: ""
            )
        }
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        val doc = Jsoup.connect(chapterUrl).userAgent(userAgent).get()

        val title = doc.selectFirst("h1.font-white")?.text() ?: ""
        val contentElement = doc.selectFirst(".chapter-inner")

        contentElement?.select("script, style")?.remove()

        val content = contentElement?.children()?.joinToString("\n") { it.text() }
            ?: contentElement?.text()
            ?: "No content found"

        return ChapterContent(title, content)
    }

    override fun getNextChapter(chapters: List<Chapter>, currentChapter: Chapter): Chapter? {
        val currentIndex = chapters.indexOfFirst { it.url == currentChapter.url }
        return if (currentIndex != -1 && currentIndex < chapters.size - 1) {
            chapters[currentIndex + 1]
        } else null
    }

    override fun getPrevChapter(chapters: List<Chapter>, currentChapter: Chapter): Chapter? {
        val currentIndex = chapters.indexOfFirst { it.url == currentChapter.url }
        return if (currentIndex > 0) {
            chapters[currentIndex - 1]
        } else null
    }
}