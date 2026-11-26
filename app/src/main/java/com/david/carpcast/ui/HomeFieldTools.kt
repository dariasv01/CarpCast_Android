package com.david.carpcast.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.david.carpcast.ForecastActivity
import com.david.carpcast.R
import com.david.carpcast.journal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeFieldTools() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var cached by remember { mutableStateOf<CachedForecast?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch {
                cached = withContext(Dispatchers.IO) { runCatching { FieldStore(context).cachedForecast() }.getOrNull() }
            }
        }
        owner.lifecycle.addObserver(observer)
        scope.launch { cached = withContext(Dispatchers.IO) { runCatching { FieldStore(context).cachedForecast() }.getOrNull() } }
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { context.startActivity(Intent(context, JournalActivity::class.java)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.field_journal_action))
        }
        cached?.let { saved ->
            val root = remember(saved) { runCatching { JSONObject(saved.json) }.getOrNull() }
            if (root != null) FieldCard {
                Text(stringResource(R.string.field_last_forecast), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(root.optString("name", root.optJSONObject("location")?.optString("name") ?: ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(speciesName(root.optString("species")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.field_updated, formatForecastTimestamp(root.optString("generatedAt"))), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.startActivity(Intent(context, ForecastActivity::class.java).putExtra(ForecastActivity.EXTRA_USE_CACHE, true)) }) {
                    Text(stringResource(R.string.field_open_saved))
                }
            }
        }
    }
}

fun formatForecastTimestamp(text: String): String = runCatching {
    Instant.parse(text).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.getDefault()))
}.getOrDefault(text.ifBlank { "—" })
