package com.ledgecred.ccsettleapp.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.dto.AddCardRequest
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.UserCard
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val vpa: String                = "",
    val digestHour: Int            = 22,
    val splitAboveCap: Boolean     = true,
    val batteryOptIgnored: Boolean = false,
    val cards: List<UserCard>      = emptyList()
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val db    = AppDatabase.getInstance(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.vpa, prefs.splitAboveCap,
        db.userCardDao().observeAll()
    ) { vpa, split, cards ->
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        SettingsUiState(
            vpa               = vpa,
            splitAboveCap     = split,
            batteryOptIgnored = pm.isIgnoringBatteryOptimizations(app.packageName),
            cards             = cards
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init { syncCards() }

    fun setVpa(v: String)            = viewModelScope.launch { prefs.setVpa(v) }
    fun setSplitAboveCap(s: Boolean) = viewModelScope.launch { prefs.setSplitAboveCap(s) }
    fun logout()                     { FirebaseAuth.getInstance().signOut() }

    fun addCard(bank: String, last4: String, nickname: String?) = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        val card = UserCard(id = id, bank = bank.trim(), last4 = last4.trim(), nickname = nickname?.trim()?.ifBlank { null })
        db.userCardDao().upsert(card)
        try { ApiClient.get().addCard(AddCardRequest(bank = card.bank, last4 = card.last4, nickname = card.nickname)) } catch (_: Exception) {}
    }

    fun removeCard(card: UserCard) = viewModelScope.launch {
        db.userCardDao().deleteById(card.id)
        try { ApiClient.get().deleteCard(card.id) } catch (_: Exception) {}
    }

    // Fetch from backend only when Room is empty (first login / fresh install)
    fun syncCards() = viewModelScope.launch {
        try {
            val local = db.userCardDao().getAll()
            if (local.isNotEmpty()) return@launch   // Room has data — use cache
            val remote = ApiClient.get().getCards().cards
            remote.forEach {
                db.userCardDao().upsert(UserCard(id = it.id, bank = it.bank, last4 = it.last4, nickname = it.nickname))
            }
        } catch (_: Exception) {}
    }
}
