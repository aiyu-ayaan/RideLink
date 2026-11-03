package com.aiyu.ridelink.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.ridelink.core.protocol.ClusterModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    /** null = Auto: detected from the display, see ClusterModel.detect. */
    val model: ClusterModel? = null,
    val riderName: String = "",
    /** Custom text lines on the display (DID 0x4C / 0x63), re-sent on every connect. Blank = not sent. */
    val displayLine1: String = "",
    val displayLine2: String = "",
    val forwardNavigation: Boolean = true,
    val forwardCalls: Boolean = true,
    /** WhatsApp / Facebook / Instagram unread counts (`5B 9C`). */
    val showSocial: Boolean = true,
    /** Weather + AQI screen (`5B 5D`) and temperature in the status frame. */
    val showWeather: Boolean = true,
    /** Current place name (`5B 5C`). */
    val showLocation: Boolean = true,
    /** Song, artist and play state (`5B 62`, `5B 63`, `5B 60`). */
    val showMusic: Boolean = true,
    /** Accept / reject a ringing call with the scooter's switch (5A 10 byte 12). */
    val callButtons: Boolean = true,
    /** "Reject with SMS" on the scooter texts the caller [rejectSmsText] (blank = RejectMessage.DEFAULT). */
    val rejectSms: Boolean = false,
    val rejectSmsText: String = "",
    /** Scooter voice button opens the phone's assistant. */
    val voiceButton: Boolean = true,
    /** Ride data card (odometer, trip, fuel, range, speeds, mileage) on the Ride screen. */
    val showRideData: Boolean = true,
    /** Vehicle card (RPM, battery, engine temperature, service, engine light) on the Ride screen. */
    val showVehicle: Boolean = true,
    /** Overspeed warning on the display; the limit goes in the status frame. */
    val overSpeedAlert: Boolean = false,
    val overSpeedKmh: Int = DEFAULT_OVERSPEED_KMH,
    /** Missed-call badge (older Ntorq family; the 2025+ status frame has no field for it). */
    val missedCalls: Boolean = true,
) {
    companion object {
        const val DEFAULT_OVERSPEED_KMH = 80
        val OVERSPEED_RANGE = 30..120
    }
}

private val Context.store by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val address = stringPreferencesKey("device_address")
        val name = stringPreferencesKey("device_name")
        val model = stringPreferencesKey("model")
        val rider = stringPreferencesKey("rider_name")
        val line1 = stringPreferencesKey("display_line_1")
        val line2 = stringPreferencesKey("display_line_2")
        val nav = booleanPreferencesKey("forward_nav")
        val calls = booleanPreferencesKey("forward_calls")
        val social = booleanPreferencesKey("show_social")
        val weather = booleanPreferencesKey("show_weather")
        val location = booleanPreferencesKey("show_location")
        val music = booleanPreferencesKey("show_music")
        val callButtons = booleanPreferencesKey("call_buttons")
        val rejectSms = booleanPreferencesKey("reject_sms")
        val rejectSmsText = stringPreferencesKey("reject_sms_text")
        val voiceButton = booleanPreferencesKey("voice_button")
        val rideData = booleanPreferencesKey("show_ride_data")
        val vehicle = booleanPreferencesKey("show_vehicle")
        val overSpeedAlert = booleanPreferencesKey("overspeed_alert")
        val overSpeedKmh = intPreferencesKey("overspeed_kmh")
        val missedCalls = booleanPreferencesKey("missed_calls")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            deviceAddress = p[K.address],
            deviceName = p[K.name],
            model = p[K.model]?.let { runCatching { ClusterModel.valueOf(it) }.getOrNull() },
            riderName = p[K.rider] ?: "",
            displayLine1 = p[K.line1] ?: "",
            displayLine2 = p[K.line2] ?: "",
            forwardNavigation = p[K.nav] ?: true,
            forwardCalls = p[K.calls] ?: true,
            showSocial = p[K.social] ?: true,
            showWeather = p[K.weather] ?: true,
            showLocation = p[K.location] ?: true,
            showMusic = p[K.music] ?: true,
            callButtons = p[K.callButtons] ?: true,
            rejectSms = p[K.rejectSms] ?: false,
            rejectSmsText = p[K.rejectSmsText] ?: "",
            voiceButton = p[K.voiceButton] ?: true,
            showRideData = p[K.rideData] ?: true,
            showVehicle = p[K.vehicle] ?: true,
            overSpeedAlert = p[K.overSpeedAlert] ?: false,
            overSpeedKmh = p[K.overSpeedKmh] ?: Settings.DEFAULT_OVERSPEED_KMH,
            missedCalls = p[K.missedCalls] ?: true,
        )
    }

    suspend fun current() = settings.first()

    suspend fun setDevice(address: String?, name: String?) {
        context.store.edit {
            if (address == null) it.remove(K.address) else it[K.address] = address
            if (name == null) it.remove(K.name) else it[K.name] = name
        }
    }

    suspend fun setModel(model: ClusterModel?) { context.store.edit { if (model == null) it.remove(K.model) else it[K.model] = model.name } }
    suspend fun setRiderName(name: String) { context.store.edit { it[K.rider] = name } }
    suspend fun setDisplayLines(line1: String, line2: String) {
        context.store.edit {
            it[K.line1] = line1
            it[K.line2] = line2
        }
    }
    suspend fun setForwardNavigation(on: Boolean) { context.store.edit { it[K.nav] = on } }
    suspend fun setForwardCalls(on: Boolean) { context.store.edit { it[K.calls] = on } }
    suspend fun setShowSocial(on: Boolean) { context.store.edit { it[K.social] = on } }
    suspend fun setShowWeather(on: Boolean) { context.store.edit { it[K.weather] = on } }
    suspend fun setShowLocation(on: Boolean) { context.store.edit { it[K.location] = on } }
    suspend fun setShowMusic(on: Boolean) { context.store.edit { it[K.music] = on } }
    suspend fun setCallButtons(on: Boolean) { context.store.edit { it[K.callButtons] = on } }
    suspend fun setRejectSms(on: Boolean) { context.store.edit { it[K.rejectSms] = on } }
    suspend fun setRejectSmsText(text: String) { context.store.edit { it[K.rejectSmsText] = text } }
    suspend fun setVoiceButton(on: Boolean) { context.store.edit { it[K.voiceButton] = on } }
    suspend fun setShowRideData(on: Boolean) { context.store.edit { it[K.rideData] = on } }
    suspend fun setShowVehicle(on: Boolean) { context.store.edit { it[K.vehicle] = on } }
    suspend fun setOverSpeedAlert(on: Boolean) { context.store.edit { it[K.overSpeedAlert] = on } }
    suspend fun setOverSpeedKmh(kmh: Int) { context.store.edit { it[K.overSpeedKmh] = kmh.coerceIn(Settings.OVERSPEED_RANGE) } }
    suspend fun setMissedCalls(on: Boolean) { context.store.edit { it[K.missedCalls] = on } }
}
