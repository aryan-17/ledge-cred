package com.ledgecred.ccsettleapp.ui.waiting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SettleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class WaitingState {
    object Waiting : WaitingState()
    object Cleared : WaitingState()
    data class Partial(val clearedPaise: Long, val remainingPaise: Long) : WaitingState()
}

class WaitingViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {

    private val eventId = saved.get<String>("eventId") ?: error("eventId required")
    private val db      = AppDatabase.getInstance(app)
    private val repo    = SettleRepository(db, AppPreferences(app))

    val waitingState: StateFlow<WaitingState> = db.settleEventDao().observeAll()
        .map { events ->
            val event = events.firstOrNull { it.id == eventId }
                ?: return@map WaitingState.Waiting
            when (event.status) {
                "CLEARED"      -> WaitingState.Cleared
                "MANUAL_MATCH" -> WaitingState.Cleared
                "PARTIAL"      -> {
                    val cleared   = event.clearedAmountPaise ?: 0L
                    val remaining = event.requestedAmountPaise - cleared
                    WaitingState.Partial(cleared, remaining)
                }
                else -> WaitingState.Waiting
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaitingState.Waiting)

    val elapsedMillis: StateFlow<Long> = flow {
        val start = System.currentTimeMillis()
        while (true) {
            emit(System.currentTimeMillis() - start)
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun markManuallySettled() = viewModelScope.launch {
        repo.markManuallySettled(eventId)
    }
}
