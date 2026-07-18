package com.auto.engine.bookmarks

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "Default",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE title LIKE :q OR url LIKE :q ORDER BY createdAt DESC")
    suspend fun search(q: String): List<Bookmark>

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}

@Database(entities = [Bookmark::class], version = 1, exportSchema = false)
abstract class BookmarkDatabase : RoomDatabase() {
    abstract fun dao(): BookmarkDao

    companion object {
        @Volatile private var INSTANCE: BookmarkDatabase? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BookmarkDatabase::class.java, "bookmarks.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

class BookmarkManager(context: Context) {
    private val dao = BookmarkDatabase.get(context).dao()

    suspend fun addBookmark(title: String, url: String, folder: String = "Default") =
        withContext(Dispatchers.IO) { dao.insert(Bookmark(title = title, url = url, folder = folder)) }

    suspend fun getAll(): List<Bookmark> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun search(query: String): List<Bookmark> =
        withContext(Dispatchers.IO) { dao.search("%$query%") }

    suspend fun delete(bookmark: Bookmark) = withContext(Dispatchers.IO) { dao.delete(bookmark) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clearAll() }
}
