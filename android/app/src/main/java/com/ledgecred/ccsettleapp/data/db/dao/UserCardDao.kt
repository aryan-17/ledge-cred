package com.ledgecred.ccsettleapp.data.db.dao

import androidx.room.*
import com.ledgecred.ccsettleapp.data.db.entity.UserCard
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCardDao {
    @Query("SELECT * FROM user_cards ORDER BY bank, last4")
    fun observeAll(): Flow<List<UserCard>>

    @Query("SELECT * FROM user_cards ORDER BY bank, last4")
    suspend fun getAll(): List<UserCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg card: UserCard)

    @Query("DELETE FROM user_cards WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_cards")
    suspend fun deleteAll()
}
