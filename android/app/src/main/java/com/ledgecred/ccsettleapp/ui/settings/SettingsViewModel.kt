package com.ledgecred.ccsettleapp.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.ApiService
import com.ledgecred.ccsettleapp.data.api.dto.AddCardRequest
import com.ledgecred.ccsettleapp.data.repository.SyncRepository
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.UserCard
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val vpa: String                   = "",
    val digestHour: Int               = 22,
    val splitAboveCap: Boolean        = true,
    val batteryOptIgnored: Boolean    = false,
    val notificationAccessGranted: Boolean = false,
    val cards: List<UserCard>         = emptyList()
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val db    = AppDatabase.getInstance(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.vpa, prefs.splitAboveCap,
        db.userCardDao().observeAll()
    ) { vpa, split, cards ->
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notifListeners = Settings.Secure.getString(app.contentResolver, "enabled_notification_listeners") ?: ""
        val notifGranted = notifListeners.contains(ComponentName(app, "com.ledgecred.ccsettleapp.notification.NotificationCreditListener").flattenToString())
        SettingsUiState(
            vpa                        = vpa,
            splitAboveCap              = split,
            batteryOptIgnored          = pm.isIgnoringBatteryOptimizations(app.packageName),
            notificationAccessGranted  = notifGranted,
            cards                      = cards
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init { syncCards() }

    fun setVpa(v: String)            = viewModelScope.launch { prefs.setVpa(v) }
    fun setSplitAboveCap(s: Boolean) = viewModelScope.launch { prefs.setSplitAboveCap(s) }
    fun logout()                     { FirebaseAuth.getInstance().signOut() }

    /** Wipes cloud DB for this user, then pushes all local data — use after switching cloud DB. */
    fun forceSyncAll() = viewModelScope.launch {
        try {
            ApiClient.get().resetCloudData()  // clear cloud
        } catch (_: Exception) {}
        prefs.clearLastSyncedAt()             // force full push
        try {
            SyncRepository(db, ApiClient.get(), prefs).sync()
        } catch (_: Exception) {}
    }

    fun addCard(bank: String, last4: String, nickname: String?, type: String = "card") = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        val card = UserCard(id = id, bank = bank.trim(), last4 = last4.trim(), nickname = nickname?.trim()?.ifBlank { null }, type = type)
        db.userCardDao().upsert(card)
        // Reset scan window so next open re-scans last 7 days — picks up SMS missed before card was tracked
        prefs.setLastInboxReadAt(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
        try { ApiClient.get().addCard(AddCardRequest(bank = card.bank, last4 = card.last4, nickname = card.nickname, type = card.type)) } catch (_: Exception) {}
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
                db.userCardDao().upsert(UserCard(id = it.id, bank = it.bank, last4 = it.last4, nickname = it.nickname, type = it.type))
            }
        } catch (_: Exception) {}
    }
}
