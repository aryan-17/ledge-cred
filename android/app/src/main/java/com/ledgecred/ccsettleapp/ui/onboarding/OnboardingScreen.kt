package com.ledgecred.ccsettleapp.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ledgecred.ccsettleapp.R
import com.ledgecred.ccsettleapp.ui.theme.*

@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel = viewModel(),
    onComplete: () -> Unit
) {
    val state   = vm.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    // Google Sign-In launcher
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken ?: return@rememberLauncherForActivityResult
            vm.signInWithGoogle(idToken, onComplete)
        } catch (_: ApiException) {}
    }

    // SMS permission launcher
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.onSmsSmsPermissionGranted() }


    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Eyebrow
        Text("STEP ${state.currentStep + 1} OF 4",
            fontFamily = JetBrainsMono, fontSize = 10.sp,
            color = TextDisabled, letterSpacing = 1.2.sp)

        Spacer(Modifier.height(16.dp))

        val headlines = listOf(
            "Sign in to get started",
            "Allow SMS access",
            "Let it stay awake at 10 PM",
            "Enable autostart",
        )
        Text(headlines[state.currentStep],
            fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
            fontSize = 27.sp, color = TextPrimary, lineHeight = 32.sp)

        Spacer(Modifier.height(32.dp))

        // Permission rows
        val steps = listOf("Sign in with Google", "Read SMS", "Battery exemption", "Autostart")
        steps.forEachIndexed { i, label ->
            PermissionRow(
                label   = label,
                isDone  = i < state.currentStep,
                isActive = i == state.currentStep
            )
            Spacer(Modifier.height(12.dp))
        }

        // "WHAT WE'LL READ" panel
        if (state.currentStep == 1) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("WHAT WE'LL READ", fontFamily = JetBrainsMono, fontSize = 9.sp,
                    color = TextDisabled, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Only bank SMS alerts (debit/credit notifications). OTPs and personal messages are never read or stored.",
                    style = AppTypography.bodyMedium, color = TextLabel)
            }
        }

        Spacer(Modifier.weight(1f))

        if (state.error != null) {
            Text(state.error, style = AppTypography.bodySmall, color = Red)
            Spacer(Modifier.height(8.dp))
        }

        // CTA
        Button(
            onClick = {
                when (state.currentStep) {
                    0 -> signInLauncher.launch(googleClient.signInIntent)
                    1 -> smsLauncher.launch(Manifest.permission.READ_SMS)
                    2 -> {
                        context.startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ))
                        vm.onBatteryExemptionGranted()
                    }
                    3 -> { vm.onAutoStartDone(); onComplete() }
                }
            },
            enabled  = !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = AmberInk, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    when (state.currentStep) {
                        0    -> "Sign in with Google"
                        3    -> "Done"
                        else -> "Allow and continue"
                    },
                    color = AmberInk, fontFamily = InstrumentSans,
                    fontWeight = FontWeight.Bold, fontSize = 15.5.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onComplete) {
            Text("Skip — I'll settle manually",
                style = AppTypography.bodySmall, color = TextLabel)
        }
    }
}

@Composable
private fun PermissionRow(label: String, isDone: Boolean, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) SurfaceRaised else Surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isActive) AmberTintBorder else Border,
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp)
                .background(if (isDone) Green else if (isActive) Amber.copy(alpha = 0f) else TextDisabled.copy(alpha = 0f), CircleShape)
                .border(
                    1.5.dp,
                    when { isDone -> Green; isActive -> Amber; else -> TextDisabled },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) Text("✓", fontSize = 11.sp, color = GreenBg, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label,
            style = AppTypography.bodyMedium,
            color = when { isDone -> TextLabel; isActive -> TextPrimary; else -> TextDisabled }
        )
    }
}
