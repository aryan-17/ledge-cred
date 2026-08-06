package com.ledgecred.ccsettleapp.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.CardInfo
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val vpa: String                = "",
    val digestHour: Int            = 22,
    val dailyCapPaise: Long        = 10_000_000L,
    val splitAboveCap: Boolean     = true,
    val batteryOptIgnored: Boolean = false,
    val detectedCards: List<CardInfo> = emptyList(),
    val trackedCards: Set<String>  = emptySet()   // empty = track all cards
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val db    = AppDatabase.getInstance(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.vpa, prefs.digestHour, prefs.dailyCapPaise,
        prefs.splitAboveCap, prefs.trackedCards,
        db.transactionDao().observeDistinctCards()
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val vpa     = args[0] as String
        val hour    = args[1] as Int
        val cap     = args[2] as Long
        val split   = args[3] as Boolean
        val tracked = args[4] as Set<String>
        val cards   = args[5] as List<CardInfo>
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        SettingsUiState(
            vpa               = vpa,
            digestHour        = hour,
            dailyCapPaise     = cap,
            splitAboveCap     = split,
            batteryOptIgnored = pm.isIgnoringBatteryOptimizations(app.packageName),
            detectedCards     = cards,
            trackedCards      = tracked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setVpa(v: String)            = viewModelScope.launch { prefs.setVpa(v) }
    fun setDigestHour(h: Int)        = viewModelScope.launch { prefs.setDigestHour(h) }
    fun setDailyCapPaise(p: Long)    = viewModelScope.launch { prefs.setDailyCapPaise(p) }
    fun setSplitAboveCap(s: Boolean) = viewModelScope.launch { prefs.setSplitAboveCap(s) }
    fun logout()                     { FirebaseAuth.getInstance().signOut() }

    fun toggleCard(cardKey: String, enabled: Boolean) = viewModelScope.launch {
        val current = prefs.trackedCards.first().toMutableSet()
        if (enabled) current.add(cardKey) else current.remove(cardKey)
        prefs.setTrackedCards(current)
    }

    fun trackAllCards() = viewModelScope.launch {
        prefs.setTrackedCards(emptySet())
    }
}
