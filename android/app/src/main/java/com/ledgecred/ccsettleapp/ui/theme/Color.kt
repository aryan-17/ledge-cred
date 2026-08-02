package com.ledgecred.ccsettleapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Backgrounds
val Bg           = Color(0xFF0A0B0D)
val BgNav        = Color(0xFF0C0E11)
val Surface      = Color(0xFF101317)
val SurfaceRaised = Color(0xFF141920)
val SurfaceSunken = Color(0xFF0C0F13)
val Track        = Color(0xFF16191E)
val Chip         = Color(0xFF1C2029)

// Borders / dividers
val Border       = Color(0x0FFFFFFF)   // rgba(255,255,255,.06)
val BorderStrong = Color(0x17FFFFFF)   // rgba(255,255,255,.09)
val Divider      = Color(0x0DFFFFFF)   // rgba(255,255,255,.05)

// Text
val TextPrimary  = Color(0xFFF2F3F5)
val TextSecondary = Color(0xFFEDEFF2)
val TextLabel    = Color(0xFF8A9099)
val TextMuted    = Color(0xFF6C737D)
val TextDisabled = Color(0xFF4E555F)
val TextMeta     = Color(0xFF525963)

// Amber
val Amber        = Color(0xFFFFB020)
val AmberBright  = Color(0xFFFFC94D)
val AmberDeep    = Color(0xFFFF8A3D)
val AmberInk     = Color(0xFF14100A)
val AmberIconBg  = Color(0xFF17110A)
val AmberTintBg  = Color(0x0FFFB020)   // rgba(255,176,32,.06)
val AmberTintBorder = Color(0x23FFB020) // rgba(255,176,32,.14)

// Green
val Green        = Color(0xFF3ECF8E)
val GreenBg      = Color(0xFF0C1712)

// Red
val Red          = Color(0xFFFF6B6B)
val RedBg        = Color(0xFF150F0F)
val RedText      = Color(0xFFFF9A9A)

// Blue (Gemini suggestion)
val Blue         = Color(0xFF7CC4FF)

// Gradients
val AmberGradient = Brush.linearGradient(listOf(Amber, AmberDeep))
val GaugeGradient = Brush.linearGradient(listOf(AmberDeep, AmberBright))
