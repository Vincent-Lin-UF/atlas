import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import sources.NovelSource
import kotlin.random.Random
import kotlin.reflect.full.createInstance

fun main() = runBlocking {
    val availableSources = NovelSource::class.sealedSubclasses
        .map { it.createInstance() }

    if (availableSources.isEmpty()) {
        println("No sources found. Ensure your classes implement 'sealed interface NovelSource'.")
        return@runBlocking
    }

    println("Select a source:")
    availableSources.forEachIndexed { index, source ->
        println("${index + 1}. ${source.name}")
    }

    val sourceChoice = readln().toIntOrNull()?.minus(1)
    if (sourceChoice == null || sourceChoice !in availableSources.indices) {
        println("Invalid choice.")
        return@runBlocking
    }

    val selectedSource = availableSources[sourceChoice]

    print("\nSearching on ${selectedSource.name}\nEnter search query: ")
    val query = readln()

    val allNovels = mutableListOf<Novel>()
    var result = selectedSource.search(query)
    var selectedNovel: Novel? = null

    // 1. Search Loop
    while (true) {
        try {
            if (result.novels.isEmpty() && allNovels.isEmpty()) {
                println("No results found.")
                break
            }

            result.novels.forEach { novel ->
                allNovels.add(novel)
                println("${allNovels.size}. ${novel.title}")
            }

            val prompt = if (selectedSource.hasNextPage) {
                "\nSelect a number to view, or press Enter to load more: "
            } else {
                "\nSelect a number to view: "
            }
            print(prompt)

            val input = readln()
            val choice = input.toIntOrNull()

            if (choice != null && (choice - 1) in allNovels.indices) {
                selectedNovel = allNovels[choice - 1]
                break
            } else if (selectedSource.hasNextPage) {
                println("Loading more results...")
                delay(Random.nextLong(500, 1500))
                result = selectedSource.loadNextPage()
            } else {
                println("No more results available.")
                if (allNovels.isEmpty()) break
            }
        } catch (e: Exception) {
            println("Critical Error: ${e.localizedMessage}")
            break
        }
    }

    // 2. Chapter Phase
    if (selectedNovel != null) {
        println("\nFetching chapters for: ${selectedNovel.title}...")
        val chapters = selectedSource.getChapters(selectedNovel.url)

        if (chapters.isEmpty()) {
            println("No chapters found for this novel.")
            return@runBlocking
        }

        chapters.forEachIndexed { index, chapter ->
            println("${index + 1}. ${chapter.name}")
        }

        print("\nEnter chapter number to read: ")
        val chapterIndex = readln().toIntOrNull()?.minus(1)

        if (chapterIndex != null && chapterIndex in chapters.indices) {
            var currentChapter = chapters[chapterIndex]

            // 3. Continuous Reading Loop
            while (true) {
                val content = selectedSource.getChapterContent(currentChapter.url)

                println("\n--- ${content.title} ---")
                println("\n${content.content}\n")

                val next = selectedSource.getNextChapter(chapters, currentChapter)
                val prev = selectedSource.getPrevChapter(chapters, currentChapter)

                println("--- Navigation ---")
                if (prev != null) print("[P] Previous  ")
                if (next != null) print("[N] Next  ")
                println("[B] Back to Menu")

                print("\nChoice: ")
                when (readln().lowercase()) {
                    "n" -> if (next != null) currentChapter = next else println("Last chapter reached.")
                    "p" -> if (prev != null) currentChapter = prev else println("First chapter reached.")
                    "b" -> break
                    else -> println("Invalid choice.")
                }
            }
        }
    }
}