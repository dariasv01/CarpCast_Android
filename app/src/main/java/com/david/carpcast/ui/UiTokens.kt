package com.david.carpcast.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared UI tokens and small components used across screens

// Colors
val AppNavy = Color(0xFF071522)
val AppAccentBlue = Color(0xFF7FC1FF)
val AppAccentBlueStrong = Color(0xFF4DA8FF)
val AppAccentGreen = Color(0xFF9FD8B8)
val AppAccentRed = Color(0xFFFF9AA2)

// Cards
val AppCardCorner: Dp = 20.dp
val AppCardShape = RoundedCornerShape(AppCardCorner)
@Composable
fun AppCardElevation(default: Dp = 0.dp) = CardDefaults.cardElevation(defaultElevation = default)

// Activity theme helpers (kept small and deterministic)
fun activityAccentColor(score: Int): Color {
    return when {
        score >= 80 -> AppAccentGreen
        score >= 60 -> AppAccentBlue
        score >= 40 -> Color(0xFF8FBCEF)
        else -> Color(0xFFBFD9FF)
    }
}

fun activityGradientForScore(score: Int): Brush {
    val accent = activityAccentColor(score)
    val dark = when {
        score >= 80 -> Color(0xFF05281E)
        score >= 60 -> Color(0xFF062033)
        score >= 40 -> Color(0xFF081826)
        else -> Color(0xFF031021)
    }
    return Brush.verticalGradient(listOf(dark, accent.copy(alpha = 0.18f)))
}

@Composable
fun ThinProgress(value: Float, modifier: Modifier = Modifier, prominent: Boolean = false, color: Color? = null) {
    val height = if (prominent) 6.dp else 4.dp
    val barColor = color ?: if (prominent) AppAccentBlue else Color(0xFF8FBCEF)
    LinearProgressIndicator(
        progress = { value },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp)),
        trackColor = Color(0xFF0B1E33).copy(alpha = 0.6f),
        color = barColor
    )
}

