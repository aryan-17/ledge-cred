package com.ledgecred.ccsettleapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.ledgecred.ccsettleapp.ui.theme.*

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state   = vm.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var vpaInput by remember(state.vpa) { mutableStateOf(state.vpa) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onBack) { Text("← Back", color = TextLabel) }
        }

        // Battery optimization alert
        if (!state.batteryOptIgnored) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RedBg, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Battery optimization is ON — digest job may be killed.",
                        style = AppTypography.bodyMedium, color = RedText, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("FIX", color = Red, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // SETTLE section
        item { SectionHeader("SETTLE") }
        item {
            SettingsCard {
                // VPA
                OutlinedTextField(
                    value         = vpaInput,
                    onValueChange = { vpaInput = it },
                    label         = { Text("Your UPI ID") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Amber,
                        unfocusedBorderColor = Border,
                        focusedLabelColor    = Amber,
                        cursorColor          = Amber
                    ),
                    trailingIcon  = {
                        if (vpaInput != state.vpa) {
                            TextButton(onClick = { vm.setVpa(vpaInput) }) {
                                Text("Save", color = Amber, fontSize = 12.sp)
                            }
                        }
                    }
                )
                Divider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                // Digest time
                SettingsRow("Digest time", "${state.digestHour}:00") {}
                Divider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                // Split above cap toggle
                SettingsToggle("Split above daily cap", state.splitAboveCap) { vm.setSplitAboveCap(it) }
            }
        }

        // PARSER section
        item { SectionHeader("PARSER") }
        item {
            SettingsCard {
                SettingsToggle("Nightly Gemini fallback", state.geminiEnabled) { vm.setGeminiEnabled(it) }
                Divider(color = Divider, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { /* trigger ClassifyWorker for all */ }) {
                    Text("Re-parse all stored SMS →",
                        style = AppTypography.bodyMedium, color = TextLabel)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, color = TextDisabled, letterSpacing = 1.2.sp)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        content  = content
    )
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTypography.bodyMedium, color = TextSecondary)
        Text(value, fontFamily = JetBrainsMono, fontSize = 13.sp, color = TextLabel)
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTypography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Switch(
            checked           = checked,
            onCheckedChange   = onCheckedChange,
            colors            = SwitchDefaults.colors(
                checkedThumbColor   = AmberInk,
                checkedTrackColor   = Amber,
                uncheckedThumbColor = TextDisabled,
                uncheckedTrackColor = Surface
            )
        )
    }
}
