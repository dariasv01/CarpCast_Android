// kotlin
package com.david.carpcast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class BestWindow(val label: String, val score: Int)

@Composable
fun BestWindowsRank(windows: List<BestWindow>, modifier: Modifier = Modifier) {
    val maxScore = (windows.maxOfOrNull { it.score } ?: 1).coerceAtLeast(1)
    Column(modifier = modifier) {
        windows.forEachIndexed { idx, w ->
            val barHeight = if (idx == 0) 20.dp else 14.dp
            val barColor = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = w.label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = w.score.toFloat() / maxScore)
                            .height(barHeight)
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${w.score}", style = if (idx == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall)
            }
        }
    }
}