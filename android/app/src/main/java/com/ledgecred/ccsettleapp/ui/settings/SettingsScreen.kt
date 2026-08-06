package com.ledgecred.ccsettleapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.ledgecred.ccsettleapp.data.db.entity.UserCard
import com.ledgecred.ccsettleapp.ui.theme.*

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onLogout: () -> Unit = onBack
) {
    val state   = vm.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var vpaInput by remember(state.vpa) { mutableStateOf(state.vpa) }
    var showAddCard by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onBack) { Text("← Back", color = TextLabel) }
        }

        // Battery alert
        if (!state.batteryOptIgnored) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(RedBg, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Battery optimization is ON — digest may be killed.",
                        style = AppTypography.bodyMedium, color = RedText, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text("FIX", color = Red, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // SETTLE section
        item { SectionHeader("SETTLE") }
        item {
            SettingsCard {
                OutlinedTextField(
                    value = vpaInput, onValueChange = { vpaInput = it },
                    label = { Text("Your UPI ID") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = Border, focusedLabelColor = Amber
                    ),
                    trailingIcon = {
                        if (vpaInput != state.vpa) {
                            TextButton(onClick = { vm.setVpa(vpaInput) }) {
                                Text("Save", color = Amber, fontSize = 12.sp)
                            }
                        }
                    }
                )
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                SettingsToggle("Split above daily cap", state.splitAboveCap) { vm.setSplitAboveCap(it) }
            }
        }

        // TRACKED CARDS section
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("TRACKED CARDS")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddCard = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add card", tint = Amber)
                }
            }
        }

        if (state.cards.isEmpty()) {
            item {
                Text(
                    "No cards added. Tap + to add a card.\nAll transactions counted until a card is added.",
                    style = AppTypography.bodySmall, color = TextLabel,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            item {
                SettingsCard {
                    state.cards.forEachIndexed { i, card ->
                        if (i > 0) HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 8.dp))
                        CardRow(card = card, onDelete = { vm.removeCard(card) })
                    }
                }
            }
        }

        // Sign out
        item {
            TextButton(
                onClick = { vm.logout(); onLogout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out", color = Red,
                    fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showAddCard) {
        AddCardDialog(
            onDismiss = { showAddCard = false },
            onAdd = { bank, last4, nickname ->
                vm.addCard(bank, last4, nickname)
                showAddCard = false
            }
        )
    }
}

@Composable
private fun CardRow(card: UserCard, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(card.display, style = AppTypography.bodyLarge, color = TextPrimary)
            Text("··${card.last4} · ${card.bank}", style = AppTypography.bodySmall, color = TextLabel)
        }
        if (showConfirm) {
            TextButton(onClick = { onDelete(); showConfirm = false },
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Remove", color = Red, fontSize = 12.sp, fontFamily = InstrumentSans)
            }
        } else {
            IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove",
                    tint = TextDisabled, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onAdd: (bank: String, last4: String, nickname: String?) -> Unit) {
    var bank     by remember { mutableStateOf("") }
    var last4    by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title = { Text("Add Credit Card", color = TextPrimary, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = bank, onValueChange = { bank = it },
                    label = { Text("Bank (e.g. HDFC, Slice, IDFC)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber)
                )
                OutlinedTextField(
                    value = last4, onValueChange = { if (it.length <= 4) last4 = it.filter { c -> c.isDigit() } },
                    label = { Text("Last 4 digits") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber)
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    label = { Text("Nickname (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber)
                )
                if (error.isNotEmpty()) Text(error, color = Red, style = AppTypography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bank.isBlank()) { error = "Bank is required"; return@TextButton }
                if (last4.length != 4) { error = "Enter 4 digits"; return@TextButton }
                onAdd(bank.trim(), last4.trim(), nickname.trim().ifBlank { null })
            }) { Text("Add", color = Amber, fontFamily = InstrumentSans, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextLabel) }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, color = TextDisabled, letterSpacing = 1.2.sp)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(16.dp),
        content  = content
    )
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTypography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmberInk, checkedTrackColor = Amber,
                uncheckedThumbColor = TextDisabled, uncheckedTrackColor = Surface))
    }
}
