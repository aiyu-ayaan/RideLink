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
    /** Off by default: the stock app never sends message senders to Ntorq clusters. */
    val forwardMessages: Boolean = false,
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
        val messages = booleanPreferencesKey("forward_messages")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            deviceAddress = p[K.address],
            deviceName = p[K.name],
            model = p[K.model]?.let { runCatching { ClusterModel.valueOf(it) }.getOrNull() },
            riderName = p[K.rider] ?: "",
            forwardNavigation = p[K.nav] ?: true,
            forwardCalls = p[K.calls] ?: true,
            forwardMessages = p[K.messages] ?: false,
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
    suspend fun setForwardMessages(on: Boolean) { context.store.edit { it[K.messages] = on } }
}
