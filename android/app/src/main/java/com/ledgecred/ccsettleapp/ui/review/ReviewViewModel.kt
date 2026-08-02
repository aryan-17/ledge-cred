package com.ledgecred.ccsettleapp.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val queue: StateFlow<List<Transaction>> = db.transactionDao()
        .observeUnreviewed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ignore(id: String) = update(id, "UNPARSED", reviewed = true)
    fun markAsRefund(id: String) = update(id, "REFUND")
    fun markAsDebit(id: String)  = update(id, "DEBIT")

    fun acceptSuggestion(tx: Transaction) = update(
        id   = tx.id,
        type = tx.suggestedType ?: tx.type
    )

    private fun update(id: String, type: String, reviewed: Boolean = true) =
        viewModelScope.launch {
            val tx = db.transactionDao().findById(id) ?: return@launch
            db.transactionDao().upsert(
                tx.copy(type = type, reviewed = reviewed,
                    updatedAt = System.currentTimeMillis())
            )
        }
}
