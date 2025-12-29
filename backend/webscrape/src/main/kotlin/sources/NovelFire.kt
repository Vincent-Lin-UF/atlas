package sources

import Chapter
import ChapterContent
import Novel
import SearchResult
import org.jsoup.Jsoup

class NovelFire : NovelSource {
    override val name = "NovelFire"
    override val baseUrl = "https://www.novelfire.net"
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
        val url = "$baseUrl/search?keyword=$query&page=$currentPage"

        val doc = Jsoup.connect(url)
            .userAgent(userAgent)
            .get()

        val novelElements = doc.select("li.novel-item")

        val novels = novelElements.map { element ->
            Novel(
                title = element.selectFirst("h4.novel-title")?.text() ?: "Unknown",
                url = element.selectFirst("a")?.attr("abs:href") ?: "",
                imageUrl = element.selectFirst("img")?.attr("abs:src") ?: ""
            )
        }

        hasNextPage = novels.isNotEmpty()

        return SearchResult(novels, if (hasNextPage) currentPage.toString() else null)
    }

    private fun cleanTitle(raw: String): String {
        val unicodeRegex = Regex("""\\u([0-9a-fA-F]{4})""")
        var title = unicodeRegex.replace(raw) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        title = title.replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace(Regex("""[\u007F-\u009F\u00AD\u200B-\u200F\uFEFF]"""), "")
            .replace("\u00c2", "")
        return title.replace(Regex("""\s+"""), " ").trim()
    }

    override suspend fun getChapters(novelUrl: String): List<Chapter> {
        return try {
            val initialResponse = Jsoup.connect(novelUrl)
                .userAgent(userAgent)
                .execute()

            val doc = initialResponse.parse()
            val csrfToken = doc.select("meta[name=csrf-token]").attr("content")
            val novelId = doc.select("#novel-report").attr("report-post_id")

            if (csrfToken.isEmpty() || novelId.isEmpty()) return emptyList()

            val jsonResponse = Jsoup.connect("https://novelfire.net/ajax/getListChapterById")
                .method(org.jsoup.Connection.Method.POST)
                .header("X-CSRF-TOKEN", csrfToken)
                .cookies(initialResponse.cookies())
                .data("post_id", novelId)
                .data("_token", csrfToken)
                .ignoreContentType(true)
                .execute()
                .body()

            val baseUrl = novelUrl.removeSuffix("/")
            val pattern = Regex("""\{"id":\d+,"title":"(.*?)","n_sort":(\d+)}""")

            pattern.findAll(jsonResponse).map { match ->
                val rawTitle = match.groupValues[1]
                val nSort = match.groupValues[2]

                Chapter(
                    name = cleanTitle(rawTitle),
                    url = "$baseUrl/chapter-$nSort"
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        val doc = Jsoup.connect(chapterUrl).userAgent(userAgent).get()

        val title = doc.selectFirst(".chapter-title")?.text() ?: "Unknown Title"
        val contentElement = doc.selectFirst("#content")

        contentElement?.select("[style*='height:1px'], [style*='width:0'], [style*='display:none']")?.remove()

        contentElement?.allElements?.forEach { element ->
            if (element.tagName() !in listOf("p", "br", "div", "h4", "strong")) {
                element.remove()
            }
        }

        contentElement?.select("script, style")?.remove()
        val content = contentElement?.select("p")?.joinToString("\n") { it.text() }
            ?: contentElement?.text()
            ?: "No content found"

        return ChapterContent(title, content.trim())
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