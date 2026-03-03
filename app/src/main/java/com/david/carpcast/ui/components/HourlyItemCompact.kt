// kotlin
package com.david.carpcast.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

@Composable
fun HourlyItemCompact(hour: String, temp: String, score: Int, onClick: () -> Unit, modifier: Modifier = Modifier, isPrime: Boolean = false) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 12.dp),
        color = if (isPrime) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // prime accent stripe
            if (isPrime) {
                Box(modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary))
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.width(4.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPrime) {
                            // small badge with emoji
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), tonalElevation = 0.dp) {
                                Text(text = "🔥", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = hour, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(text = temp, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                val fraction = (score.coerceIn(0, 100) / 100f)
                val animated = animateFloatAsState(targetValue = fraction)
                val barHeight = if (isPrime) 10.dp else 8.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animated.value)
                            .height(barHeight)
                            .background(
                                color = when {
                                    isPrime -> MaterialTheme.colorScheme.primary
                                    score >= 75 -> MaterialTheme.colorScheme.primary
                                    score >= 40 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                    )
                }
            }
        }
    }
}