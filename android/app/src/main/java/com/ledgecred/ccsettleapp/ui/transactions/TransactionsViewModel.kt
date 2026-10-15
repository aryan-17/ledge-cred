package com.ledgecred.ccsettleapp.ui.transactions

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.ui.home.formatIndianRupees
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    /** Manually marks a transaction as settled (paid). Purely a local ledger entry —
     *  no UPI intent, no SMS matching. Propagates to the backend on the next periodic sync. */
    fun settle(id: String) = viewModelScope.launch {
        db.transactionDao().setSettledAt(id, System.currentTimeMillis(), System.currentTimeMillis())
    }

    fun unsettle(id: String) = viewModelScope.launch {
        db.transactionDao().setSettledAt(id, null, System.currentTimeMillis())
    }

    fun markLent(id: String, name: String) = viewModelScope.launch {
        db.transactionDao().setLentTo(id, name, System.currentTimeMillis())
    }

    fun clearLent(id: String) = viewModelScope.launch {
        db.transactionDao().setLentTo(id, null, System.currentTimeMillis())
    }

    fun buildShareIntent(tx: Transaction): Intent {
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(tx.txnTime))
        val amount = formatIndianRupees(tx.amountPaise)
        val card = tx.cardLast4?.let { " (${tx.bank} ··$it)" } ?: " (${tx.bank})"
        val text = "Hey ${tx.lentTo}, I paid $amount on $date$card.\nPlease settle when you can!"
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
    }

    fun buildShareIntentFallback(tx: Transaction): Intent {
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(tx.txnTime))
        val amount = formatIndianRupees(tx.amountPaise)
        val card = tx.cardLast4?.let { " (${tx.bank} ··$it)" } ?: " (${tx.bank})"
        val text = "Hey ${tx.lentTo}, I paid $amount on $date$card.\nPlease settle when you can!"
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share receipt"
        )
    }
}
