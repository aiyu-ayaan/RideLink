package com.aiyu.ridelink.core.settings

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aiyu.ridelink.core.protocol.ClusterModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A paired scooter. [model] null = Auto. */
data class Bike(val address: String, val name: String?, val model: ClusterModel?)

/**
 * Every scooter the rider has paired (framework SQLite; lists stay out of DataStore).
 * The active one is still `Settings.deviceAddress`, so the service and boot paths only ever see one bike.
 */
@Singleton
class GarageStore @Inject constructor(@ApplicationContext context: Context, settings: SettingsRepository) {
    private val db = Helper(context)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bikes = MutableStateFlow<List<Bike>>(emptyList())
    /** In pairing order. */
    val bikes: StateFlow<List<Bike>> = _bikes.asStateFlow()

    init {
        io.launch {
            // Installs from before multi-bike have one scooter in DataStore only; adopt it.
            val s = settings.current()
            s.deviceAddress?.let { add(Bike(it, s.deviceName, s.model)) } ?: reload()
        }
    }

    /** Adds or updates the bike with this address. */
    suspend fun add(bike: Bike) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("address", bike.address); put("name", bike.name); put("model", bike.model?.name)
        }
        db.writableDatabase.insertWithOnConflict(T_BIKES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        reload()
    }

    suspend fun setModel(address: String, model: ClusterModel?) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(T_BIKES, ContentValues().apply { put("model", model?.name) }, "address = ?", arrayOf(address))
        reload()
    }

    suspend fun remove(address: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(T_BIKES, "address = ?", arrayOf(address))
        reload()
    }

    private fun reload() {
        _bikes.value = db.readableDatabase.query(T_BIKES, null, null, null, null, null, "added ASC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Bike(
                            address = c.getString(c.getColumnIndexOrThrow("address")),
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            model = c.getString(c.getColumnIndexOrThrow("model"))?.let { runCatching { ClusterModel.valueOf(it) }.getOrNull() },
                        ),
                    )
                }
            }
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "garage.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $T_BIKES (address TEXT PRIMARY KEY, name TEXT, model TEXT,
                added INTEGER NOT NULL DEFAULT (strftime('%s','now')))""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val T_BIKES = "bikes"
    }
}
