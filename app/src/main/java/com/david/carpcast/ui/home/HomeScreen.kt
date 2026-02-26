package com.david.carpcast.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.carpcast.ForecastActivity
import com.david.carpcast.R
import com.david.carpcast.location.LocationViewModel
import com.david.carpcast.location.OpenStreetMapView
import com.david.carpcast.model.LocationModel
import com.david.carpcast.model.SpeciesOption
import com.david.carpcast.repository.ForecastRepository
import com.david.carpcast.ui.theme.CarpCastTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarpCastTopBar() {
    TopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.carpcast_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    stringResource(R.string.carpcast_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun HomeScreen(locationVm: LocationViewModel = viewModel()) {
    val searchQuery = locationVm.searchQuery
    val searchResults = locationVm.searchResults
    val selectedLocation = locationVm.selectedLocation
    val isLoading = locationVm.isLoading
    val favorites = locationVm.favorites

    HomeScreenContent(
        searchQuery = searchQuery,
        searchResults = searchResults,
        selectedLocation = selectedLocation,
        isLoading = isLoading,
        favorites = favorites,
        onQueryChanged = { locationVm.onQueryChanged(it) },
        onLocationSelected = { locationVm.selectLocation(it) },
        onClearSelection = { locationVm.clearSelection() },
        onToggleFavorite = { locationVm.toggleFavorite(it) },
        onRemoveFavorite = { locationVm.removeFavorite(it) },
        isFavorite = { locationVm.isFavorite(it) }
    )
}

@Composable
fun HomeScreenContent(
    searchQuery: String,
    searchResults: List<LocationModel>,
    selectedLocation: LocationModel?,
    isLoading: Boolean,
    favorites: List<LocationModel> = emptyList(),
    onQueryChanged: (String) -> Unit = {},
    onLocationSelected: (LocationModel) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onToggleFavorite: (LocationModel) -> Unit = {},
    onRemoveFavorite: (LocationModel) -> Unit = {},
    isFavorite: (LocationModel?) -> Boolean = { false }
) {
    // obtain labels via stringResource (composable) and then build list inside remember
    val speciesCarpLabel = stringResource(R.string.species_carp_label)
    val speciesCarpSubtitle = stringResource(R.string.species_carp_subtitle)
    val speciesBarbelLabel = stringResource(R.string.species_barbel_label)
    val speciesBarbelSubtitle = stringResource(R.string.species_barbel_subtitle)
    val speciesBassLabel = stringResource(R.string.species_bass_label)
    val speciesBassSubtitle = stringResource(R.string.species_bass_subtitle)
    val speciesPikeLabel = stringResource(R.string.species_pike_label)
    val speciesPikeSubtitle = stringResource(R.string.species_pike_subtitle)
    val speciesCatfishLabel = stringResource(R.string.species_catfish_label)
    val speciesCatfishSubtitle = stringResource(R.string.species_catfish_subtitle)

    // localized fallback names
    val myLocationName = stringResource(R.string.my_location)
    val defaultLocationName = stringResource(R.string.default_location_name)
    // strings usados en diálogos / callbacks
    val enterFavText = stringResource(R.string.enter_favorite_name)
    val favoriteAdded = stringResource(R.string.favorite_added)
    val okLabel = stringResource(R.string.ok)
    val cancelLabel = stringResource(R.string.cancel)

    val speciesOptions = remember {
        listOf(
            SpeciesOption("carp", speciesCarpLabel, speciesCarpSubtitle, R.drawable.carpicondos),
            SpeciesOption("barbel", speciesBarbelLabel, speciesBarbelSubtitle, R.drawable.barboicon),
            SpeciesOption("bass", speciesBassLabel, speciesBassSubtitle, R.drawable.blackbassicon),
            SpeciesOption("pike", speciesPikeLabel, speciesPikeSubtitle, R.drawable.pikeicondos),
            SpeciesOption("catfish", speciesCatfishLabel, speciesCatfishSubtitle, R.drawable.catfishicon)
        )
    }

    var selectedSpecies by remember { mutableStateOf(speciesOptions.first()) }

    val scroll = rememberScrollState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Local flag: mostrar banner de carga SOLO cuando el usuario pulse "Obtener Pronóstico"
    var showLoadingForecast by remember { mutableStateOf(false) }

    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // si el usuario concede el permiso, se puede intentar obtener la ubicación cuando corresponda
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Hero()
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.species_title), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    // SpeciesGrid accepts an icon provider lambda; defaults to Pets if not provided.
                    SpeciesGrid(speciesOptions, selectedSpecies, onSelect = { selectedSpecies = it })
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.select_location_title), fontWeight = FontWeight.SemiBold)

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query -> onQueryChanged(query) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        singleLine = true
                    )

                    if (searchResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column {
                            searchResults.forEach { r ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLocationSelected(r) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(r.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${r.latitude.format(4)}, ${r.longitude.format(4)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        val loc = selectedLocation ?: LocationModel(defaultLocationName, 40.4168, -3.7038)
                        val zoom = if (selectedLocation != null) 12 else 6

                        // vista del mapa: en preview mostramos un placeholder para evitar crashes
                        val isPreview = LocalInspectionMode.current
                        if (isPreview) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                                // texto pequeño para identificar el placeholder
                                Text(text = "Map preview", modifier = Modifier.align(Alignment.Center))
                            }
                        } else {
                            // vista real del mapa
                            OpenStreetMapView(
                                location = loc,
                                modifier = Modifier.fillMaxSize(),
                                zoom = zoom,
                                onMapClick = { onLocationSelected(it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            // comprobar permiso
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PermissionChecker.PERMISSION_GRANTED

                            if (!fineGranted) {
                                // solicitar permiso
                                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                // obtener ubicación de forma asíncrona y seleccionar
                                scope.launch {
                                    val deviceLoc = fetchDeviceLocation(context, myLocationName)
                                    if (deviceLoc != null) {
                                        onLocationSelected(deviceLoc)
                                    } else {
                                        // fallback: usar nombre localizado
                                        val loc = LocationModel(
                                            myLocationName,
                                            40.4168 + kotlin.random.Random.nextDouble(-0.02, 0.02),
                                            -3.7038 + kotlin.random.Random.nextDouble(-0.02, 0.02)
                                        )
                                        onLocationSelected(loc)
                                    }
                                }
                            }
                        },

                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isLoading) stringResource(R.string.getting_location) else myLocationName)
                    }

                    // Favoritos: diálogo para introducir nombre al añadir, confirmación al eliminar
                    var showFavDialog by remember { mutableStateOf(false) }
                    var favName by remember { mutableStateOf("") }
                    var showFavBanner by remember { mutableStateOf(false) }
                    var favBannerText by remember { mutableStateOf("") }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isFav = isFavorite(selectedLocation)
                        val addFavLabel = stringResource(R.string.add_favorite)
                        val removeFavLabel = stringResource(R.string.remove_favorite)
                        OutlinedButton(
                            onClick = {
                                selectedLocation?.let { sel ->
                                    if (isFav) {
                                        // eliminar favorito sin diálogo (confirmación rápida)
                                        onToggleFavorite(sel)
                                        favBannerText = removeFavLabel
                                        showFavBanner = true
                                        scope.launch { delay(1100); showFavBanner = false }
                                    } else {
                                        // abrir diálogo para introducir nombre antes de añadir
                                        favName = sel.name
                                        showFavDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedLocation != null
                        ) {
                            Icon(Icons.Default.Star, contentDescription = if (isFav) removeFavLabel else addFavLabel)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFav) removeFavLabel else addFavLabel)
                        }
                    }

                    // Diálogo para introducir nombre y confirmar añadido
                    if (showFavDialog) {
                        val sel = selectedLocation
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showFavDialog = false },
                            title = { Text(stringResource(R.string.add_favorite)) },
                            text = {
                                Column {
                                    Text(enterFavText)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = favName,
                                        onValueChange = { favName = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    if (sel != null) {
                                        val locToSave = LocationModel(favName.ifBlank { sel.name }, sel.latitude, sel.longitude)
                                        onToggleFavorite(locToSave)
                                        favBannerText = favoriteAdded
                                        showFavBanner = true
                                        scope.launch { delay(1100); showFavBanner = false }
                                    }
                                    showFavDialog = false
                                }) {
                                    Text(okLabel)
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showFavDialog = false }) { Text(cancelLabel) }
                            }
                        )
                    }

                    // Banner modal temporal para confirmar acción de favoritos (texto breve)
                    if (showFavBanner) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { /* no dismiss */ },
                            title = null,
                            text = { Text(favBannerText, style = MaterialTheme.typography.bodyMedium) },
                            confirmButton = {},
                            dismissButton = {}
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (favorites.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.favorites_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Column {
                            favorites.forEach { fav ->
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLocationSelected(fav) }
                                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(fav.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onRemoveFavorite(fav) }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            // activar banner de carga y lanzar tarea en background
                            showLoadingForecast = true
                            scope.launch {
                                try {
                                    val repository = ForecastRepository()
                                    val loc = selectedLocation ?: LocationModel(defaultLocationName, 40.4168, -3.7038)
                                    // Ejecutar la generación del JSON en IO para no bloquear la UI
                                    val forecastJson = withContext(Dispatchers.IO) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            // Llamada real solo en dispositivos compatibles
                                            repository.buildForecastJsonReal(
                                                loc.latitude,
                                                loc.longitude,
                                                selectedSpecies.id
                                            ).toString()
                                        } else {
                                            // Fallback seguro para previsualización y dispositivos antiguos
                                            // Construimos un JSON mínimo que la Activity puede manejar
                                            "{ \"lat\": ${loc.latitude}, \"lon\": ${loc.longitude}, \"mode\": \"${selectedSpecies.id}\" }"
                                        }
                                    }
                                    val intent = Intent(context, ForecastActivity::class.java).apply {
                                        putExtra(ForecastActivity.EXTRA_FORECAST_JSON, forecastJson)
                                    }
                                    // Iniciar la Activity y mantener el banner visible un breve momento
                                    context.startActivity(intent)
                                    // dar tiempo a que la transición sea percibida
                                    delay(400)
                                } catch (_: IOException) {
                                    // manejar error (log, Snackbar, etc.)
                                } catch (_: Exception) {
                                    // cualquier fallo
                                } finally {
                                    // ocultar el banner en cualquier caso
                                    showLoadingForecast = false
                                }
                            }
                        },
                        enabled = selectedLocation != null || searchQuery.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedLocation != null || searchQuery.isNotBlank())
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.get_forecast))
                    }

                    Spacer(Modifier.height(12.dp))

                    if (selectedLocation != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    selectedLocation.name,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${selectedLocation.latitude.format(4)}, ${selectedLocation.longitude.format(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onClearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back_desc))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            FeaturesGrid()

            Spacer(Modifier.height(12.dp))

            Footer()
        }

        // Overlay modal-style alert shown while loading forecast (modal, non-dismissible)
        if (showLoadingForecast) {
            BackHandler(enabled = true) { /* ignore back while active */ }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { /* do not dismiss automatically */ },
                title = { Text(stringResource(R.string.changing_page_title), fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.changing_page_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = { /* no buttons */ },
                dismissButton = { /* no buttons */ }
            )
        }
    }
}


