package com.aiyu.ridelink.core.care

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Last odometer and fuel bars the display reported for a bike, kept so the next connect can spot a refuel. */
data class BikeReading(val odometerTenths: Int?, val fuelBars: Int?, val t: Long)

/**
 * Fuel log, service items and paper expiry dates, per bike (framework SQLite, `care.db`). Lists for the
 * selected bike are exposed as flows and reload when the bike changes.
 */
@Singleton
class CareStore @Inject constructor(@ApplicationContext context: Context, settings: SettingsRepository) {
    private val db = Helper(context)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var bike: String? = null

    private val _fills = MutableStateFlow<List<Fill>>(emptyList())
    /** Newest first. */
    val fills: StateFlow<List<Fill>> = _fills.asStateFlow()

    private val _records = MutableStateFlow(CareItem.entries.map { CareRecord(it) })
    val records: StateFlow<List<CareRecord>> = _records.asStateFlow()

    private val _reading = MutableStateFlow<BikeReading?>(null)
    /** Last reading of the selected bike, also while it is not connected. */
    val reading: StateFlow<BikeReading?> = _reading.asStateFlow()

    init {
        io.launch {
            settings.settings.map { it.deviceAddress }.distinctUntilChanged().collect {
                bike = it
                reload()
            }
        }
    }

    suspend fun addFill(odometerTenths: Int, litres: Double?, cost: Double?, full: Boolean, auto: Boolean, t: Long = System.currentTimeMillis()): Long =
        withContext(Dispatchers.IO) {
            val v = ContentValues().apply {
                put("bike", bike); put("t", t); put("odo", odometerTenths); put("litres", litres); put("cost", cost)
                put("full", if (full) 1 else 0); put("auto", if (auto) 1 else 0)
            }
            db.writableDatabase.insert(T_FILLS, null, v).also { reload() }
        }

    suspend fun updateFill(f: Fill) = withContext(Dispatchers.IO) {
        val v = ContentValues().apply {
            put("odo", f.odometerTenths); put("litres", f.litres); put("cost", f.cost); put("full", if (f.full) 1 else 0)
        }
        db.writableDatabase.update(T_FILLS, v, "id = ?", arrayOf(f.id.toString()))
        reload()
    }

    suspend fun deleteFill(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(T_FILLS, "id = ?", arrayOf(id.toString()))
        reload()
    }

    suspend fun saveRecord(r: CareRecord) = withContext(Dispatchers.IO) {
        val v = ContentValues().apply {
            put("bike", bike.orEmpty()); put("item", r.item.name); put("every_km", r.everyKm)
            put("done_odo", r.doneOdometerTenths); put("due", r.due?.toString())
        }
        db.writableDatabase.insertWithOnConflict(T_ITEMS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        reload()
    }

    /** Stores the latest display reading for [forBike]; written only when a value changed. */
    suspend fun saveReading(forBike: String, r: BikeReading) = withContext(Dispatchers.IO) {
        val v = ContentValues().apply { put("bike", forBike); put("odo", r.odometerTenths); put("fuel", r.fuelBars); put("t", r.t) }
        db.writableDatabase.insertWithOnConflict(T_READINGS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        if (forBike == bike) _reading.value = r
    }

    suspend fun readingOf(forBike: String): BikeReading? = withContext(Dispatchers.IO) { loadReading(forBike) }

    private fun loadReading(forBike: String): BikeReading? =
        db.readableDatabase.query(T_READINGS, null, "bike = ?", arrayOf(forBike), null, null, null).use { c ->
            if (!c.moveToFirst()) null else BikeReading(c.intOrNull("odo"), c.intOrNull("fuel"), c.getLong(c.getColumnIndexOrThrow("t")))
        }

    private fun reload() {
        val b = bike
        val r = db.readableDatabase
        // Rows saved without a bike (nothing paired yet) show for every bike.
        val (where, args) = if (b == null) "bike IS NULL" to emptyArray<String>() else "(bike = ? OR bike IS NULL)" to arrayOf(b)
        _fills.value = r.query(T_FILLS, null, where, args, null, null, "odo DESC, t DESC").use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Fill(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        bike = c.getString(c.getColumnIndexOrThrow("bike")),
                        t = c.getLong(c.getColumnIndexOrThrow("t")),
                        odometerTenths = c.getInt(c.getColumnIndexOrThrow("odo")),
                        litres = c.doubleOrNull("litres"),
                        cost = c.doubleOrNull("cost"),
                        full = c.getInt(c.getColumnIndexOrThrow("full")) == 1,
                        auto = c.getInt(c.getColumnIndexOrThrow("auto")) == 1,
                    ),
                )
            }
        }
        val saved = r.query(T_ITEMS, null, "bike = ?", arrayOf(b.orEmpty()), null, null, null).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val item = runCatching { CareItem.valueOf(c.getString(c.getColumnIndexOrThrow("item"))) }.getOrNull() ?: continue
                    put(
                        item,
                        CareRecord(
                            item = item,
                            everyKm = c.intOrNull("every_km"),
                            doneOdometerTenths = c.intOrNull("done_odo"),
                            due = c.getString(c.getColumnIndexOrThrow("due"))?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        ),
                    )
                }
            }
        }
        _records.value = CareItem.entries.map { saved[it] ?: CareRecord(it) }
        _reading.value = b?.let { loadReading(it) }
    }

    private fun Cursor.intOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.doubleOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "care.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $T_FILLS (id INTEGER PRIMARY KEY AUTOINCREMENT, bike TEXT, t INTEGER NOT NULL,
                odo INTEGER NOT NULL, litres REAL, cost REAL, full INTEGER NOT NULL, auto INTEGER NOT NULL)""",
            )
            db.execSQL(
                """CREATE TABLE $T_ITEMS (bike TEXT NOT NULL, item TEXT NOT NULL, every_km INTEGER, done_odo INTEGER,
                due TEXT, PRIMARY KEY (bike, item))""",
            )
            db.execSQL("CREATE TABLE $T_READINGS (bike TEXT PRIMARY KEY, odo INTEGER, fuel INTEGER, t INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val T_FILLS = "fills"
        const val T_ITEMS = "items"
        const val T_READINGS = "readings"
    }
}
