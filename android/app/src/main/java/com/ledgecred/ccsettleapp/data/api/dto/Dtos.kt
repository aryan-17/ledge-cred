package com.ledgecred.ccsettleapp.data.api.dto

data class SyncTransactionDto(
    val id: String,
    val amountPaise: Long,
    val type: String,
    val cardLast4: String?,
    val bank: String,
    val txnTime: String,            // ISO 8601
    val dedupeHash: String,
    val matchedSettleEventId: String?,
    val suggestedType: String?,
    val suggestedConfidence: Float?,
    val reviewed: Boolean,
    val updatedAt: String,          // ISO 8601
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

data class ClassifyMessageDto(val id: String, val text: String)
data class ClassifyResultDto(val id: String, val suggestedType: String, val confidence: Float)
data class ClassifyRequest(val messages: List<ClassifyMessageDto>)
data class ClassifyResponse(val results: List<ClassifyResultDto>)

data class RegisterRequest(val fcmToken: String)
data class FcmNotifyRequest(val title: String, val body: String)