@Composable
fun Hero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Envolver la imagen en un Card circular para forzar el recorte
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = null
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.carpcast_subtitle),
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
    }
}

@Composable
fun SpeciesGrid(options: List<SpeciesOption>, selected: SpeciesOption, onSelect: (SpeciesOption) -> Unit) {
    Column {
        val rows = options.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { s ->
                    val isSelected = s.id == selected.id
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp)
                            .clickable { onSelect(s) },
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = s.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                s.label,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
fun FeaturesGrid() {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically

        ) {
            FeatureCard(
                title = stringResource(R.string.feature_meteorology_title),
                subtitle = stringResource(R.string.feature_meteorology_subtitle),
                icon = Icons.Default.Cloud,
                modifier = Modifier.weight(1f)
            )
            FeatureCard(
                title = stringResource(R.string.feature_astronomy_title),
                subtitle = stringResource(R.string.feature_astronomy_subtitle),
                icon = Icons.Default.AccessTime,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FeatureCard(
                title = stringResource(R.string.feature_hydro_title),
                subtitle = stringResource(R.string.feature_hydro_subtitle),
                icon = Icons.Default.InvertColors,
                modifier = Modifier.weight(1f)
            )
            FeatureCard(
                title = stringResource(R.string.feature_map_title),
                subtitle = stringResource(R.string.feature_map_subtitle),
                icon = Icons.Default.Map,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.height(110.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Suppress("unused")
@Composable
fun InfoCard() {
    Card(shape = RoundedCornerShape(12.dp)) {
        Box(
            modifier = Modifier.background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.info_how_title), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.info_bullet_1), color = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(R.string.info_bullet_2), color = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(R.string.info_bullet_3), color = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(R.string.info_bullet_4), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Suppress("unused")
@Composable
fun QuickAccess() {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.quick_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { /* ir a favoritos */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.quick_fav_title), fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.quick_fav_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { /* ir a settings */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.quick_settings_title), fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.quick_settings_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun Footer() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sources_label) + ": Open-Meteo · OpenStreetMap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "© 2026 " + stringResource(R.string.carpcast_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

@Preview(showBackground = true, widthDp = 412, heightDp = 1400, showSystemUi = true)
@Composable
fun HomePreview() {
    CarpCastTheme {
        // En preview forzamos un contenedor con altura fija para que el contenido interno
        // (que ya tiene verticalScroll) pueda desplazarse dentro del área de preview.
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(1400.dp)) {
            HomeScreenContent(
                searchQuery = "Madrid",
                searchResults = listOf(
                    LocationModel("Madrid", 40.4168, -3.7038),
                    LocationModel("Madrid, New Mexico", 35.4059, -106.1542)
                ),
                selectedLocation = null,
                isLoading = false,
                favorites = listOf(LocationModel("Home lake", 40.5, -3.7)),
                onQueryChanged = {},
                onLocationSelected = {},
                onClearSelection = {},
                onToggleFavorite = {},
                onRemoveFavorite = {},
                isFavorite = { false }
            )
        }
    }
}

suspend fun fetchDeviceLocation(context: Context, defaultName: String = "My location"): LocationModel? = withContext(Dispatchers.Main) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    suspendCancellableCoroutine<LocationModel?> { cont ->
        try {
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) {
                        if (loc != null) cont.resume(LocationModel(defaultName, loc.latitude, loc.longitude))
                        else cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        } catch (_: SecurityException) {
            if (!cont.isCompleted) cont.resume(null)
        } catch (_: Exception) {
            if (!cont.isCompleted) cont.resume(null)
        }
    }
}
