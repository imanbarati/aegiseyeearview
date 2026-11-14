package com.example.data.local

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

@Entity(tableName = "sos_broadcasts")
data class SosBroadcastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val callsign: String,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val emergencyMessage: String,
    val yellowPageEndpoint: String,
    val onlineSyncStatus: String,
    val onlineRecordUrl: String?,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "movement_breadcrumbs")
data class BreadcrumbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sequenceNumber: Int,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
    val bearingDegrees: Float,
    val distanceFromPreviousMeters: Double,
    val timestampMs: Long = System.currentTimeMillis(),
    val isTemporary: Boolean = true
)

@Dao
interface SosBroadcastDao {
    @Query("SELECT * FROM sos_broadcasts ORDER BY timestampMs DESC LIMIT 25")
    fun observeAllBroadcasts(): Flow<List<SosBroadcastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBroadcast(entity: SosBroadcastEntity): Long

    @Query("UPDATE sos_broadcasts SET onlineSyncStatus = :status, onlineRecordUrl = :recordUrl WHERE id = :id")
    suspend fun updateOnlineStatus(id: Long, status: String, recordUrl: String?)

    @Query("DELETE FROM sos_broadcasts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sos_broadcasts")
    suspend fun clearAll()
}

@Dao
interface BreadcrumbDao {
    @Query("SELECT * FROM movement_breadcrumbs ORDER BY timestampMs ASC")
    fun observeAllBreadcrumbs(): Flow<List<BreadcrumbEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumb(entity: BreadcrumbEntity): Long

    @Query("DELETE FROM movement_breadcrumbs WHERE isTemporary = 1 AND timestampMs < :cutoffTimestampMs")
    suspend fun deleteExpiredTemporary(cutoffTimestampMs: Long)

    @Query("DELETE FROM movement_breadcrumbs")
    suspend fun clearAll()
}

@Database(
    entities = [SosBroadcastEntity::class, BreadcrumbEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AegisLocalDatabase : RoomDatabase() {
    abstract fun sosBroadcastDao(): SosBroadcastDao
    abstract fun breadcrumbDao(): BreadcrumbDao

    companion object {
        @Volatile
        private var INSTANCE: AegisLocalDatabase? = null

        fun getInstance(context: Context): AegisLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AegisLocalDatabase::class.java,
                    "aegis_eye_tactical.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
