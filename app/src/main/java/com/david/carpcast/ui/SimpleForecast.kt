package com.david.carpcast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import java.util.Locale
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.david.carpcast.R
import androidx.compose.material3.LinearProgressIndicator

data class SimpleForecast(
    val generatedAt: String,
    val locationName: String,
    val dayAvg: Int,
    val bestHourTime: String,
    val bestHourScore: Int,
    val worstHourTime: String,
    val worstHourScore: Int,
    val avgConfidencePct: Int,
    val precipSum: Double,
    val cloudAvg: Double,
    val humidityAvg: Double,
    val tempMin: Double,
    val tempMax: Double,
    val windMin: Double,
    val windAvgMin: Double,
    val windAvgMax: Double,
    val windMax: Double,
    val pressureMin: Double,
    val pressureMax: Double,
    val bestWindows: List<Pair<String, Int>>, // Pair<"HH:mm - HH:mm", score>
    val sunrise: String,
    val sunset: String,
    val moonInfo: String,
    val hydroText: String?,
    // Serie horaria para la nueva sección "por horas"
    val hourly: List<HourlyEntry> = emptyList(),
    // Nuevos: lista de otros días para mostrar en la UI
    val otherDays: List<SimpleForecast> = emptyList()
)

// Representa un punto horario simple usado en la UI
data class HourlyEntry(
    val time: String, // ISO o "HH:mm"
    val temperature: Double? = null,
    val precipitation: Double? = null,
    val pressure: Double? = null,
    val windSpeed: Double? = null,
    val score: Int? = null,
    val confidencePct: Int? = null,
    // Nuevos campos
    val precipitationProbability: Double? = null, // 0..1
    val cloudCover: Double? = null, // 0..100
    val isDay: Boolean? = null,
    val condition: String? = null, // "clear" | "cloudy" | "rain" | "snow" | etc.
    val windDirectionDeg: Double? = null, // grados 0..360
    val waterTempSource: String? = null,
    val weatherCode: Int? = null,
    val windGust: Double? = null,
    val influences: List<com.david.carpcast.scoring.FactorInfluence> = emptyList()
)