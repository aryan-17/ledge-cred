package com.ledgecred.ccsettleapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val amountPaise: Long,
    val type: String,                   // TransactionType.name
    val cardLast4: String?,
    val bank: String,
    val txnTime: Long,                  // epoch millis
    val smsTime: Long,                  // epoch millis — local only, never synced
    val rawSms: String,                 // local only — never synced
    val dedupeHash: String,
    val matchedSettleEventId: String?,
    val suggestedType: String?,
    val suggestedConfidence: Float?,
    val reviewed: Boolean = false,
    val settledAt: Long? = null,        // manually marked as paid — no SMS involved
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
