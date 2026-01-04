package com.atlas.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getNovel(id: String): Novel?

    @Query("SELECT * FROM novels WHERE id = :id")
    fun getNovelFlow(id: String): Flow<Novel?>

    @Query("SELECT * FROM novels ORDER BY lastReadTime DESC")
    fun getLibraryNovels(): Flow<List<Novel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: Novel)

    @Update
    suspend fun updateNovel(novel: Novel)

    @Query("DELETE FROM novels WHERE id = :novelId")
    suspend fun deleteNovel(novelId: String)

    @Query("DELETE FROM novels WHERE category = 'None' AND lastReadTime = 0")
    suspend fun clearUnusedPreviews()

    @Query("SELECT * FROM novels WHERE title LIKE '%' || :query || '%' AND category != 'None'")
    suspend fun searchLibrary(query: String): List<Novel>

    @Transaction
    suspend fun insertNovelWithChapters(novel: Novel, chapters: List<Chapter>) {
        insertNovel(novel)
        insertChapters(chapters)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapters(chapters: List<Chapter>)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY `index` ASC")
    fun getChaptersForNovel(novelId: String): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND `index` = :index LIMIT 1")
    suspend fun getChapter(novelId: String, index: Int): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChapter(chapter: Chapter)

    @Query("UPDATE chapters SET body = :content WHERE id = :chapterId")
    suspend fun updateChapterBody(chapterId: Long, content: String)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersForNovel(novelId: String)
}

@Database(entities = [Novel::class, Chapter::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atlas_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true) // TODO: remove this
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}