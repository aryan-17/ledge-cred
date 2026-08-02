package com.ledgecred.ccsettleapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ledgecred.ccsettleapp.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val InstrumentSans = FontFamily(
    Font(GoogleFont("Instrument Sans"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("Instrument Sans"), provider, weight = FontWeight.Medium),
    Font(GoogleFont("Instrument Sans"), provider, weight = FontWeight.SemiBold),
    Font(GoogleFont("Instrument Sans"), provider, weight = FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(GoogleFont("JetBrains Mono"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("JetBrains Mono"), provider, weight = FontWeight.Medium),
    Font(GoogleFont("JetBrains Mono"), provider, weight = FontWeight.Bold),
)

val AppTypography = Typography(
    // Screen title: Instrument Sans 700 · 19sp · -0.3sp
    titleLarge = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, letterSpacing = (-0.3).sp
    ),
    // Hero balance integer: JetBrains Mono 700 · 37sp · -1.4sp
    displayLarge = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
        fontSize = 37.sp, letterSpacing = (-1.4).sp
    ),
    // Stat value: JetBrains Mono 700 · 17sp
    headlineMedium = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    ),
    // List primary: Instrument Sans 600 · 13.5sp
    bodyLarge = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp
    ),
    // Body copy: Instrument Sans 400 · 13.5sp
    bodyMedium = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 22.sp
    ),
    // Primary button: Instrument Sans 700 · 15.5sp
    labelLarge = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp
    ),
    // Tab label: JetBrains Mono 600 · 9.5sp
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp, letterSpacing = 1.sp
    ),
    // Micro-label (all-caps): JetBrains Mono 500 · 10sp
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 1.2.sp
    ),
    // List meta / amount: JetBrains Mono 400 · 10.5sp
    bodySmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp
    ),
)
