package com.ledgecred.ccsettleapp.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val vpa: String            = "",
    val digestHour: Int        = 22,
    val dailyCapPaise: Long    = 10_000_000L,
    val splitAboveCap: Boolean = true,
    val batteryOptIgnored: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.vpa, prefs.digestHour, prefs.dailyCapPaise, prefs.splitAboveCap
    ) { vpa, hour, cap, split ->
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        SettingsUiState(
            vpa               = vpa,
            digestHour        = hour,
            dailyCapPaise     = cap,
            splitAboveCap     = split,
            batteryOptIgnored = pm.isIgnoringBatteryOptimizations(app.packageName)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setVpa(v: String)               = viewModelScope.launch { prefs.setVpa(v) }
    fun setDigestHour(h: Int)           = viewModelScope.launch { prefs.setDigestHour(h) }
    fun setDailyCapPaise(p: Long)       = viewModelScope.launch { prefs.setDailyCapPaise(p) }
    fun setSplitAboveCap(s: Boolean)    = viewModelScope.launch { prefs.setSplitAboveCap(s) }
}
