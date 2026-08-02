package com.ledgecred.ccsettleapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ledgecred.ccsettleapp.data.db.dao.SettleEventDao
import com.ledgecred.ccsettleapp.data.db.dao.TransactionDao
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.db.entity.Transaction

@Database(
    entities = [Transaction::class, SettleEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun settleEventDao(): SettleEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ccsettleapp.db"
                ).build().also { INSTANCE = it }
            }
    }
}
