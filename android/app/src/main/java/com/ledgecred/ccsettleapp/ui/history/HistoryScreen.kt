package com.ledgecred.ccsettleapp.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.ui.home.formatIndianRupees
import com.ledgecred.ccsettleapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    vm: HistoryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state = vm.uiState.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onBack) { Text("← Back", color = TextLabel) }
        }

        // Stat cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard(
                    label = "SETTLED · ${currentMonth()}",
                    value = formatIndianRupees(state.settledThisMonthPaise),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (state.hasCarriedOver) {
                    StatCard("CARRIED OVER", formatIndianRupees(state.carriedOverPaise),
                        Amber, Modifier.weight(1f))
                } else {
                    StatCard("STREAK", "${state.streakDays}d", Green, Modifier.weight(1f))
                }
            }
        }

        // Sparkline
        item {
            val max = state.dailyAmounts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                state.dailyAmounts.forEachIndexed { i, amount ->
                    val isToday     = i == state.dailyAmounts.lastIndex
                    val heightFrac  = (amount.toFloat() / max).coerceIn(0.1f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFrac)
                            .background(
                                if (isToday) Amber else Track,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
            }
        }

        // Settle events list
        items(state.events) { event ->
            SettleEventRow(event)
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettleEventRow(event: SettleEvent) {
    val isPartial = event.status == "PARTIAL"
    val ref       = event.parentRef + (event.suffix ?: "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(Modifier.size(8.dp).background(
            when (event.status) {
                "CLEARED", "MANUAL_MATCH" -> Green
                "AWAITING"                -> Amber
                "PARTIAL"                 -> AmberDeep
                else                      -> Red
            }, CircleShape
        ))
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(ref, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, color = TextPrimary)
            Text(
                when (event.status) {
                    "CLEARED"      -> "cleared in ${clearedIn(event)}"
                    "MANUAL_MATCH" -> "marked manually"
                    "PARTIAL"      -> "partial — ${formatIndianRupees(event.clearedAmountPaise ?: 0L)} settled"
                    "AWAITING"     -> "awaiting credit SMS"
                    else           -> event.status.lowercase()
                },
                fontFamily = JetBrainsMono, fontSize = 10.sp, color = TextMeta
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatIndianRupees(event.clearedAmountPaise ?: event.requestedAmountPaise),
                fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp, color = if (isPartial) Green else TextPrimary
            )
            if (isPartial) {
                val remaining = event.requestedAmountPaise - (event.clearedAmountPaise ?: 0L)
                Text("${formatIndianRupees(remaining)} left",
                    fontFamily = JetBrainsMono, fontSize = 10.sp, color = Amber)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(Surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, fontFamily = JetBrainsMono, fontSize = 9.sp, color = TextDisabled, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
    }
}

private fun currentMonth() = SimpleDateFormat("MMM", Locale.getDefault()).format(Date()).uppercase()

private fun clearedIn(event: SettleEvent): String {
    if (event.clearedAt == null) return "—"
    val diff = event.clearedAt - event.createdAt
    return if (diff < 60_000) "${diff / 1000}s"
    else "${diff / 60_000}m ${(diff % 60_000) / 1000}s"
}
