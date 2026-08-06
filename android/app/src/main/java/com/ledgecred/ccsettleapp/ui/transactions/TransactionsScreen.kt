package com.ledgecred.ccsettleapp.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
fun TransactionsScreen(
    vm: TransactionsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val transactions = vm.transactions.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier.fillMaxSize().background(Bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = TextLabel, fontFamily = InstrumentSans)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${transactions.size} transactions",
                fontFamily = JetBrainsMono, fontSize = 11.sp, color = TextLabel
            )
        }

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions yet", style = AppTypography.bodyMedium, color = TextLabel)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.id }) { tx ->
                TransactionItem(tx = tx, onDiscard = { vm.discard(tx.id) })
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun TransactionItem(tx: Transaction, onDiscard: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    val isCredit = tx.type in listOf("REFUND", "SELF_TRANSFER")
    val amountColor  = when (tx.type) {
        "REFUND", "SELF_TRANSFER" -> Green
        "UNPARSED"                -> TextLabel
        else                      -> TextPrimary
    }
    val amountPrefix = if (isCredit) "−" else "+"
    val typeColor = when (tx.type) {
        "DEBIT"         -> Amber
        "REFUND"        -> Green
        "SELF_TRANSFER" -> Green
        "UNPARSED"      -> TextLabel
        else            -> TextDisabled
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            Modifier.size(36.dp).background(AmberIconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                tx.bank.take(2).uppercase(),
                fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, color = Amber
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(tx.bank, fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tx.type, fontFamily = JetBrainsMono, fontSize = 9.sp, color = typeColor)
                Text("·", color = TextDisabled, fontSize = 9.sp)
                Text(shortDate(tx.txnTime), fontFamily = JetBrainsMono, fontSize = 9.sp, color = TextMeta)
                if (tx.cardLast4 != null) {
                    Text("·", color = TextDisabled, fontSize = 9.sp)
                    Text("··${tx.cardLast4}", fontFamily = JetBrainsMono, fontSize = 9.sp, color = TextMeta)
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$amountPrefix${formatIndianRupees(tx.amountPaise)}",
                fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = amountColor
            )
        }

        Spacer(Modifier.width(8.dp))

        // Discard button
        if (showConfirm) {
            TextButton(
                onClick = { onDiscard(); showConfirm = false },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Confirm", color = Red, fontFamily = InstrumentSans,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        } else {
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Discard",
                    tint = TextDisabled, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun shortDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
