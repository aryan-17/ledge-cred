package com.ledgecred.ccsettleapp.ui.waiting

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgecred.ccsettleapp.ui.theme.*

@Composable
fun WaitingScreen(
    eventId: String,
    vm: WaitingViewModel = viewModel(),
    onCleared: () -> Unit,
    onPartial: () -> Unit,
    onManualConfirm: () -> Unit
) {
    val state   = vm.waitingState.collectAsStateWithLifecycle().value
    val elapsed = vm.elapsedMillis.collectAsStateWithLifecycle().value

    LaunchedEffect(state) {
        when (state) {
            is WaitingState.Cleared -> onCleared()
            is WaitingState.Partial -> onPartial()
            else                    -> {}
        }
    }

    val showManual = elapsed > 5 * 60_000L // show manual option after 5 min

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "scale2"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        // Pulse target
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).scale(scale2)
                .border(2.dp, Green.copy(alpha = 0.35f), CircleShape))
            Box(Modifier.size(120.dp).scale(scale1)
                .border(2.dp, Green.copy(alpha = 0.6f), CircleShape))
            Box(Modifier.size(74.dp).background(GreenBg, CircleShape),
                contentAlignment = Alignment.Center) {
                Text("✉", fontSize = 30.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Text("Waiting for the credit SMS",
            fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("We'll detect the credit automatically when your bank sends the confirmation.",
            style = AppTypography.bodyMedium, color = TextLabel)

        Spacer(Modifier.height(32.dp))

        // 3-step checklist
        listOf(
            "Link generated" to true,
            "Handed off to UPI app" to true,
            "Awaiting credit SMS" to false
        ).forEach { (step, done) ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(Modifier.size(20.dp)
                    .background(if (done) Green else TextDisabled.copy(alpha = 0.2f), CircleShape)
                    .border(if (done) 0.dp else 1.5.dp, TextDisabled, CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (done) Text("✓", fontSize = 11.sp, color = GreenBg, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(step, style = AppTypography.bodyMedium,
                    color = if (done) TextSecondary else TextLabel)
            }
        }

        Spacer(Modifier.weight(1f))

        if (showManual) {
            TextButton(onClick = { vm.markManuallySettled(); onManualConfirm() }) {
                Text("No SMS after 5 min? Mark as settled manually",
                    style = AppTypography.bodySmall, color = TextLabel)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
