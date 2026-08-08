package com.ledgecred.ccsettleapp.data.api.dto

data class SyncTransactionDto(
    val id: String,
    val amountPaise: Long,
    val type: String,
    val cardLast4: String?,
    val bank: String,
    val txnTime: String,
    val dedupeHash: String,
    val matchedSettleEventId: String?,
    val reviewed: Boolean,
    val settledAt: String?,
    val updatedAt: String,
    val deletedAt: String?
)

data class SyncSettleEventDto(
    val id: String,
    val parentRef: String,
    val suffix: String?,
    val status: String,
    val requestedAmountPaise: Long,
    val pendingSnapshotPaise: Long,
    val createdAt: String,
    val clearedAt: String?,
    val clearedAmountPaise: Long?,
    val updatedAt: String,
    val deletedAt: String?
)

data class SyncRequest(
    val lastSyncedAt: String?,
    val transactions: List<SyncTransactionDto>,
    val settleEvents: List<SyncSettleEventDto>
)

data class SyncResponse(
    val syncedAt: String,
    val transactions: List<SyncTransactionDto>,
    val settleEvents: List<SyncSettleEventDto>
)

data class RegisterRequest(val fcmToken: String)
data class FcmNotifyRequest(val title: String, val body: String)

data class UserCardDto(val id: String, val bank: String, val last4: String, val nickname: String?)
data class AddCardRequest(val bank: String, val last4: String, val nickname: String? = null)
data class CardsResponse(val cards: List<UserCardDto>)
