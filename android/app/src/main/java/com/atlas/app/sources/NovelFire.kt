package com.atlas.app.sources

import com.atlas.app.data.Chapter
import com.atlas.app.data.Novel
import com.atlas.app.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup

class NovelFire : NovelSource {
    override val name = "NovelFire"
    override val baseUrl = "https://novelfire.net"
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

        val response = try {
            Jsoup.connect("$baseUrl/search")
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", baseUrl)
                .data("keyword", query)
                .data("page", currentPage.toString())
                .timeout(10000)
                .execute()
        } catch (e: Exception) {
            e.printStackTrace()
            return SearchResult(emptyList(), null)
        }

        val doc = response.parse()

        val novelElements = doc.select(".chapters .novel-item")

        val novels = novelElements.map { element ->
            val linkElement = element.selectFirst("a")
            val titleElement = element.selectFirst(".novel-title") ?: element.selectFirst("h4")
            val imgElement = element.selectFirst("img")

            val actualImageUrl = when {
                imgElement?.hasAttr("data-src") == true -> imgElement.attr("abs:data-src")
                imgElement?.hasAttr("data-lazy-src") == true -> imgElement.attr("abs:data-lazy-src")
                else -> imgElement?.attr("abs:src") ?: ""
            }

            val chaptersText = element.selectFirst(".novel-stats span")?.text()
            val title = titleElement?.text()?.trim() ?: "Unknown"
            val sourceName = "NovelFire"
            val id = (title + sourceName).hashCode().toString()

            Novel(
                id = id,
                title = title,
                source = sourceName,
                url = linkElement?.attr("abs:href") ?: "",
                coverAsset = actualImageUrl,
                chapterCount = chaptersText?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            )
        }

        hasNextPage = novels.isNotEmpty()
        return SearchResult(novels, if (hasNextPage) currentPage.toString() else null)
    }

    override suspend fun getChapters(novel: Novel): List<Chapter> = withContext(Dispatchers.IO) {
        return@withContext try {
            val initialResponse = Jsoup.connect(novel.url)
                .userAgent(userAgent)
                .header("Referer", baseUrl)
                .timeout(10000)
                .execute()

            val doc = initialResponse.parse()

            novel.author = doc.selectFirst("div.author a span")?.text()
            novel.description = doc.selectFirst("div.summary div.content")?.text()

            val csrfToken = doc.select("meta[name=csrf-token]").attr("content")
            val novelId = doc.select("#novel-report").attr("report-post_id")

            if (csrfToken.isEmpty() || novelId.isEmpty()) return@withContext emptyList()

            val ajaxDomain = if (novel.url.contains("www.novelfire.net")) {
                "https://www.novelfire.net"
            } else {
                "https://novelfire.net"
            }
            val ajaxUrl = "$ajaxDomain/ajax/getListChapterById"

            val jsonResponse = Jsoup.connect(ajaxUrl)
                .method(Connection.Method.POST)
                .userAgent(userAgent)
                .header("Referer", novel.url)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookies(initialResponse.cookies())
                .data("post_id", novelId)
                .data("_token", csrfToken)
                .ignoreContentType(true)
                .execute()
                .body()

            val jsonObject = JSONObject(jsonResponse)
            val jsonArray = jsonObject.getJSONArray("data")
            val chapters = mutableListOf<Chapter>()
            val baseUrlStripped = novel.url.removeSuffix("/")

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawTitle = obj.optString("title", "Chapter ${i + 1}")
                val nSort = obj.optString("n_sort", "$i")

                val cleanName = Jsoup.parse(rawTitle).text().trim()

                chapters.add(
                    Chapter(
                        novelId = novel.id,
                        index = i + 1,
                        name = cleanName,
                        url = "$baseUrlStripped/chapter-$nSort",
                        body = null
                    )
                )
            }
            chapters
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getChapterBody(chapterUrl: String): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(chapterUrl).userAgent(userAgent).get()

        val contentElement = doc.selectFirst("#content")
        if (contentElement != null) {
            val badSelectors = mutableSetOf<String>()
            val hiddenCssRegex = Regex(
                "([.#]?[a-zA-Z0-9_-]+)\\s*\\{[^}]*?(width:\\s*0|height:\\s*[01](px)?|display:\\s*none)[^}]*?\\}",
                RegexOption.IGNORE_CASE
            )
            doc.select("style").forEach { styleTag ->
                hiddenCssRegex.findAll(styleTag.data()).forEach { match ->
                    badSelectors.add(match.groupValues[1])
                }
            }
            if (badSelectors.isNotEmpty()) {
                try {
                    contentElement.select(badSelectors.joinToString(", ")).remove()
                } catch (_: Exception) {
                }
            }
            contentElement.select("[style~=display:\\s*none], [style~=(width|height):\\s*[01](px)?]").remove()
            contentElement.select("[width=0], [width=1], [height=0], [height=1]").remove()
            contentElement.select("script, style").remove()
        }

        val content = contentElement?.select("p")?.joinToString("\n") { it.text() }
            ?: contentElement?.text()
            ?: "No content found"

        return@withContext content.trim()
    }
}