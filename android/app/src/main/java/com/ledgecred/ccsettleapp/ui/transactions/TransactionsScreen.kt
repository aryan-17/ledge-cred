package com.ledgecred.ccsettleapp.ui.transactions

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                TransactionItem(
                    tx = tx,
                    onDiscard = { vm.discard(tx.id) },
                    onSettle = { vm.settle(tx.id) },
                    onUnsettle = { vm.unsettle(tx.id) },
                    onMarkLent = { name -> vm.markLent(tx.id, name) },
                    onClearLent = { vm.clearLent(tx.id) },
                    onShare = { vm.buildShareIntent(tx) },
                    onShareFallback = { vm.buildShareIntentFallback(tx) }
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun TransactionItem(
    tx: Transaction,
    onDiscard: () -> Unit,
    onSettle: () -> Unit,
    onUnsettle: () -> Unit,
    onMarkLent: (String) -> Unit,
    onClearLent: () -> Unit,
    onShare: () -> android.content.Intent,
    onShareFallback: () -> android.content.Intent
) {
    var showConfirm by remember { mutableStateOf(false) }
    var showLentDialog by remember { mutableStateOf(false) }
    var showLentOptions by remember { mutableStateOf(false) }
    var showChangeName by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isSettled  = tx.settledAt != null
    val isLent     = tx.lentTo != null
    val isCredit   = tx.type in listOf("REFUND", "SELF_TRANSFER")
    val isUnparsed = tx.type == "UNPARSED"
    val amountColor  = when {
        isSettled                              -> TextDisabled
        isLent                                 -> Blue
        tx.type == "REFUND" || tx.type == "SELF_TRANSFER" -> Green
        isUnparsed                             -> TextLabel
        else                                   -> TextPrimary
    }
    val amountPrefix = if (isCredit) "−" else "+"
    val typeColor = when (tx.type) {
        "DEBIT"         -> Amber
        "REFUND"        -> Green
        "SELF_TRANSFER" -> Green
        "UNPARSED"      -> TextLabel
        else            -> TextDisabled
    }

    // New lent — enter name
    if (showLentDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLentDialog = false },
            containerColor = Surface,
            title = { Text("Lent to whom?",
                color = TextPrimary, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Friend's name", color = TextDisabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = TextDisabled,
                        cursorColor = Amber
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) { onMarkLent(name.trim()); showLentDialog = false } },
                    enabled = name.isNotBlank()
                ) {
                    Text("Mark as lent", color = Amber, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLentDialog = false }) {
                    Text("Cancel", color = TextLabel)
                }
            }
        )
    }

    // Lent options — share, change name, remove
    if (showLentOptions) {
        AlertDialog(
            onDismissRequest = { showLentOptions = false },
            containerColor = Surface,
            title = { Text("Lent to ${tx.lentTo}",
                color = Blue, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${formatIndianRupees(tx.amountPaise)} · ${shortDate(tx.txnTime)}",
                        fontFamily = JetBrainsMono, fontSize = 12.sp, color = TextLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showLentOptions = false
                            try { context.startActivity(onShare()) }
                            catch (_: ActivityNotFoundException) { context.startActivity(onShareFallback()) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share receipt", color = TextPrimary, fontFamily = InstrumentSans,
                            modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showLentOptions = false; showChangeName = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = Blue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change name", color = TextPrimary, fontFamily = InstrumentSans,
                            modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { onClearLent(); showLentOptions = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove lent tag", color = Red, fontFamily = InstrumentSans,
                            modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLentOptions = false }) {
                    Text("Close", color = TextLabel)
                }
            }
        )
    }

    // Change name dialog
    if (showChangeName) {
        var newName by remember { mutableStateOf(tx.lentTo ?: "") }
        AlertDialog(
            onDismissRequest = { showChangeName = false },
            containerColor = Surface,
            title = { Text("Change name",
                color = TextPrimary, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = TextDisabled,
                        cursorColor = Amber
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (newName.isNotBlank()) { onMarkLent(newName.trim()); showChangeName = false } },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save", color = Amber, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeName = false }) {
                    Text("Cancel", color = TextLabel)
                }
            }
        )
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
            Modifier.size(36.dp).background(if (isLent) Color(0xFF0D1520) else AmberIconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isLent) tx.lentTo!!.take(2).uppercase() else tx.bank.take(2).uppercase(),
                fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, color = if (isLent) Blue else Amber
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(tx.bank, fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp, color = TextPrimary)
                if (isLent) {
                    Text(
                        tx.lentTo!!,
                        fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        color = Blue,
                        modifier = Modifier
                            .background(Blue.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tx.type, fontFamily = JetBrainsMono, fontSize = 9.sp, color = typeColor)
                Text("·", color = TextDisabled, fontSize = 9.sp)
                Text(shortDate(tx.txnTime), fontFamily = JetBrainsMono, fontSize = 9.sp, color = TextMeta)
                if (tx.cardLast4 != null) {
                    Text("·", color = TextDisabled, fontSize = 9.sp)
                    Text("··${tx.cardLast4}", fontFamily = JetBrainsMono, fontSize = 9.sp, color = TextMeta)
                }
                if (isSettled) {
                    Text("·", color = TextDisabled, fontSize = 9.sp)
                    Text("SETTLED", fontFamily = JetBrainsMono, fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold, color = Green)
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (isUnparsed) "not counted" else "$amountPrefix${formatIndianRupees(tx.amountPaise)}",
                fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                fontSize = if (isUnparsed) 10.sp else 14.sp, color = amountColor
            )
        }

        Spacer(Modifier.width(4.dp))

        // Lent toggle (DEBIT only) — opens options dialog if already lent
        if (tx.type == "DEBIT") {
            IconButton(
                onClick = { if (isLent) showLentOptions = true else showLentDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isLent) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = if (isLent) "Lent options" else "Mark as lent",
                    tint = if (isLent) Blue else TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Settle toggle — requires confirm to prevent accidental taps
        if (tx.type == "DEBIT") {
            var showSettleConfirm by remember { mutableStateOf(false) }
            if (showSettleConfirm) {
                AlertDialog(
                    onDismissRequest = { showSettleConfirm = false },
                    containerColor = Surface,
                    title = { Text(if (isSettled) "Mark as unsettled?" else "Mark as settled?",
                        color = TextPrimary, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) },
                    text = { Text(if (isSettled) "This will add ₹${tx.amountPaise/100} back to your pending balance."
                        else "This removes ₹${tx.amountPaise/100} from your pending balance.",
                        style = AppTypography.bodyMedium, color = TextLabel) },
                    confirmButton = {
                        TextButton(onClick = { if (isSettled) onUnsettle() else onSettle(); showSettleConfirm = false }) {
                            Text("Confirm", color = Amber, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettleConfirm = false }) {
                            Text("Cancel", color = TextLabel)
                        }
                    }
                )
            }
            IconButton(
                onClick = { showSettleConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isSettled) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (isSettled) "Mark as unsettled" else "Mark as settled",
                    tint = if (isSettled) Green else TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

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
