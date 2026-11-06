package com.aiyu.ridelink.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.update.UpdateChannel
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
    /** Save rides (speed, distance, fuel every 5 s while connected) for Analytics. Stays on the phone. */
    val recordRides: Boolean = true,
    /** Overspeed warning on the display; the limit goes in the status frame. */
    val overSpeedAlert: Boolean = false,
    val overSpeedKmh: Int = DEFAULT_OVERSPEED_KMH,
    /** Missed-call badge (older Ntorq family; the 2025+ status frame has no field for it). */
    val missedCalls: Boolean = true,
    /** Look for a newer GitHub release on app start (at most once an hour). */
    val updatesEnabled: Boolean = true,
    /** null = the channel of the installed build (an alpha install follows alpha). */
    val updateChannel: UpdateChannel? = null,
    val updateLastChecked: Long = 0,
    /** "Not now" on an update offer: no launch check until then. */
    val updateSnoozedUntil: Long = 0,
    /** Developer options: shows the Console (frame log, test frames) in Settings. */
    val developerMode: Boolean = false,
    /** Also write every Console line to a file on the phone, for sharing after a ride. */
    val saveLogs: Boolean = false,
    /** SmartXonnect LCD backlight, stock levels 2 (low) to 5 (high), sent in `5A F1`. */
    val displayBrightness: Int = 4,
    /** SmartXonnect: show who sent an SMS (`5B 53`). */
    val smsAlerts: Boolean = true,
    /** While connected, text a missed caller [rejectSmsText] (stock "auto reply SMS" / DND). */
    val autoReply: Boolean = false,
    /** ISO dates (yyyy-MM-dd) the SmartXonnect greets on (`5A 7E`); blank = not sent. */
    val birthday: String = "",
    val purchaseDate: String = "",
    /** Where the phone was when the scooter last disconnected. */
    val parkedLat: Double? = null,
    val parkedLng: Double? = null,
    val parkedAt: Long = 0,
    val parkedBike: String? = null,
    /** Experimental SmartXonnect extras (stock has the frames but never calls them, or needs TVS cloud data). */
    val ledClear: Boolean = false,
    val deliveryAlerts: Boolean = false,
    /** Alert code for the delivery byte of `5B 9C`; meaning unknown, find it with the Console alert sweep. */
    val deliveryCode: Int = 1,
    val carbon: Boolean = false,
    /** Log a fuel stop when the fuel bars jump while standing; the rider adds litres and price later. */
    val refuelDetect: Boolean = true,
    /** Notify (and open Maps on "petrol pump") when the range or fuel bars run low. */
    val lowFuelAlert: Boolean = true,
    val lowFuelKm: Int = DEFAULT_LOW_FUEL_KM,
    /** Notify when a service item or paper is due, and flash it on the display on connect. */
    val careReminders: Boolean = true,
    /** ISO date of the last care notification, so it comes at most once a day. */
    val careNotifiedOn: String = "",
    /** Reply to WhatsApp chats with the riding message while riding, once per chat per ride. */
    val chatAutoReply: Boolean = false,
    /** Text [checkInNumber] a location link when a ride starts and when it ends. */
    val checkIn: Boolean = false,
    val checkInNumber: String = "",
    val checkInName: String = "",
) {
    companion object {
        val BRIGHTNESS_RANGE = 2..5
        const val DEFAULT_OVERSPEED_KMH = 80
        val OVERSPEED_RANGE = 30..120
        const val DEFAULT_LOW_FUEL_KM = 25
        val LOW_FUEL_RANGE = 10..60
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
        val recordRides = booleanPreferencesKey("record_rides")
        val overSpeedAlert = booleanPreferencesKey("overspeed_alert")
        val overSpeedKmh = intPreferencesKey("overspeed_kmh")
        val missedCalls = booleanPreferencesKey("missed_calls")
        val updates = booleanPreferencesKey("updates_enabled")
        val updateChannel = stringPreferencesKey("update_channel")
        val updateChecked = longPreferencesKey("update_last_checked")
        val updateSnoozed = longPreferencesKey("update_snoozed_until")
        val developer = booleanPreferencesKey("developer_mode")
        val saveLogs = booleanPreferencesKey("save_logs")
        val brightness = intPreferencesKey("display_brightness")
        val smsAlerts = booleanPreferencesKey("sms_alerts")
        val autoReply = booleanPreferencesKey("auto_reply")
        val birthday = stringPreferencesKey("birthday")
        val purchase = stringPreferencesKey("purchase_date")
        val parkedLat = doublePreferencesKey("parked_lat")
        val parkedLng = doublePreferencesKey("parked_lng")
        val parkedAt = longPreferencesKey("parked_at")
        val parkedBike = stringPreferencesKey("parked_bike")
        val ledClear = booleanPreferencesKey("led_clear")
        val delivery = booleanPreferencesKey("delivery_alerts")
        val deliveryCode = intPreferencesKey("delivery_code")
        val carbon = booleanPreferencesKey("carbon")
        val refuelDetect = booleanPreferencesKey("refuel_detect")
        val lowFuel = booleanPreferencesKey("low_fuel_alert")
        val lowFuelKm = intPreferencesKey("low_fuel_km")
        val careReminders = booleanPreferencesKey("care_reminders")
        val careNotified = stringPreferencesKey("care_notified_on")
        val chatReply = booleanPreferencesKey("chat_auto_reply")
        val checkIn = booleanPreferencesKey("check_in")
        val checkInNumber = stringPreferencesKey("check_in_number")
        val checkInName = stringPreferencesKey("check_in_name")
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
            recordRides = p[K.recordRides] ?: true,
            overSpeedAlert = p[K.overSpeedAlert] ?: false,
            overSpeedKmh = p[K.overSpeedKmh] ?: Settings.DEFAULT_OVERSPEED_KMH,
            missedCalls = p[K.missedCalls] ?: true,
            updatesEnabled = p[K.updates] ?: true,
            updateChannel = p[K.updateChannel]?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() },
            updateLastChecked = p[K.updateChecked] ?: 0,
            updateSnoozedUntil = p[K.updateSnoozed] ?: 0,
            developerMode = p[K.developer] ?: false,
            saveLogs = p[K.saveLogs] ?: false,
            displayBrightness = p[K.brightness] ?: 4,
            smsAlerts = p[K.smsAlerts] ?: true,
            autoReply = p[K.autoReply] ?: false,
            birthday = p[K.birthday] ?: "",
            purchaseDate = p[K.purchase] ?: "",
            parkedLat = p[K.parkedLat],
            parkedLng = p[K.parkedLng],
            parkedAt = p[K.parkedAt] ?: 0,
            parkedBike = p[K.parkedBike],
            ledClear = p[K.ledClear] ?: false,
            deliveryAlerts = p[K.delivery] ?: false,
            deliveryCode = p[K.deliveryCode] ?: 1,
            carbon = p[K.carbon] ?: false,
            refuelDetect = p[K.refuelDetect] ?: true,
            lowFuelAlert = p[K.lowFuel] ?: true,
            lowFuelKm = p[K.lowFuelKm] ?: Settings.DEFAULT_LOW_FUEL_KM,
            careReminders = p[K.careReminders] ?: true,
            careNotifiedOn = p[K.careNotified] ?: "",
            chatAutoReply = p[K.chatReply] ?: false,
            checkIn = p[K.checkIn] ?: false,
            checkInNumber = p[K.checkInNumber] ?: "",
            checkInName = p[K.checkInName] ?: "",
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
    suspend fun setRecordRides(on: Boolean) { context.store.edit { it[K.recordRides] = on } }
    suspend fun setOverSpeedAlert(on: Boolean) { context.store.edit { it[K.overSpeedAlert] = on } }
    suspend fun setOverSpeedKmh(kmh: Int) { context.store.edit { it[K.overSpeedKmh] = kmh.coerceIn(Settings.OVERSPEED_RANGE) } }
    suspend fun setMissedCalls(on: Boolean) { context.store.edit { it[K.missedCalls] = on } }
    suspend fun setUpdatesEnabled(on: Boolean) { context.store.edit { it[K.updates] = on } }
    suspend fun setUpdateChannel(c: UpdateChannel) { context.store.edit { it[K.updateChannel] = c.name } }
    suspend fun setUpdateLastChecked(at: Long) { context.store.edit { it[K.updateChecked] = at } }
    suspend fun setUpdateSnoozedUntil(at: Long) { context.store.edit { it[K.updateSnoozed] = at } }
    suspend fun setDeveloperMode(on: Boolean) { context.store.edit { it[K.developer] = on } }
    suspend fun setSaveLogs(on: Boolean) { context.store.edit { it[K.saveLogs] = on } }
    suspend fun setDisplayBrightness(level: Int) { context.store.edit { it[K.brightness] = level.coerceIn(Settings.BRIGHTNESS_RANGE) } }
    suspend fun setSmsAlerts(on: Boolean) { context.store.edit { it[K.smsAlerts] = on } }
    suspend fun setAutoReply(on: Boolean) { context.store.edit { it[K.autoReply] = on } }
    suspend fun setBirthday(iso: String) { context.store.edit { it[K.birthday] = iso } }
    suspend fun setPurchaseDate(iso: String) { context.store.edit { it[K.purchase] = iso } }
    suspend fun setLedClear(on: Boolean) { context.store.edit { it[K.ledClear] = on } }
    suspend fun setDeliveryAlerts(on: Boolean) { context.store.edit { it[K.delivery] = on } }
    suspend fun setDeliveryCode(code: Int) { context.store.edit { it[K.deliveryCode] = code.coerceIn(1, 255) } }
    suspend fun setCarbon(on: Boolean) { context.store.edit { it[K.carbon] = on } }
    suspend fun setRefuelDetect(on: Boolean) { context.store.edit { it[K.refuelDetect] = on } }
    suspend fun setLowFuelAlert(on: Boolean) { context.store.edit { it[K.lowFuel] = on } }
    suspend fun setLowFuelKm(km: Int) { context.store.edit { it[K.lowFuelKm] = km.coerceIn(Settings.LOW_FUEL_RANGE) } }
    suspend fun setCareReminders(on: Boolean) { context.store.edit { it[K.careReminders] = on } }
    suspend fun setCareNotifiedOn(iso: String) { context.store.edit { it[K.careNotified] = iso } }
    suspend fun setChatAutoReply(on: Boolean) { context.store.edit { it[K.chatReply] = on } }
    suspend fun setCheckIn(on: Boolean) { context.store.edit { it[K.checkIn] = on } }
    suspend fun setCheckInContact(number: String, name: String) {
        context.store.edit { it[K.checkInNumber] = number; it[K.checkInName] = name }
    }
    suspend fun setParked(lat: Double, lng: Double, at: Long, bike: String?) {
        context.store.edit {
            it[K.parkedLat] = lat; it[K.parkedLng] = lng; it[K.parkedAt] = at
            if (bike == null) it.remove(K.parkedBike) else it[K.parkedBike] = bike
        }
    }
}
