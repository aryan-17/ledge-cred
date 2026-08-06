package com.ledgecred.ccsettleapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    vm: HomeViewModel = viewModel(),
    onSettleTap: (String) -> Unit,
    onReviewTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onSeeAllTap: () -> Unit = {}
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            BottomTabBar(
                unreviewedCount = state.unreviewedCount,
                onHomeTap       = {},
                onReviewTap     = onReviewTap,
                onHistoryTap    = onHistoryTap,
                onMoreTap       = {}
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CC Settle",
                            fontFamily = InstrumentSans, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = TextPrimary)
                        Text("listening · ${state.recentTransactions.count { it.cardLast4 != null }.coerceAtLeast(1)} cards",
                            fontFamily = JetBrainsMono, fontSize = 11.sp, color = TextLabel)
                    }
                    IconButton(onClick = onSettingsTap,
                        modifier = Modifier.size(34.dp).background(Surface, CircleShape)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings",
                            tint = TextLabel, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Gauge
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GaugeComponent(
                        pendingPaise      = state.pendingPaise,
                        dailyCapPaise     = state.dailyCapPaise,
                        settledTodayPaise = state.settledTodayPaise
                    )
                }
            }

            // Stat cards row
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatCard("TODAY", formatIndianRupees(state.todaySpendPaise), TextSecondary, Modifier.weight(1f))
                    StatCard("LAST SETTLE",
                        state.lastSettleAt?.let { relativeTime(it) } ?: "—",
                        Green, Modifier.weight(1f))
                    StatCard("REVIEW", state.unreviewedCount.toString(),
                        if (state.unreviewedCount > 0) AmberDeep else TextLabel,
                        Modifier.weight(1f))
                }
            }

            // Primary CTA
            item {
                val label = if (state.settledTodayPaise > 0L)
                    "Settle remaining ${formatIndianRupees(state.pendingPaise)}"
                else "Settle ${formatIndianRupees(state.pendingPaise)} now"

                Button(
                    onClick = {
                        if (state.pendingPaise > 0L) {
                            scope.launch {
                                val id = vm.createSettleEvent(state.pendingPaise)
                                onSettleTap(id)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber)
                ) {
                    Text(label, color = AmberInk,
                        fontFamily = InstrumentSans, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                }
            }

            // Recent activity header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent activity", Modifier.weight(1f),
                        fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp, color = TextPrimary)
                    Text("SEE ALL", fontFamily = JetBrainsMono, fontSize = 10.sp,
                        color = TextLabel, modifier = Modifier.clickable(onClick = onSeeAllTap))
                }
            }

            items(state.recentTransactions) { tx ->
                TransactionRow(tx)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(label, fontFamily = JetBrainsMono, fontSize = 9.sp,
            color = TextDisabled, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = valueColor)
    }
}

@Composable
private fun TransactionRow(tx: Transaction) {
    val isCredit = tx.type in listOf("REFUND", "SELF_TRANSFER")
    val amountColor = if (isCredit) Green else TextPrimary
    val amountPrefix = if (isCredit) "−" else "+"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            Modifier.size(36.dp).background(AmberIconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(tx.bank.take(2).uppercase(),
                fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, color = Amber)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.bank, fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp, color = TextPrimary)
            Text("${tx.bank} · ${tx.cardLast4 ?: "—"} · ${shortTime(tx.txnTime)}",
                fontFamily = JetBrainsMono, fontSize = 10.sp, color = TextMeta)
        }
        Text("$amountPrefix${formatIndianRupees(tx.amountPaise)}",
            fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, color = amountColor)
    }
}

@Composable
private fun BottomTabBar(
    unreviewedCount: Int,
    onHomeTap: () -> Unit,
    onReviewTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onMoreTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgNav)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        listOf("HOME" to onHomeTap, "REVIEW" to onReviewTap,
               "HISTORY" to onHistoryTap, "MORE" to onMoreTap)
            .forEachIndexed { i, (label, action) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = action)) {
                    if (i == 1 && unreviewedCount > 0) {
                        Box {
                            Text(label, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp, color = Amber)
                            Box(Modifier.size(7.dp).background(AmberDeep, CircleShape)
                                .align(Alignment.TopEnd))
                        }
                    } else {
                        Text(label, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp, color = if (i == 0) Amber else TextDisabled)
                    }
                }
            }
    }
}

private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

private fun shortTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
