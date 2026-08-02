package com.ledgecred.ccsettleapp.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SettleRepository
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

data class HistoryUiState(
    val settledThisMonthPaise: Long = 0L,
    val streakDays: Int             = 0,
    val hasCarriedOver: Boolean     = false,
    val carriedOverPaise: Long      = 0L,
    val dailyAmounts: List<Long>    = emptyList(), // last 14 days
    val events: List<SettleEvent>   = emptyList()
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val db   = AppDatabase.getInstance(app)
    private val repo = SettleRepository(db, AppPreferences(app))

    val uiState: StateFlow<HistoryUiState> = db.settleEventDao().observeAll()
        .map { events ->
            val now         = System.currentTimeMillis()
            val monthStart  = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val settledMonth = events
                .filter { it.clearedAt != null && it.clearedAt >= monthStart }
                .sumOf { it.clearedAmountPaise ?: 0L }

            val partial = events.firstOrNull { it.status == "PARTIAL" }
            val carriedOver = partial?.let {
                it.requestedAmountPaise - (it.clearedAmountPaise ?: 0L)
            } ?: 0L

            // Streak: consecutive days with a CLEARED event ending today
            val clearedDates = events
                .filter { it.clearedAt != null && it.status in listOf("CLEARED", "MANUAL_MATCH") }
                .map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.clearedAt!!), ZoneId.systemDefault()) }
                .toSortedSet()
            var streak = 0
            var day    = LocalDate.now()
            while (clearedDates.contains(day)) { streak++; day = day.minusDays(1) }

            // Daily amounts (last 14 days)
            val dailyAmounts = (13 downTo 0).map { daysAgo ->
                val d = LocalDate.now().minusDays(daysAgo.toLong())
                events
                    .filter { it.clearedAt != null &&
                        LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.clearedAt), ZoneId.systemDefault()) == d }
                    .sumOf { it.clearedAmountPaise ?: 0L }
            }

            HistoryUiState(
                settledThisMonthPaise = settledMonth,
                streakDays            = streak,
                hasCarriedOver        = carriedOver > 0L,
                carriedOverPaise      = carriedOver,
                dailyAmounts          = dailyAmounts,
                events                = events.filter { it.deletedAt == null }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
