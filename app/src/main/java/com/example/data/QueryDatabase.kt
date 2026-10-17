package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "query_records")
data class QueryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val carrierId: String,
    val subscriberLabel: String,
    val balanceRial: Double,
    val loanDebtRial: Double,
    val lineStatus: String,
    val expiryDate: String,
    val activePackageName: String,
    val remainingDataMb: Int,
    val totalDataMb: Int,
    val remainingMinutes: Int,
    val totalMinutes: Int,
    val remainingSms: Int,
    val totalSms: Int,
    val verificationRef: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Dao
interface QueryRecordDao {
    @Query("SELECT * FROM query_records ORDER BY timestampMillis DESC")
    fun getAllRecords(): Flow<List<QueryRecordEntity>>

    @Query("SELECT * FROM query_records WHERE isFavorite = 1 ORDER BY timestampMillis DESC")
    fun getFavoriteRecords(): Flow<List<QueryRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: QueryRecordEntity): Long

    @Query("UPDATE query_records SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM query_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM query_records")
    suspend fun clearAllRecords()
}

@Database(entities = [QueryRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queryRecordDao(): QueryRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yemen_telecom_balance.db"
                ).fallbackToDestructiveMigration(false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
