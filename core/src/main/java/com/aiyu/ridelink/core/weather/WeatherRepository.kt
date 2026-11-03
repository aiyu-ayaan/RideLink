package com.aiyu.ridelink.core.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import com.aiyu.ridelink.core.protocol.WeatherKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Weather, AQI and place name for the display. Open-Meteo needs no API key; location is the coarse
 * network fix, so no Play Services. Everything returns null when offline or without location access.
 */
@Singleton
class WeatherRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun hasLocation() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(): Location? {
        if (!hasLocation()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.PASSIVE_PROVIDER
                // android.os.CancellationSignal picks the non-deprecated overload (a bare null matches both).
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                val done = Consumer<Location?> { location -> if (cont.isActive) cont.resume(location) }
                runCatching { LocationManagerCompat.getCurrentLocation(lm, provider, cancel, executor, done) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        if (fresh != null) return fresh
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Neighbourhood or town name, e.g. "Indiranagar". */
    @Suppress("DEPRECATION")
    suspend fun placeName(at: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull()
                ?.let { it.subLocality ?: it.locality ?: it.subAdminArea }
        }.getOrNull()
    }

    suspend fun weather(at: Location): ClusterProtocol.Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val lat = "%.4f".format(Locale.US, at.latitude)
            val lon = "%.4f".format(Locale.US, at.longitude)
            val forecast = JSONObject(get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&hourly=precipitation_probability&forecast_hours=1&timezone=auto",
            ))
            val current = forecast.getJSONObject("current")
            val rain = forecast.optJSONObject("hourly")?.optJSONArray("precipitation_probability")?.optInt(0) ?: 0
            // shortcut: Open-Meteo has US and European AQI only, not the Indian NAQI scale the stock app may show.
            val aqi = runCatching {
                JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi"))
                    .getJSONObject("current").optInt("us_aqi", 0)
            }.getOrDefault(0)
            val kind = WeatherKind.fromWmo(current.getInt("weather_code"), current.optInt("is_day", 1) == 1)
            ClusterProtocol.Weather(
                kind = kind,
                temperatureC = Math.round(current.getDouble("temperature_2m")).toInt(),
                aqi = aqi,
                rainPercent = rain,
                description = describe(kind),
            )
        }.getOrNull()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Short English text for the display (13 bytes max); the display font is ASCII-only. */
    private fun describe(kind: WeatherKind) = when (kind) {
        WeatherKind.CLEAR -> "Sunny"
        WeatherKind.CLEAR_NIGHT -> "Clear"
        WeatherKind.CLOUDY -> "Cloudy"
        WeatherKind.FOG -> "Fog"
        WeatherKind.RAIN -> "Rain"
        WeatherKind.SNOW -> "Snow"
        WeatherKind.THUNDERSTORM -> "Thunderstorm"
        WeatherKind.HAIL -> "Hailstorm"
    }
}
