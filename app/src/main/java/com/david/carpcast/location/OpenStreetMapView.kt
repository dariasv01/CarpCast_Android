package com.david.carpcast.location

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.david.carpcast.model.LocationModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

@Composable
fun OpenStreetMapView(
    location: LocationModel,
    modifier: Modifier = Modifier,
    zoom: Int = 6,
    markers: List<LocationModel> = emptyList(),
    onMapClick: (LocationModel) -> Unit = {},
    onMarkerClick: (LocationModel) -> Unit = {},
    mapStyle: String = "mapnik",
    forceRemoveInternalControls: Boolean = true
) {
    val isPreview = LocalInspectionMode.current

    // mantener una referencia a MapView para controlar zoom desde los botones Compose
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    // estado local para el estilo del mapa (permite cambiar al vuelo)
    val styleState = remember { mutableStateOf(mapStyle) }
    // flag que indica si debemos mostrar los botones de zoom Compose (ocultar si existen controles internos)
    val showComposeZoom = remember { mutableStateOf(true) }
    // Si forzamos la eliminación de controles internos, asumimos que mostraremos los botones Compose
    if (forceRemoveInternalControls) showComposeZoom.value = true

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                // Configuration loading is handled differently for preview and normal execution.
                if (isPreview) {
                    // In preview mode, we just set the base path to avoid a crash.
                    // The load method can cause issues in preview.
                    Configuration.getInstance().osmdroidBasePath = ctx.cacheDir
                    Configuration.getInstance().osmdroidTileCache = ctx.cacheDir
                } else {
                    // On a real device, load the configuration using shared preferences.
                    Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                }
                val mv = MapView(ctx).apply {
                    // asegurar tiles escalados a densidad para evitar borrosidad en pantallas hdpi
                    try { this.setTilesScaledToDpi(true) } catch (_: Exception) {}
                    // asegurarse de que osmdroid use un user-agent para las peticiones (algunos servidores lo requieren)
                    try { Configuration.getInstance().userAgentValue = ctx.packageName } catch (_: Exception) {}
                    // tile source inicial según mapStyle
                    try {
                        setTileSource(selectTileSource(styleState.value))
                    } catch (_: Exception) {
                        Log.w("OpenStreetMapView", "Error inicializando tile source")
                    }

                    setMultiTouchControls(true)
                    // permitir uso de conexión de datos para descargar teselas
                    try { this.setUseDataConnection(true) } catch (_: Exception) {}

                    // Desactivar controles de zoom integrados (botones blancos) para evitar duplicidad
                    try { this.setBuiltInZoomControls(false) } catch (_: Exception) {}
                    // Además, iterar vistas hijas y eliminar cualquier ZoomControls u otra vista con 'zoom' en su nombre
                    try {
                        for (i in childCount - 1 downTo 0) {
                            val ch = getChildAt(i)
                            try {
                                val parent = ch.parent as? ViewGroup
                                if (ch.javaClass.simpleName.contains("zoom", ignoreCase = true) || ch.javaClass.simpleName.contains("ZoomControls", ignoreCase = true)) {
                                    parent?.removeView(ch)
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    // Estado inicial
                    tag = location
                    val point = GeoPoint(location.latitude, location.longitude)
                    controller.setZoom(zoom.toDouble())
                    controller.setCenter(point)

                    // Deshabilita el scroll del padre mientras se interactúa con el mapa
                    setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        if (event.action == MotionEvent.ACTION_UP) view.performClick()
                        false
                    }
                }
                mapRef.value = mv
                // Eliminar overlays relacionados con zoom que el MapView pueda haber añadido
                try { mv.overlays.removeAll { it.javaClass.simpleName.contains("zoom", ignoreCase = true) } } catch (_: Exception) {}
                mv
            },

            update = { mapView ->
                // mantener referencia actualizada
                mapRef.value = mapView

                // Asegurar el tile source según mapStyle y cambiar si es necesario
                try {
                    val desired = selectTileSource(styleState.value)
                    try {
                        val currentClass = try { mapView.tileProvider.tileSource.javaClass.simpleName } catch (_: Exception) { null }
                        val desiredClass = try { desired.javaClass.simpleName } catch (_: Exception) { null }
                        if (currentClass != desiredClass) {
                            try { mapView.tileProvider.clearTileCache() } catch (_: Exception) {}
                            try { mapView.setTileSource(desired) } catch (_: Exception) { Log.w("OpenStreetMapView", "Error aplicando tile source") }
                        }
                    } catch (_: Exception) { Log.w("OpenStreetMapView", "Error comprobando tile source") }
                } catch (_: Exception) {
                    Log.w("OpenStreetMapView", "Error al aplicar tileSource")
                }
                // asegurar que no haya overlays relacionados con zoom
                try { mapView.overlays.removeAll { it.javaClass.simpleName.contains("zoom", ignoreCase = true) } } catch (_: Exception) {}

                // Eliminar recursivamente cualquier control de zoom que pueda haber sido añadido
                fun removeRecursively(v: View) {
                    try {
                        val parent = v.parent as? ViewGroup
                        if (v.javaClass.simpleName.contains("zoom", ignoreCase = true) || v.javaClass.simpleName.contains("ZoomControls", ignoreCase = true)) {
                            parent?.removeView(v)
                            return
                        }
                        if (v is ViewGroup) {
                            for (i in v.childCount - 1 downTo 0) {
                                try { removeRecursively(v.getChildAt(i)) } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
                try {
                    try { mapView.setBuiltInZoomControls(false) } catch (_: Exception) {}
                    // iterar hijos y eliminar controles
                    for (i in mapView.childCount - 1 downTo 0) {
                        try { removeRecursively(mapView.getChildAt(i)) } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                // Eliminar sólo los marcadores existentes para re-renderizar
                val overlays = mapView.overlays
                overlays.removeAll { it is Marker }

                // Marcador principal
                val mainMarker = Marker(mapView).apply {
                    position = GeoPoint(location.latitude, location.longitude)
                    title = location.name
                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(location)
                        true
                    }
                }
                overlays.add(mainMarker)

                // Marcadores adicionales
                markers.forEach { loc ->
                    val m = Marker(mapView).apply {
                        position = GeoPoint(loc.latitude, loc.longitude)
                        title = loc.name
                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick(loc)
                            true
                        }
                    }
                    overlays.add(m)
                }

                // Añadir MapEventsOverlay si no existe (para detectar taps)
                val hasMapEvents = overlays.any { it is MapEventsOverlay }
                if (!hasMapEvents) {
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            p?.let {
                                val lm = LocationModel("Marcador", it.latitude, it.longitude)
                                onMapClick(lm)
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            return false
                        }
                    }
                    overlays.add(MapEventsOverlay(receiver))
                }

                // Añadir RotationGestureOverlay si no existe
                val rotationOverlay = RotationGestureOverlay(mapView)
                rotationOverlay.isEnabled = true
                if (!overlays.any { it is RotationGestureOverlay }) overlays.add(rotationOverlay)

                // Centrar/zoom
                try {
                    mapView.controller.setZoom(zoom.toDouble())
                    mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                } catch (_: Exception) { /* ignore during preview */ }

                mapView.invalidate()
                // También programar una limpieza después de la invalidación/layout
                try {
                    mapView.post {
                        try {
                            mapView.setBuiltInZoomControls(false)
                        } catch (_: Exception) {}
                        try {
                            for (i in mapView.childCount - 1 downTo 0) {
                                val ch = mapView.getChildAt(i)
                                try {
                                    val parent = ch.parent as? ViewGroup
                                    if (ch.javaClass.simpleName.contains("zoom", ignoreCase = true) || ch.javaClass.simpleName.contains("ZoomControls", ignoreCase = true)) {
                                        parent?.removeView(ch)
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                        // limpiar también del rootView por si se añadió fuera; y recorrer padres
                        try {
                            val root = mapView.rootView as? ViewGroup
                            root?.let { rv ->
                                for (i in rv.childCount - 1 downTo 0) {
                                    val ch = rv.getChildAt(i)
                                    try {
                                        if (ch.javaClass.simpleName.contains("zoom", ignoreCase = true) || ch.javaClass.simpleName.contains("ZoomControls", ignoreCase = true)) {
                                            Log.d("OpenStreetMapView", "Removing zoom control from rootView (update): ${ch.javaClass.simpleName}")
                                            (ch.parent as? ViewGroup)?.removeView(ch)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            // recorrer padres directos por si se añadió en contenedores intermedios
                            var p = mapView.parent
                            while (p is ViewGroup) {
                                val pg = p as ViewGroup
                                for (i in pg.childCount - 1 downTo 0) {
                                    val ch = pg.getChildAt(i)
                                    try {
                                        if (ch.javaClass.simpleName.contains("zoom", ignoreCase = true) || ch.javaClass.simpleName.contains("ZoomControls", ignoreCase = true)) {
                                            Log.d("OpenStreetMapView", "Removing zoom control from parent (update): ${ch.javaClass.simpleName}")
                                            (ch.parent as? ViewGroup)?.removeView(ch)
                                        }
                                    } catch (_: Exception) {}
                                }
                                p = pg.parent
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        )

        // Controles de zoom (+ / -) — ocultar en preview
        if (!isPreview && showComposeZoom.value) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                // botón para alternar estilos (mapa <-> satélite)
                FloatingActionButton(
                    onClick = {
                        styleState.value = if (styleState.value.lowercase().startsWith("sat")) "mapnik" else "satellite"
                        // forzar refresco de tiles
                        mapRef.value?.let { mv ->
                            try { mv.tileProvider.clearTileCache() } catch (_: Exception) {}
                            try { mv.setTileSource(selectTileSource(styleState.value)) } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    val isSat = styleState.value.lowercase().startsWith("sat")
                    Icon(if (isSat) Icons.Default.Map else Icons.Default.Satellite, contentDescription = "Toggle map style")
                }
                Spacer(modifier = Modifier.size(8.dp))
                FloatingActionButton(
                    onClick = {
                        mapRef.value?.let { mv ->
                            try {
                                val current = mv.zoomLevelDouble
                                val next = (current + 1.0).coerceAtMost(21.0)
                                mv.controller.setZoom(next)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in")
                }
                Spacer(modifier = Modifier.size(8.dp))
                FloatingActionButton(
                    onClick = {
                        mapRef.value?.let { mv ->
                            try {
                                val current = mv.zoomLevelDouble
                                val next = (current - 1.0).coerceAtLeast(0.0)
                                mv.controller.setZoom(next)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                }
            }
        }
    }
}

// TileSources disponibles: ESRI World Imagery (satellite) y Stamen Toner (ejemplo)
private val esriWorldImagery = object : OnlineTileSourceBase(
    "ESRI_WorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/")
) {
    override fun getTileURLString(pTileIndex: Long): String {
        val x = MapTileIndex.getX(pTileIndex)
        val y = MapTileIndex.getY(pTileIndex)
        val z = MapTileIndex.getZoom(pTileIndex)
        return "${baseUrl}tile/$z/$y/$x.jpg"
    }
}

private val stamenToner = XYTileSource(
    "StamenToner",
    0, 20, 256, ".png",
    arrayOf("https://stamen-tiles.a.ssl.fastly.net/toner/")
)

private fun selectTileSource(style: String) = when (style.lowercase()) {
    "satellite", "esri", "world_imagery" -> esriWorldImagery
    "stamen" -> stamenToner
    else -> TileSourceFactory.MAPNIK
}
