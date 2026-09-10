package com.anomalyzed.simpletranscriber.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transcriptions")
data class TranscriptionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val text: String,
    val summary: String? = null,
    val isSummaryOnly: Boolean = false,
    val engineMode: String? = null,
    val modelName: String? = null
)

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranscriptionItem>>

    @Insert
    suspend fun insert(item: TranscriptionItem): Long

    @Query("UPDATE transcriptions SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: Int, summary: String)

    @Query("UPDATE transcriptions SET summary = :summary WHERE timestamp = :timestamp")
    suspend fun updateSummaryByTimestamp(timestamp: Long, summary: String)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM transcriptions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Int>)
}

@Database(entities = [TranscriptionItem::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN engineMode TEXT")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN modelName TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN summary TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transcriptions ADD COLUMN isSummaryOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "watranscriber_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
