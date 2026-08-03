package com.ledgecred.ccsettleapp.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.ui.home.formatIndianRupees
import com.ledgecred.ccsettleapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReviewScreen(
    vm: ReviewViewModel = viewModel(),
    onBack: () -> Unit
) {
    val queue = vm.queue.collectAsStateWithLifecycle().value

    Column(Modifier.fillMaxSize().background(Bg).padding(26.dp)) {

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = TextLabel) }
            Spacer(Modifier.weight(1f))
            Text("${queue.size} to review",
                fontFamily = JetBrainsMono, fontSize = 11.sp, color = TextLabel)
        }

        Spacer(Modifier.height(16.dp))

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Review queue empty", style = AppTypography.bodyMedium, color = TextLabel)
            }
            return@Column
        }

        // Progress bar
        val current = queue.first()
        LinearProgressIndicator(
            progress    = { 0f },
            modifier    = Modifier.fillMaxWidth().height(4.dp),
            color       = Amber,
            trackColor  = Track
        )

        Spacer(Modifier.height(24.dp))

        // Top SMS card
        ReviewCard(tx = current)

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton("✕",  Red,   "IGNORE") { vm.ignore(current.id) }
            ActionButton("↩",  Green, "REFUND") { vm.markAsRefund(current.id) }
            ActionButton("+",  Amber, "DEBIT")  { vm.markAsDebit(current.id) }
        }

        Spacer(Modifier.height(12.dp))
        Text("Swipe left to ignore · right to count it",
            style = AppTypography.bodySmall, color = TextDisabled,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ReviewCard(tx: Transaction) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(24.dp))
            .border(1.dp, BorderStrong, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        // Sender + time
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tx.bank, fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp, color = TextPrimary)
            Text(SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(tx.smsTime)),
                fontFamily = JetBrainsMono, fontSize = 10.5.sp, color = TextMeta)
        }

        Spacer(Modifier.height(12.dp))

        // Raw SMS
        Text(
            text     = tx.rawSms,
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 22.sp, color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceSunken, RoundedCornerShape(8.dp))
                .padding(12.dp)
        )


        Spacer(Modifier.height(12.dp))
        Text(formatIndianRupees(tx.amountPaise),
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, color = AmberBright)
    }
}

@Composable
private fun ActionButton(icon: String, color: androidx.compose.ui.graphics.Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick  = onClick,
            modifier = Modifier.size(60.dp),
            shape    = CircleShape,
            colors   = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
            contentPadding = PaddingValues(0.dp)
        ) { Text(icon, fontSize = 22.sp, color = color) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 9.sp, color = TextLabel)
    }
}
