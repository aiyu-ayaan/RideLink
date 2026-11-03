package com.aiyu.ridelink.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
)

private val Context.store by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val address = stringPreferencesKey("device_address")
        val name = stringPreferencesKey("device_name")
        val model = stringPreferencesKey("model")
        val rider = stringPreferencesKey("rider_name")
        val nav = booleanPreferencesKey("forward_nav")
        val calls = booleanPreferencesKey("forward_calls")
        val social = booleanPreferencesKey("show_social")
        val weather = booleanPreferencesKey("show_weather")
        val location = booleanPreferencesKey("show_location")
        val music = booleanPreferencesKey("show_music")
        val callButtons = booleanPreferencesKey("call_buttons")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            deviceAddress = p[K.address],
            deviceName = p[K.name],
            model = p[K.model]?.let { runCatching { ClusterModel.valueOf(it) }.getOrNull() },
            riderName = p[K.rider] ?: "",
            forwardNavigation = p[K.nav] ?: true,
            forwardCalls = p[K.calls] ?: true,
            showSocial = p[K.social] ?: true,
            showWeather = p[K.weather] ?: true,
            showLocation = p[K.location] ?: true,
            showMusic = p[K.music] ?: true,
            callButtons = p[K.callButtons] ?: true,
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
    suspend fun setForwardNavigation(on: Boolean) { context.store.edit { it[K.nav] = on } }
    suspend fun setForwardCalls(on: Boolean) { context.store.edit { it[K.calls] = on } }
    suspend fun setShowSocial(on: Boolean) { context.store.edit { it[K.social] = on } }
    suspend fun setShowWeather(on: Boolean) { context.store.edit { it[K.weather] = on } }
    suspend fun setShowLocation(on: Boolean) { context.store.edit { it[K.location] = on } }
    suspend fun setShowMusic(on: Boolean) { context.store.edit { it[K.music] = on } }
    suspend fun setCallButtons(on: Boolean) { context.store.edit { it[K.callButtons] = on } }
}
