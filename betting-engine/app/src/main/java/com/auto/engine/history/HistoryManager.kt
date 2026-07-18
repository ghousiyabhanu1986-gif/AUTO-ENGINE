package com.auto.engine.history

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    suspend fun getAll(): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE title LIKE :q OR url LIKE :q ORDER BY visitedAt DESC")
    suspend fun search(q: String): List<HistoryEntry>

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var INSTANCE: HistoryDatabase? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, "history.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

class HistoryManager(context: Context) {
    private val dao = HistoryDatabase.get(context).historyDao()

    suspend fun addEntry(title: String, url: String) = withContext(Dispatchers.IO) {
        dao.insert(HistoryEntry(title = title, url = url))
    }

    suspend fun getAll(): List<HistoryEntry> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun search(query: String): List<HistoryEntry> = withContext(Dispatchers.IO) {
        dao.search("%$query%")
    }

    suspend fun delete(entry: HistoryEntry) = withContext(Dispatchers.IO) { dao.delete(entry) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clearAll() }
}
