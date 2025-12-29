data class Novel(
    val title: String,
    val url: String,
    val imageUrl: String
)

data class SearchResult(
    val novels: List<Novel>,
    val nextPage: String?
)

data class Chapter(
    val name: String,
    val url: String
)

data class ChapterContent(
    val title: String,
    val content: String
)