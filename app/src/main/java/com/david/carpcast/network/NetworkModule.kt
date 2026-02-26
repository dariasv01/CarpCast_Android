// File: src/main/java/com/david/carpcast/network/NetworkModule.kt
package com.david.carpcast.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // Default timeout in seconds (aumentado para evitar timeouts cortos)
    private const val TIMEOUT = 60L

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val defaultHeaders = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "CarpCast/2.0")
            .build()
        chain.proceed(request)
    }

    // Build an OkHttpClient per desired timeout so we can tune individual Retrofit instances.
    private fun httpClient(timeoutSeconds: Long = TIMEOUT): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(defaultHeaders)
            .addInterceptor(logging)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val openMeteo: Retrofit by lazy { retrofit("https://api.open-meteo.com/") }
    val nominatim: Retrofit by lazy { retrofit("https://nominatim.openstreetmap.org/") }
    val sunriseSunset: Retrofit by lazy { retrofit("https://api.sunrise-sunset.org/") }
    val usgs: Retrofit by lazy { retrofit("https://waterservices.usgs.gov/") }
    val usgsSite: Retrofit by lazy { retrofit("https://waterservices.usgs.gov/") }
    val usno: Retrofit by lazy { retrofit("https://aa.usno.navy.mil/api/") }//https://aa.usno.navy.mil/api
    // FarmSense (http) can be slower / unreliable; usar timeout más largo
    val farmsense: Retrofit by lazy { retrofit("http://api.farmsense.net/") }
    // Open-Meteo Flood API (fallback para hidrología fuera de EEUU)
    val floodOpenMeteo: Retrofit by lazy { retrofit("https://flood-api.open-meteo.com/v1/") }

    fun <T> create(baseRetrofit: Retrofit, clazz: Class<T>): T = baseRetrofit.create(clazz)
}