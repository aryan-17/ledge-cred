package com.ledgecred.ccsettleapp.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.dto.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OnboardingUiState(
    val currentStep: Int       = 0,
    val isLoading: Boolean     = false,
    val error: String?         = null,
    val smsGranted: Boolean    = false,
    val batteryGranted: Boolean = false
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onSmsSmsPermissionGranted() {
        _state.value = _state.value.copy(smsGranted = true, currentStep = 1)
    }

    fun onBatteryExemptionGranted() {
        _state.value = _state.value.copy(batteryGranted = true, currentStep = 2)
    }

    fun onAutoStartDone() {
        _state.value = _state.value.copy(currentStep = 3)
    }

    fun signInWithGoogle(idToken: String, onComplete: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).await()

            // Register with backend
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            ApiClient.get().register(RegisterRequest(fcmToken))

            _state.value = _state.value.copy(isLoading = false)
            onComplete()
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message)
        }
    }
}
