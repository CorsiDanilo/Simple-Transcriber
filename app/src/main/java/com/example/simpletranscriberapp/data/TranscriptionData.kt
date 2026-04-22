package com.example.simpletranscriberapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transcriptions")
data class TranscriptionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val text: String
)

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranscriptionItem>>

    @Insert
    suspend fun insert(item: TranscriptionItem)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()
}

@Database(entities = [TranscriptionItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "watranscriber_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
