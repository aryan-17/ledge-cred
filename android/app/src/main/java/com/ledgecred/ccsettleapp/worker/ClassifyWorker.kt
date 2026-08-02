package com.ledgecred.ccsettleapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.dto.ClassifyMessageDto
import com.ledgecred.ccsettleapp.data.api.dto.ClassifyRequest
import com.ledgecred.ccsettleapp.data.db.AppDatabase

class ClassifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val db       = AppDatabase.getInstance(applicationContext)
            val pending  = db.transactionDao().getUnparsedWithoutSuggestion()
            if (pending.isEmpty()) return Result.success()

            val messages = pending.map { ClassifyMessageDto(id = it.id, text = it.rawSms) }
            val response = ApiClient.get().classify(ClassifyRequest(messages))

            response.results.forEach { result ->
                val tx = db.transactionDao().findById(result.id) ?: return@forEach
                db.transactionDao().upsert(
                    tx.copy(
                        suggestedType       = result.suggestedType,
                        suggestedConfidence = result.confidence,
                        updatedAt           = System.currentTimeMillis()
                    )
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
