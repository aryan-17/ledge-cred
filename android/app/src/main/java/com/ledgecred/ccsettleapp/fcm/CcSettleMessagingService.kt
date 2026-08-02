package com.ledgecred.ccsettleapp.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.dto.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CcSettleMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.get().updateFcmToken(mapOf("token" to token))
            } catch (_: Exception) {
                // Token will be re-registered on next login
            }
        }
    }
}
