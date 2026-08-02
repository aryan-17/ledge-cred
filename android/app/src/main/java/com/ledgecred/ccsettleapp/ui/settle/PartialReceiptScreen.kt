package com.ledgecred.ccsettleapp.ui.settle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgecred.ccsettleapp.ui.home.formatIndianRupees
import com.ledgecred.ccsettleapp.ui.theme.*
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import kotlinx.coroutines.flow.*

class PartialReceiptViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val eventId = saved.get<String>("eventId") ?: error("eventId required")
    private val db      = AppDatabase.getInstance(app)

    data class ReceiptState(
        val settledPaise: Long   = 0L,
        val remainingPaise: Long = 0L,
        val ref: String          = "",
        val clearedInSeconds: Long = 0L
    )

    val receiptState: StateFlow<ReceiptState> = db.settleEventDao().observeAll()
        .map { events ->
            val event = events.firstOrNull { it.id == eventId }
                ?: return@map ReceiptState()
            val settled   = event.clearedAmountPaise ?: 0L
            val remaining = event.requestedAmountPaise - settled
            val ref       = event.parentRef + (event.suffix ?: "") + "-" +
                            if (event.suffix == null) "A" else event.suffix
            val secs      = if (event.clearedAt != null)
                (event.clearedAt - event.createdAt) / 1000 else 0L
            ReceiptState(settled, remaining, ref, secs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptState())
}

@Composable
fun PartialReceiptScreen(
    eventId: String,
    vm: PartialReceiptViewModel = viewModel(),
    onSendRemaining: (String) -> Unit,
    onDone: () -> Unit
) {
    val state = vm.receiptState.collectAsStateWithLifecycle().value
    val total = state.settledPaise + state.remainingPaise

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        // Green tick
        Box(Modifier.size(60.dp).background(GreenBg, RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 28.sp, color = Green, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        Text(formatIndianRupees(state.settledPaise) + " credited",
            fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Matched in ${state.clearedInSeconds}s. This was a partial settle.",
            style = AppTypography.bodyMedium, color = TextLabel)

        Spacer(Modifier.height(28.dp))

        // Split bar
        if (total > 0L) {
            val settledFrac = state.settledPaise.toFloat() / total
            Row(Modifier.fillMaxWidth().height(12.dp)) {
                Box(Modifier.weight(settledFrac).fillMaxHeight()
                    .background(Green, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
                Box(Modifier.weight(1f - settledFrac).fillMaxHeight()
                    .background(Amber, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("SETTLED", fontFamily = JetBrainsMono, fontSize = 9.sp,
                    color = Green, modifier = Modifier.weight(1f))
                Text("STILL PENDING", fontFamily = JetBrainsMono, fontSize = 9.sp,
                    color = Amber)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Detail card
        Column(
            Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailRow("Reference", state.ref)
            DetailRow("Type", "PARTIAL · ${if (total > 0) "${(state.settledPaise * 100 / total)}%" else "—"}")
        }

        Spacer(Modifier.weight(1f))

        // Send remaining CTA
        OutlinedButton(
            onClick  = { onSendRemaining(eventId) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
            border   = ButtonDefaults.outlinedButtonBorder
        ) {
            Text("Send the remaining ${formatIndianRupees(state.remainingPaise)}",
                fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done", style = AppTypography.bodyMedium, color = TextLabel)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AppTypography.bodySmall, color = TextLabel)
        Text(value, fontFamily = JetBrainsMono, fontSize = 11.sp, color = TextSecondary)
    }
}
