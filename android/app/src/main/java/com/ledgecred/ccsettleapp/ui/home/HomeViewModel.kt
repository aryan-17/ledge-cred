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

    private val _state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observePendingPaise(),
                prefs.dailyCapPaise,
                db.transactionDao().observeUnreviewedCount(),
                db.transactionDao().observeRecent(5),
                combine(
                    db.settleEventDao().observeAll(),
                    db.transactionDao().observeTodaySpend(todayStart)
                ) { events, todaySpend -> Pair(events, todaySpend) }
            ) { values ->
                val pending    = values[0] as Long
                val cap        = values[1] as Long
                val unreviewed = values[2] as Int
                @Suppress("UNCHECKED_CAST")
                val recent     = values[3] as List<Transaction>
                @Suppress("UNCHECKED_CAST")
                val eventsPair  = values[4] as Pair<List<com.ledgecred.ccsettleapp.data.db.entity.SettleEvent>, Long>
                val (events, todaySpend) = eventsPair

                val settledToday = events
                    .filter { it.clearedAt != null && it.clearedAt >= todayStart }
                    .sumOf { it.clearedAmountPaise ?: 0L }
                val lastSettle  = events.filter { it.clearedAt != null }.maxOfOrNull { it.clearedAt!! }
                val activeId    = events.firstOrNull { it.status == "AWAITING" }?.id

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
            }.collect { _state.value = it }
        }
    }

    suspend fun createSettleEvent(requestedPaise: Long): String =
        repo.createSettleEvent(requestedPaise).id
}
