package com.ledgecred.ccsettleapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background       = Bg,
    surface          = Surface,
    surfaceVariant   = SurfaceRaised,
    primary          = Amber,
    onPrimary        = AmberInk,
    secondary        = Green,
    error            = Red,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = Border,
)

@Composable
fun CcSettleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
