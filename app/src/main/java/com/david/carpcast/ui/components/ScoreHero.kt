// kotlin
package com.david.carpcast.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.david.carpcast.R

@Composable
fun ScoreHero(
    score: Int,
    stateText: String,
    bestRange: String?,
    confidence: Int?,
    modifier: Modifier = Modifier,
    selectedLabel: String? = null,
    trendText: String? = null
) {
    val color = when {
        score >= 80 -> MaterialTheme.colorScheme.primaryContainer
        score >= 65 -> MaterialTheme.colorScheme.secondary
        score >= 50 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val animatedColor = animateColorAsState(targetValue = color)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = animatedColor.value,
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Text(text = stringResource(id = R.string.fishing_activity_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))

            // score line
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$score / 100",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 40.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = stateText + " " + stringResource(id = R.string.conditions_suffix), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (!bestRange.isNullOrEmpty()) {
                                Text(text = stringResource(id = R.string.best_time_label, bestRange), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // confidence small
                if (confidence != null) {
                    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                        Text(text = stringResource(id = R.string.confidence_badge, confidence), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // trend
            if (!trendText.isNullOrEmpty()) {
                Text(text = stringResource(id = R.string.activity_trend_label, trendText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }

            // selected small label
            if (!selectedLabel.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(id = R.string.selected_label, selectedLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ChipBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
