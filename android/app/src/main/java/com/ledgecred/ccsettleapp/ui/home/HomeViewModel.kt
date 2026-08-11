package com.ledgecred.ccsettleapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SettleRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val pendingPaise: Long       = 0L,
    val dailyCapPaise: Long      = 10_000_000L,
    val settledTodayPaise: Long  = 0L,
    val todaySpendPaise: Long    = 0L,
    val lastSettleAt: Long?      = null,
    val unreviewedCount: Int     = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val activeEventId: String?   = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db    = AppDatabase.getInstance(app)
    private val prefs = AppPreferences(app)
    private val repo  = SettleRepository(db, prefs)

    private val todayStart: Long
        get() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val uiState: StateFlow<HomeUiState> = repo.observePendingPaise()
        .combine(prefs.dailyCapPaise) { pending, cap -> pending to cap }
        .combine(db.transactionDao().observeUnreviewedCount()) { (pending, cap), count -> Triple(pending, cap, count) }
        .combine(db.transactionDao().observeRecent(5)) { (pending, cap, count), recent -> Pair(Triple(pending, cap, count), recent) }
        .combine(db.settleEventDao().observeAll()) { (triple, recent), events ->
            val (pending, cap, unreviewed) = triple
            val todaySpend   = recent.filter { it.type == "DEBIT" && it.txnTime > todayStart && it.deletedAt == null }
                                   .sumOf { it.amountPaise }
            val settledToday = events
                .filter { it.clearedAt != null && it.clearedAt >= todayStart }
                .sumOf { it.clearedAmountPaise ?: 0L }
            val lastSettle   = events.filter { it.clearedAt != null }.maxOfOrNull { it.clearedAt!! }
            val activeId     = events.firstOrNull { it.status == "AWAITING" }?.id

            HomeUiState(
                pendingPaise       = pending,
                dailyCapPaise      = cap,
                settledTodayPaise  = settledToday,
                todaySpendPaise    = todaySpend,
                lastSettleAt       = lastSettle,
                unreviewedCount    = unreviewed,
                recentTransactions = recent,
                activeEventId      = activeId
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    suspend fun createSettleEvent(requestedPaise: Long): String =
        repo.createSettleEvent(requestedPaise).id
}
