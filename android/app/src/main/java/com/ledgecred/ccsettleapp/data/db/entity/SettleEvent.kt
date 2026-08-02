package com.ledgecred.ccsettleapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settle_events")
data class SettleEvent(
    @PrimaryKey val id: String,
    val parentRef: String,              // e.g. CCS20260801
    val suffix: String?,                // A, B… for partial/split same day
    val status: String,                 // SettleStatus.name
    val requestedAmountPaise: Long,
    val pendingSnapshotPaise: Long,
    val createdAt: Long,                // epoch millis
    val clearedAt: Long? = null,
    val clearedAmountPaise: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
