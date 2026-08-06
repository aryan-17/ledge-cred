package com.ledgecred.ccsettleapp.ui.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val transactions: StateFlow<List<Transaction>> = db.transactionDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun discard(id: String) = viewModelScope.launch {
        db.transactionDao().deleteById(id)
        try { ApiClient.get().deleteTransaction(id) } catch (_: Exception) {
            // best-effort — Room is already cleaned up
        }
    }
}
