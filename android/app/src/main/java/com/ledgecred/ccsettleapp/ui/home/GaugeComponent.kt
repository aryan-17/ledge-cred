package com.ledgecred.ccsettleapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgecred.ccsettleapp.ui.theme.*

/**
 * 270° arc gauge. Gap sits at bottom.
 * pendingPaise: amount owed (amber arc)
 * settledTodayPaise: settled today (green arc, shown when > 0)
 * dailyCapPaise: gauge denominator (= bank's daily UPI cap, not arbitrary)
 */
@Composable
fun GaugeComponent(
    pendingPaise: Long,
    dailyCapPaise: Long,
    settledTodayPaise: Long,
    modifier: Modifier = Modifier
) {
    val sweepTotal  = 270f
    val startAngle  = 135f                 // gap at bottom
    val strokeWidth = 13.dp

    Box(modifier = modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val padding = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)

            // 1. Track (background)
            drawArc(
                color      = Track,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = stroke
            )

            val cap = dailyCapPaise.coerceAtLeast(1L)

            if (settledTodayPaise > 0L) {
                // Two-arc mode (post-partial): green settled arc + amber pending arc
                val greenSweep  = (settledTodayPaise.toFloat() / cap * sweepTotal).coerceIn(0f, sweepTotal)
                val amberSweep  = (pendingPaise.toFloat()      / cap * sweepTotal).coerceIn(0f, sweepTotal - greenSweep)

                drawArc(
                    color      = Green.copy(alpha = 0.28f),
                    startAngle = startAngle,
                    sweepAngle = greenSweep,
                    useCenter  = false,
                    topLeft    = topLeft, size = arcSize, style = stroke
                )
                drawArc(
                    color      = Amber,
                    startAngle = startAngle + greenSweep,
                    sweepAngle = amberSweep,
                    useCenter  = false,
                    topLeft    = topLeft, size = arcSize, style = stroke
                )
            } else {
                // Single amber arc
                val amberSweep = (pendingPaise.toFloat() / cap * sweepTotal).coerceIn(0f, sweepTotal)
                drawArc(
                    color      = Amber,
                    startAngle = startAngle,
                    sweepAngle = amberSweep,
                    useCenter  = false,
                    topLeft    = topLeft, size = arcSize, style = stroke
                )
            }
        }

        // Centred text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = if (settledTodayPaise > 0L) "STILL PENDING" else "PENDING TO SETTLE",
                style = AppTypography.labelMedium,
                color = TextDisabled
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = formatIndianRupees(pendingPaise),
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize   = 37.sp,
                color      = AmberBright
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "of ${formatIndianRupees(dailyCapPaise)} daily UPI cap",
                style = AppTypography.bodySmall,
                color = TextLabel
            )
            if (settledTodayPaise > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "● ${formatIndianRupees(settledTodayPaise)} settled today",
                    style = AppTypography.bodySmall,
                    color = Green
                )
            }
        }
    }
}

/** ₹X,XX,XXX — Indian number grouping. */
fun formatIndianRupees(paise: Long): String {
    val rupees = paise / 100
    val s = rupees.toString()
    if (s.length <= 3) return "₹$s"
    val last3 = s.takeLast(3)
    val rest  = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "₹$rest,$last3"
}
