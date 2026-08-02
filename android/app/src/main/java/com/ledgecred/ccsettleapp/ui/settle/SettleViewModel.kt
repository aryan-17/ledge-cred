package com.ledgecred.ccsettleapp.ui.settle

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SettleRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettleUiState(
    val event: SettleEvent?  = null,
    val pendingPaise: Long   = 0L,
    val draftPaise: Long     = 0L,
    val dailyCapPaise: Long  = 10_000_000L,
    val vpa: String          = ""
) {
    val staysPendingPaise get() = (pendingPaise - draftPaise).coerceAtLeast(0L)
    val exceedsCap        get() = draftPaise > dailyCapPaise
}

class SettleViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {

    private val eventId = saved.get<String>("eventId") ?: error("eventId required")
    private val db      = AppDatabase.getInstance(app)
    private val prefs   = AppPreferences(app)
    private val repo    = SettleRepository(db, prefs)

    private val _draft  = MutableStateFlow(0L)

    val uiState: StateFlow<SettleUiState> = combine(
        db.settleEventDao().observeAll(),
        repo.observePendingPaise(),
        prefs.dailyCapPaise,
        prefs.vpa,
        _draft
    ) { events, pending, cap, vpa, draft ->
        val event = events.firstOrNull { it.id == eventId }
        val effective = if (draft == 0L && event != null) event.requestedAmountPaise else draft
        SettleUiState(event = event, pendingPaise = pending,
            draftPaise = effective, dailyCapPaise = cap, vpa = vpa)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleUiState())

    fun setDraft(paise: Long) { _draft.value = paise.coerceIn(0L, uiState.value.pendingPaise) }
    fun quarter() = setDraft(uiState.value.pendingPaise / 4)
    fun half()    = setDraft(uiState.value.pendingPaise / 2)
    fun full()    = setDraft(uiState.value.pendingPaise)

    fun buildUpiIntent(): Intent {
        val state  = uiState.value
        val event  = state.event ?: error("No active event")
        val ref    = event.parentRef + (event.suffix ?: "")
        val amount = "%.2f".format(state.draftPaise / 100.0)
        val uri    = "upi://pay?pa=${state.vpa}&pn=Self&am=$amount&cu=INR&tn=CC+settle&tr=$ref"
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    }

    fun onPayTapped() = viewModelScope.launch {
        // Update event status from AWAITING → still AWAITING but with requested amount updated
        val event = uiState.value.event ?: return@launch
        db.settleEventDao().upsert(
            event.copy(requestedAmountPaise = _draft.value.coerceAtLeast(event.requestedAmountPaise),
                updatedAt = System.currentTimeMillis())
        )
    }
}
