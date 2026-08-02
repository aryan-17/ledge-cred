package com.ledgecred.ccsettleapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ccsettleapp_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val LAST_SYNCED_AT    = longPreferencesKey("last_synced_at")
        val VPA               = stringPreferencesKey("vpa")
        val DIGEST_HOUR       = intPreferencesKey("digest_hour")         // default 22
        val DAILY_CAP_PAISE   = longPreferencesKey("daily_cap_paise")    // default ₹1L = 10_000_000
        val SPLIT_ABOVE_CAP   = booleanPreferencesKey("split_above_cap")
        val GEMINI_ENABLED    = booleanPreferencesKey("gemini_enabled")
        val DISABLED_CARDS    = stringSetPreferencesKey("disabled_cards") // set of "BANK·last4"
    }

    val lastSyncedAt: Flow<Long?>  = context.dataStore.data.map { it[LAST_SYNCED_AT] }
    val vpa: Flow<String>          = context.dataStore.data.map { it[VPA] ?: "" }
    val digestHour: Flow<Int>      = context.dataStore.data.map { it[DIGEST_HOUR] ?: 22 }
    val dailyCapPaise: Flow<Long>  = context.dataStore.data.map { it[DAILY_CAP_PAISE] ?: 10_000_000L }
    val splitAboveCap: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_ABOVE_CAP] ?: true }
    val geminiEnabled: Flow<Boolean> = context.dataStore.data.map { it[GEMINI_ENABLED] ?: true }

    suspend fun setLastSyncedAt(millis: Long) =
        context.dataStore.edit { it[LAST_SYNCED_AT] = millis }

    suspend fun setVpa(vpa: String) =
        context.dataStore.edit { it[VPA] = vpa }

    suspend fun setDailyCapPaise(paise: Long) =
        context.dataStore.edit { it[DAILY_CAP_PAISE] = paise }

    suspend fun setDigestHour(hour: Int) =
        context.dataStore.edit { it[DIGEST_HOUR] = hour }

    suspend fun setGeminiEnabled(enabled: Boolean) =
        context.dataStore.edit { it[GEMINI_ENABLED] = enabled }

    suspend fun setSplitAboveCap(split: Boolean) =
        context.dataStore.edit { it[SPLIT_ABOVE_CAP] = split }
}
