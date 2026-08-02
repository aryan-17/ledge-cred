package com.ledgecred.ccsettleapp.data.db.dao

import androidx.room.*
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface SettleEventDao {

    @Query("SELECT * FROM settle_events WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SettleEvent>>

    @Query("SELECT * FROM settle_events WHERE status = 'AWAITING' AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveSettleEvent(): SettleEvent?

    @Query("SELECT * FROM settle_events WHERE status = 'AWAITING' AND deletedAt IS NULL")
    suspend fun getAwaitingEvents(): List<SettleEvent>

    @Query("SELECT * FROM settle_events WHERE updatedAt > :since")
    suspend fun modifiedSince(since: Long): List<SettleEvent>

    @Query("SELECT * FROM settle_events WHERE parentRef = :parentRef AND deletedAt IS NULL")
    suspend fun getEventsForDay(parentRef: String): List<SettleEvent>

    @Query("SELECT * FROM settle_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SettleEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg event: SettleEvent)
}
