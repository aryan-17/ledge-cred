package com.ledgecred.ccsettleapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledgecred.ccsettleapp.data.db.dao.SettleEventDao
import com.ledgecred.ccsettleapp.data.db.dao.TransactionDao
import com.ledgecred.ccsettleapp.data.db.dao.UserCardDao
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.data.db.entity.UserCard

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_cards (
                id TEXT NOT NULL PRIMARY KEY,
                bank TEXT NOT NULL,
                last4 TEXT NOT NULL,
                nickname TEXT
            )
        """)
    }
}

@Database(
    entities = [Transaction::class, SettleEvent::class, UserCard::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun settleEventDao(): SettleEventDao
    abstract fun userCardDao(): UserCardDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ccsettleapp.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
    }
}
