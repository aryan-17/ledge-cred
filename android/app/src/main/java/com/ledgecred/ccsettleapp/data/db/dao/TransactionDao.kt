package com.ledgecred.ccsettleapp.data.db.dao

import androidx.room.*
import com.ledgecred.ccsettleapp.data.db.entity.CardInfo
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY txnTime DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'UNPARSED' AND reviewed = 0 AND deletedAt IS NULL ORDER BY txnTime DESC")
    fun observeUnreviewed(): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'UNPARSED' AND reviewed = 0 AND deletedAt IS NULL")
    fun observeUnreviewedCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE updatedAt > :since")
    suspend fun modifiedSince(since: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE dedupeHash = :hash LIMIT 1")
    suspend fun findByDedupeHash(hash: String): Transaction?

    @Query("SELECT dedupeHash FROM transactions")
    suspend fun getAllDedupeHashes(): List<String>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Transaction?

    @Query("""
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'DEBIT' AND txnTime > :dayStart AND deletedAt IS NULL
    """)
    fun observeTodaySpend(dayStart: Long): Flow<Long>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY txnTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<Transaction>>

    @Query("SELECT DISTINCT bank, cardLast4 FROM transactions WHERE cardLast4 IS NOT NULL AND type = 'DEBIT' AND deletedAt IS NULL")
    fun observeDistinctCards(): Flow<List<CardInfo>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE transactions SET settledAt = :settledAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSettledAt(id: String, settledAt: Long?, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg tx: Transaction)
}
