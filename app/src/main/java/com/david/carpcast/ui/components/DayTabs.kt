package com.david.carpcast.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DaySelector(items: List<Pair<String, Int>>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, it ->
            val label = it.first
            val score = it.second
            val isSelected = idx == selectedIndex
            val bgColor = animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            Surface(modifier = Modifier
                .weight(1f)
                .clickable { onSelect(idx) }
                .height(64.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = if (isSelected) 6.dp else 1.dp,
                color = bgColor.value
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = label, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    // mini score text
                    Text(text = "$score", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    // mini bar
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))) {
                        Box(modifier = Modifier
                            .fillMaxWidth(score.coerceIn(0,100) / 100f)
                            .height(6.dp)
                            .background(color = if (score >= 75) MaterialTheme.colorScheme.primary else if (score >= 40) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error))
                    }
                }
            }
        }
    }
}