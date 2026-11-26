package com.david.carpcast.journal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.david.carpcast.R
import com.david.carpcast.scoring.SpeciesSelection
import com.david.carpcast.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalHost(store: FieldStore, snapshotId: String?, onBack: () -> Unit, ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<FishingSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var seed by remember { mutableStateOf<String?>(null) }
    var editingSession by remember { mutableStateOf<FishingSession?>(null) }
    var snapshotOpened by rememberSaveable { mutableStateOf(false) }
    var addingCatch by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var includeImportedFavorites by rememberSaveable { mutableStateOf(false) }
    val selected = sessions.firstOrNull { it.id == selectedId }

    fun execute(action: () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                sessions = withContext(ioDispatcher) { action(); store.sessions() }
                error = false
                done()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val result = withContext(ioDispatcher) { store.sessions() to snapshotId?.let { store.sessionSnapshot(it) } }
            sessions = result.first
            seed = result.second
            if (seed != null && !snapshotOpened) { editing = true; snapshotOpened = true }
            if (editing && selectedId != null) editingSession = sessions.firstOrNull { it.id == selectedId }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }

    var transferMessage by remember { mutableStateOf<String?>(null) }
    fun transfer(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = withContext(ioDispatcher) { action() to store.sessions() }
                sessions = result.second
                transferMessage = result.first
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { transferMessage = context.getString(R.string.field_transfer_error) }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) transfer {
            val json = store.exportBackup()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            context.getString(R.string.field_export_success)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transfer {
            val result = requireNotNull(context.contentResolver.openInputStream(uri)).use { store.importBackup(it, includeFavorites = includeImportedFavorites) }
            context.getString(R.string.field_import_success, result.sessionsAdded, result.sessionsSkipped, result.locationsAdded) +
                "\n" + context.getString(R.string.field_import_catch_result, result.capturesAdded, result.capturesSkipped) +
                (if (result.historiesAdded > 0) "\n" + context.getString(R.string.field_history_result, result.historiesAdded, 0) else "")
        }
    }

    fun reconstruct(targets: List<FishingSession>) {
        transfer {
            var added = 0
            var failed = 0
            val repository = com.david.carpcast.repository.HistoricalForecastRepository()
            for (target in targets.filter { it.needsHistoricalForecast() }) {
                try {
                    val current = store.sessions().first { it.id == target.id }
                    if (!current.needsHistoricalForecast()) continue
                    val json = repository.reconstruct(current)
                    store.saveSession(current.copy(historicalForecastJson = json))
                    added++
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed++ }
            }
            context.getString(R.string.field_history_result, added, failed)
        }
    }

    val goBack = {
        if (addingCatch) addingCatch = false
        else if (editing) { editing = false; editingSession = null; seed = null }
        else if (selectedId != null) selectedId = null
        else onBack()
    }
    BackHandler { if (!busy) goBack() }

    FieldScaffold(title = stringResource(R.string.field_journal), onBack = { if (!busy) goBack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            transferMessage?.let { message ->
                FieldCard {
                    Text(message)
                    TextButton(onClick = { transferMessage = null }) { Text(stringResource(R.string.field_dismiss_message)) }
                }
            }
            if (error) {
                FieldCard {
                    Text(stringResource(R.string.field_storage_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { execute({}) { loading = false } }, enabled = !busy) { Text(stringResource(R.string.field_retry)) }
                }
            }
            if (!loading && !error) {
                when {
                    addingCatch && selected != null -> CatchForm(selected, onDismiss = { addingCatch = false }, onSave = { record ->
                        execute({ store.saveSession(selected.copy(catches = selected.catches + record)) }) { addingCatch = false }
                    }, enabled = !busy)
                    editing -> SessionForm(editingSession, seed, enabled = !busy,
                        onCancel = { goBack() }, onSave = { session -> execute({ store.saveSession(session) }) {
                            selectedId = session.id; editing = false; editingSession = null; seed = null
                        } })
                    selected != null -> {
                        SessionDetail(selected, enabled = !busy, onHistory = { reconstruct(listOf(selected)) }, onEdit = { editingSession = selected; editing = true },
                            onCatch = { addingCatch = true },
                            onFinish = { execute({ store.saveSession(selected.copy(endedAtMs = maxOf(System.currentTimeMillis(), selected.startedAtMs + 1))) }) },
                            onDelete = { deleteId = selected.id },
                            onRemoveCatch = { id -> deleteCatchId = id })
                    }
                    else -> {
                        Text(stringResource(R.string.field_journal_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { editingSession = null; seed = null; editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.field_new_session))
                        }
                        FieldCard {
                            Text(stringResource(R.string.field_backup_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.field_backup_hint), style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = includeImportedFavorites, onCheckedChange = { includeImportedFavorites = it }, enabled = !busy)
                                Text(stringResource(R.string.field_import_favorites_opt_in), style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { export.launch("CarpCast-copia-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Text(stringResource(R.string.field_export))
                            }
                            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Text(stringResource(R.string.field_import))
                            }
                        }
                        if (sessions.any { it.needsHistoricalForecast() }) FieldCard {
                            Text(stringResource(R.string.field_history_note), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { reconstruct(sessions) }, enabled = !busy) { Text(stringResource(R.string.field_history_all)) }
                        }
                        if (sessions.isEmpty()) FieldCard {
                            Text(stringResource(R.string.field_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.field_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        sessions.forEach { session ->
                            Card(onClick = { selectedId = session.id }, shape = AppCardShape, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(session.locationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${speciesName(session.targetSpecies)} · ${if (session.dateOnly) Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) else formatMoment(session.startedAtMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(if (session.endedAtMs == null) R.string.field_active else R.string.field_finished), color = MaterialTheme.colorScheme.primary)
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.field_captures, session.catches.size, session.catches.size))
                                }
                            }
                        }

                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text(stringResource(R.string.field_delete_title)) }, text = { Text(stringResource(R.string.field_delete_body)) },
        confirmButton = { TextButton(onClick = { execute({ store.deleteSession(id) }) { deleteId = null; selectedId = null } }, enabled = !busy) { Text(stringResource(R.string.field_delete)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.field_cancel)) } }) }
    val pendingCatch = selected?.catches?.firstOrNull { it.id == deleteCatchId }
    if (pendingCatch != null && selected != null) AlertDialog(
        onDismissRequest = { if (!busy) deleteCatchId = null },
        title = { Text(stringResource(R.string.field_delete_catch_title)) },
        text = { Text(stringResource(R.string.field_delete_catch_body, speciesName(pendingCatch.species), formatMoment(pendingCatch.atMs))) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            execute({
                val current = store.sessions().first { it.id == selected.id }
                store.saveSession(current.copy(catches = current.catches.filterNot { it.id == pendingCatch.id }))
            }) { deleteCatchId = null }
        }) { Text(stringResource(R.string.field_delete)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleteCatchId = null }) { Text(stringResource(R.string.field_cancel)) } }
    )
}

@Composable
private fun SessionForm(existing: FishingSession?, snapshot: String?, enabled: Boolean, onCancel: () -> Unit, onSave: (FishingSession) -> Unit) {
    val raw = remember(snapshot) { snapshot?.let { runCatching { JSONObject(it) }.getOrNull() } }
    var location by rememberSaveable(existing?.id, snapshot) { mutableStateOf(existing?.locationName ?: raw?.optString("name")?.takeIf { it.isNotBlank() } ?: raw?.optJSONObject("location")?.optString("name") ?: "") }
    var latitude by rememberSaveable(existing?.id, snapshot) { mutableStateOf(existing?.latitude ?: raw?.optDouble("lat")?.takeIf { it.isFinite() }) }
    var longitude by rememberSaveable(existing?.id, snapshot) { mutableStateOf(existing?.longitude ?: raw?.optDouble("lon")?.takeIf { it.isFinite() }) }
    var species by rememberSaveable(existing?.id, snapshot) { mutableStateOf(existing?.targetSpecies ?: raw?.optString("species", "carp") ?: "carp") }
    var rods by rememberSaveable(existing?.id) { mutableStateOf((existing?.rods ?: 1).toString()) }
    var waterbody by rememberSaveable(existing?.id) { mutableStateOf((existing?.waterbody ?: Waterbody.UNKNOWN).name) }
    var depth by rememberSaveable(existing?.id) { mutableStateOf(existing?.depthM?.toString() ?: "") }
    var water by rememberSaveable(existing?.id) { mutableStateOf(existing?.measuredWaterC?.toString() ?: "") }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var invalid by remember { mutableStateOf(false) }
    val originalStart = rememberSaveable(existing?.id) { existing?.startedAtMs ?: System.currentTimeMillis() }
    val zone = ZoneId.systemDefault()
    val initialDay = Instant.ofEpochMilli(originalStart).atZone(zone).toLocalDate()
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    var sessionDate by rememberSaveable(existing?.id) { mutableStateOf(initialDay.format(dateFormat)) }

    Text(stringResource(if (existing == null) R.string.field_new_session else R.string.field_edit_session), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    FieldCard {
        OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.field_location)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        JournalLocationPicker(location, latitude, longitude, enabled) { selected ->
            location = selected.name; latitude = selected.latitude; longitude = selected.longitude
        }
        if (raw != null || existing?.forecastJson != null) {
            Text(speciesName(species), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.field_snapshot_attached), color = MaterialTheme.colorScheme.primary)
        } else SpeciesPicker(species, enabled) { species = it }
        OutlinedTextField(rods, { rods = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), label = { Text(stringResource(R.string.field_rods)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        Text(stringResource(R.string.field_waterbody), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Waterbody.entries.forEach { type -> FilterChip(selected = type.name == waterbody, onClick = { waterbody = type.name }, label = { Text(waterbodyName(type)) }, enabled = enabled) } }
        OutlinedTextField(depth, { depth = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), label = { Text(stringResource(R.string.field_depth)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        OutlinedTextField(water, { water = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), label = { Text(stringResource(R.string.field_water_temp)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        Text(stringResource(R.string.field_context_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = enabled)
        Text(stringResource(R.string.field_session_date), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(sessionDate, { sessionDate = it }, label = { Text(stringResource(R.string.field_date_manual)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        CalendarDateButton(sessionDate, enabled) { sessionDate = it.format(dateFormat) }
        Text(stringResource(R.string.field_capture_time_matters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (invalid) Text(stringResource(R.string.field_invalid_session), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = {
        val depthValue = depth.replace(',', '.').toDoubleOrNull()
        val waterValue = water.replace(',', '.').toDoubleOrNull()
        if (depth.isNotBlank() && depthValue == null || water.isNotBlank() && waterValue == null) { invalid = true; return@Button }
        val day = parseJournalDate(sessionDate)
        if (day == null || day > LocalDate.now(zone)) { invalid = true; return@Button }
        val preserveTimes = existing != null && !existing.dateOnly && day == initialDay
        val start = if (preserveTimes) existing!!.startedAtMs else day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = if (preserveTimes) existing!!.endedAtMs else if (day < LocalDate.now(zone))
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            else if (day == initialDay) existing?.endedAtMs else null
        val session = (existing ?: FishingSession(startedAtMs = start,
            latitude = raw?.optDouble("lat")?.takeIf { it.isFinite() }, longitude = raw?.optDouble("lon")?.takeIf { it.isFinite() },
            forecastJson = snapshot)).copy(startedAtMs = start, endedAtMs = end, dateOnly = !preserveTimes, locationName = location.trim(), latitude = latitude, longitude = longitude, targetSpecies = species,
                rods = rods.toIntOrNull() ?: 0, waterbody = Waterbody.valueOf(waterbody), depthM = depthValue, measuredWaterC = waterValue, notes = notes.trim())
        if (runCatching { session.validate() }.isSuccess) onSave(session) else invalid = true
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(stringResource(R.string.field_save)) }
    TextButton(onClick = onCancel, enabled = enabled) { Text(stringResource(R.string.field_cancel)) }
}

@Composable
private fun SessionDetail(session: FishingSession, enabled: Boolean, onHistory: () -> Unit, onEdit: () -> Unit, onCatch: () -> Unit, onFinish: () -> Unit, onDelete: () -> Unit, onRemoveCatch: (String) -> Unit) {
    Text(session.locationName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(speciesName(session.targetSpecies), color = MaterialTheme.colorScheme.primary)
    if (session.latitude != null && session.longitude != null) {
        Text(stringResource(R.string.field_coordinates, session.latitude, session.longitude), style = MaterialTheme.typography.bodySmall)
    }
    FieldCard {
        if (session.dateOnly) {
            Text(stringResource(R.string.field_session_date) + ": " + Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")))
            Text(stringResource(R.string.field_capture_time_matters), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.field_start_time, formatMoment(session.startedAtMs)))
            Text(session.endedAtMs?.let { stringResource(R.string.field_end_time, formatMoment(it)) } ?: stringResource(R.string.field_active))
        }
        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.field_captures, session.catches.size, session.catches.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.field_rod_count, session.rods, session.rods))
        session.rodHours()?.let { Text(stringResource(R.string.field_effort, it)) }
        session.catchesPerRodHour()?.let { Text(stringResource(R.string.field_cpue, it)) }
        Text(waterbodyName(session.waterbody), color = MaterialTheme.colorScheme.onSurfaceVariant)
        session.depthM?.let { Text(stringResource(R.string.field_depth_value, it)) }
        session.measuredWaterC?.let { Text(stringResource(R.string.field_water_value, it)) }
        if (session.notes.isNotBlank()) Text(session.notes)
        OutlinedButton(onClick = onEdit, enabled = enabled) { Text(stringResource(R.string.field_edit_session)) }
    }
    SessionMap(session)
    if (session.needsHistoricalForecast()) FieldCard {
        Text(stringResource(R.string.field_history_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onHistory, enabled = enabled) { Text(stringResource(R.string.field_history_one)) }
    }
    Button(onClick = onCatch, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.field_add_catch)) }
    session.catches.sortedBy { it.atMs }.forEach { record -> FieldCard {
        Text("${speciesName(record.species)} · ${formatMoment(record.atMs)}", style = MaterialTheme.typography.titleSmall)
        record.weightKg?.let { Text(stringResource(R.string.field_weight_value, it)) }
        if (record.notes.isNotBlank()) Text(record.notes)
        HorizontalDivider()
        CatchForecastDetails(session, record)
        TextButton(onClick = { onRemoveCatch(record.id) }, enabled = enabled) { Text(stringResource(R.string.field_remove_catch)) }
    } }
    (session.forecastJson ?: session.historicalForecastJson)?.let { json ->
        val snapshot = remember(json) { JSONObject(json) }
        FieldCard {
            Text(stringResource(if (session.forecastJson != null) R.string.field_saved_prediction else R.string.field_history_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.field_prediction_date, snapshot.optString("generatedAt", "—")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val array = snapshot.optJSONArray("forecasts")
            val scores = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val point = array?.optJSONObject(i) ?: return@mapNotNull null
                val time = com.david.carpcast.util.TimeUtils.parseTimeMs(point.optString("time")) ?: return@mapNotNull null
                val end = session.endedAtMs ?: System.currentTimeMillis()
                if (time + 3_600_000L <= session.startedAtMs || time > end) return@mapNotNull null
                point.optJSONObject("activity")?.optInt("overall")
            }
            if (scores.isNotEmpty()) Text(stringResource(R.string.field_prediction_score, scores.average().toInt()))
            else Text(stringResource(R.string.field_prediction_no_overlap))
            Text(stringResource(R.string.field_snapshot_immutable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (session.endedAtMs == null) OutlinedButton(onClick = onFinish, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.field_finish_session)) }
    TextButton(onClick = onDelete, enabled = enabled) { Text(stringResource(R.string.field_delete)) }
}

@Composable
private fun CatchForm(session: FishingSession, enabled: Boolean, onDismiss: () -> Unit, onSave: (CatchRecord) -> Unit) {
    val zone = remember(session.id) { ZoneId.systemDefault() }
    val sessionDay = remember(session.id) { Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() }
    val defaultAt = remember { session.endedAtMs ?: System.currentTimeMillis() }
    val local = remember { Instant.ofEpochMilli(defaultAt).atZone(zone) }
    val defaultTime = local.format(DateTimeFormatter.ofPattern("HH:mm"))
    var time by rememberSaveable { mutableStateOf(if (sessionDay < LocalDate.now(zone)) "" else defaultTime) }
    val context = LocalContext.current
    var species by rememberSaveable { mutableStateOf(session.targetSpecies) }
    var weight by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Text(stringResource(R.string.field_add_catch), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    FieldCard {
            SpeciesPicker(species, enabled, title = R.string.field_catch_species) { species = it }
            Text(stringResource(R.string.field_capture_date_fixed, sessionDay.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.field_catch_time)) }, singleLine = true, enabled = enabled,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii))
            OutlinedButton(onClick = {
                val initial = parseCaptureClock(time) ?: LocalTime.now(zone)
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                }, initial.hour, initial.minute, true).show()
            }, enabled = enabled) { Text(stringResource(R.string.field_open_clock)) }
            OutlinedTextField(weight, { weight = it }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), label = { Text(stringResource(R.string.field_weight)) }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) })
            if (invalid) Text(stringResource(R.string.field_invalid_catch), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = {
        val clock = parseCaptureClock(time)
        val at = if (clock == null) null else if (local.toLocalDate() == sessionDay && time.trim() == defaultTime) defaultAt
            else sessionDay.atTime(clock).atZone(zone).toInstant().toEpochMilli()
        val kg = weight.replace(',', '.').toDoubleOrNull()
        if (at == null || weight.isNotBlank() && kg == null) { invalid = true; return@Button }
        val record = CatchRecord(atMs = at, species = species, weightKg = kg, notes = notes.trim())
        if (runCatching { session.copy(catches = session.catches + record).validate() }.isSuccess) onSave(record) else invalid = true
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.field_catch_save)) }
    TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.field_cancel)) }
}

@Composable
private fun SpeciesPicker(selected: String, enabled: Boolean, title: Int = R.string.field_target_species, onSelect: (String) -> Unit) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        val options = listOf("carp", "barbel", "bass", "pike", "catfish") + if (title == R.string.field_catch_species) listOf("other") else emptyList()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { id ->
            FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(speciesName(id)) }, enabled = enabled)
        } }
    }
}

@Composable
fun speciesName(id: String): String {
    if (id == "other") return stringResource(R.string.field_species_other)
    if (SpeciesSelection.fromId(id) == com.david.carpcast.scoring.FishSpecies.general && id.isNotBlank() && id !in listOf("unknown", "general")) return id
    return stringResource(when (SpeciesSelection.fromId(id)) {
    com.david.carpcast.scoring.FishSpecies.carp -> R.string.species_carp_label
    com.david.carpcast.scoring.FishSpecies.barbel -> R.string.species_barbel_label
    com.david.carpcast.scoring.FishSpecies.bass -> R.string.species_bass_label
    com.david.carpcast.scoring.FishSpecies.pike -> R.string.species_pike_label
    com.david.carpcast.scoring.FishSpecies.catfish -> R.string.species_catfish_label
    else -> R.string.field_unknown
    })
}

@Composable
private fun waterbodyName(type: Waterbody): String = stringResource(when(type) {
    Waterbody.UNKNOWN -> R.string.field_unknown
    Waterbody.RIVER -> R.string.field_river
    Waterbody.RESERVOIR -> R.string.field_reservoir
    Waterbody.POND -> R.string.field_pond
})

private fun formatMoment(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.getDefault()))

internal fun parseJournalDate(value: String): LocalDate? = runCatching {
    val text = value.trim()
    if ('/' in text) LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
    else LocalDate.parse(text)
}.getOrNull()

@Composable
private fun CalendarDateButton(value: String, enabled: Boolean, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val day = parseJournalDate(value) ?: LocalDate.now()
        android.app.DatePickerDialog(context, { _, year, month, date ->
            onSelect(LocalDate.of(year, month + 1, date))
        }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }, enabled = enabled) { Text(stringResource(R.string.field_open_calendar)) }
}

internal fun parseCaptureClock(value: String): LocalTime? = runCatching {
    val text = value.trim()
    require(Regex("[0-9]{2}:[0-9]{2}").matches(text))
    LocalTime.parse(text)
}.getOrNull()
