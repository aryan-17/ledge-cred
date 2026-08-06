package com.ledgecred.ccsettleapp.ui.settle

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.ledgecred.ccsettleapp.ui.home.formatIndianRupees
import com.ledgecred.ccsettleapp.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettleScreen(
    eventId: String,
    vm: SettleViewModel = viewModel(),
    onPaid: () -> Unit,
    onBack: () -> Unit
) {
    val state   = vm.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 26.dp, vertical = 24.dp)
    ) {
        // Back
        TextButton(onClick = onBack) {
            Text("← Back", color = TextLabel, fontFamily = InstrumentSans)
        }

        Spacer(Modifier.height(24.dp))

        // Hero amount
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = formatIndianRupees(state.draftPaise),
                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                fontSize   = 42.sp, color = AmberBright
            )
            Text(
                text  = "of ${formatIndianRupees(state.pendingPaise)} pending",
                style = AppTypography.bodySmall, color = TextLabel
            )
        }

        Spacer(Modifier.height(24.dp))

        // Slider
        Slider(
            value         = state.draftPaise.toFloat(),
            onValueChange = { vm.setDraft(it.toLong()) },
            valueRange    = 0f..state.pendingPaise.toFloat().coerceAtLeast(1f),
            colors        = SliderDefaults.colors(thumbColor = AmberBright, activeTrackColor = Amber),
            modifier      = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Quick chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("¼" to { vm.quarter() }, "½" to { vm.half() },
                   "FULL" to { vm.full() }).forEach { (label, action) ->
                OutlinedButton(
                    onClick = action,
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                    modifier = Modifier.weight(if (label == "FULL") 1.5f else 1f)
                ) { Text(label, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Ledger card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LedgerRow("Pending now",   formatIndianRupees(state.pendingPaise), TextSecondary)
            Divider(color = Divider)
            LedgerRow("You send",      "− ${formatIndianRupees(state.draftPaise)}", Amber)
            Divider(color = Divider)
            LedgerRow(
                label = "Stays pending",
                value = formatIndianRupees(state.staysPendingPaise),
                valueColor = AmberDeep,
                modifier = Modifier
                    .background(AmberTintBg, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }

        if (state.exceedsCap) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Amount exceeds daily UPI cap. Tap pay — the app will split into multiple transfers.",
                style = AppTypography.bodySmall, color = AmberDeep,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmberTintBg, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Pay CTA
        Button(
            onClick = {
                scope.launch {
                    vm.onPayTapped()
                    try {
                        context.startActivity(
                            Intent.createChooser(vm.buildUpiIntent(), "Pay with UPI")
                        )
                    } catch (_: ActivityNotFoundException) {}
                    onPaid()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            Text("Pay ${formatIndianRupees(state.draftPaise)} →",
                color = AmberInk, fontFamily = InstrumentSans,
                fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
        }
    }
}

@Composable
private fun LedgerRow(
    label: String, value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AppTypography.bodyMedium, color = TextLabel)
        Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, color = valueColor)
    }
}
