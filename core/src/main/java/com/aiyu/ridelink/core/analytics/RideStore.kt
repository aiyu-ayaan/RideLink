package com.aiyu.ridelink.core.analytics

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ride history on the phone (framework SQLite, no extra library). One row per finished ride with its summary,
 * plus the 5 s samples for the ride detail charts. Nothing leaves the phone except through CSV export.
 */
@Singleton
class RideStore @Inject constructor(@ApplicationContext context: Context) {
    private val db = Helper(context)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _rides = MutableStateFlow<List<RideRecord>>(emptyList())
    /** Newest first. */
    val rides: StateFlow<List<RideRecord>> = _rides.asStateFlow()

    init { io.launch { reload() } }

    suspend fun save(summary: RideSummary, samples: List<RideSample>, demo: Boolean): Long = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        var id = -1L
        w.beginTransaction()
        try {
            id = w.insert(T_RIDES, null, summary.toValues(demo))
            samples.forEach { w.insert(T_SAMPLES, null, it.toValues(id)) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        reload()
        id
    }

    suspend fun samples(rideId: Long): List<RideSample> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(T_SAMPLES, null, "ride = ?", arrayOf(rideId.toString()), null, null, "t ASC").use { c ->
            buildList { while (c.moveToNext()) add(c.sample()) }
        }
    }

    suspend fun delete(rideId: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(T_SAMPLES, "ride = ?", arrayOf(rideId.toString()))
        db.writableDatabase.delete(T_RIDES, "id = ?", arrayOf(rideId.toString()))
        reload()
    }

    /** Removes sample and demo-ride data only; real rides stay. */
    suspend fun deleteDemo() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(T_SAMPLES, "ride IN (SELECT id FROM $T_RIDES WHERE demo = 1)", null)
        db.writableDatabase.delete(T_RIDES, "demo = 1", null)
        reload()
    }

    /** CSV with one line per ride, oldest first; units in the header. */
    suspend fun exportCsv(out: Writer, zone: ZoneId) = withContext(Dispatchers.IO) {
        out.appendLine(
            "start,end,distance_km,duration_min,moving_min,avg_speed_kmh,max_speed_kmh,avg_economy_kmpl,fuel_used_level," +
                "odometer_start_km,odometer_end_km,sample",
        )
        for (r in _rides.value.asReversed()) {
            val s = r.summary
            fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()
            out.appendLine(
                listOf(
                    time(s.start), time(s.end), "%.1f".format(java.util.Locale.US, s.distanceKm),
                    "%.1f".format(java.util.Locale.US, s.durationS / 60.0), "%.1f".format(java.util.Locale.US, s.movingS / 60.0),
                    "%.1f".format(java.util.Locale.US, s.avgMovingSpeedKmh), s.maxSpeedKmh,
                    s.avgEconomy?.let { "%.1f".format(java.util.Locale.US, it) }.orEmpty(), s.fuelUsed?.toString().orEmpty(),
                    s.odometerStartTenths?.let { "%.1f".format(java.util.Locale.US, it / 10.0) }.orEmpty(),
                    s.odometerEndTenths?.let { "%.1f".format(java.util.Locale.US, it / 10.0) }.orEmpty(),
                    if (r.demo) "yes" else "no",
                ).joinToString(","),
            )
        }
        out.flush()
    }

    private fun reload() {
        _rides.value = db.readableDatabase.query(T_RIDES, null, null, null, null, null, "start DESC").use { c ->
            buildList { while (c.moveToNext()) add(c.ride()) }
        }
    }

    private fun RideSummary.toValues(demo: Boolean) = ContentValues().apply {
        put("start", start); put("end_t", end); put("demo", if (demo) 1 else 0)
        put("distance_m", distanceM); put("duration_s", durationS); put("moving_s", movingS)
        put("max_speed", maxSpeedKmh); put("avg_speed", avgMovingSpeedKmh)
        avgEconomy?.let { put("avg_economy", it) }; fuelUsed?.let { put("fuel_used", it) }
        odometerStartTenths?.let { put("odo_start", it) }; odometerEndTenths?.let { put("odo_end", it) }
        put("bands", speedBandS.joinToString(",")); put("hours", hourS.joinToString(","))
    }

    private fun RideSample.toValues(ride: Long) = ContentValues().apply {
        put("ride", ride); put("t", t)
        speedKmh?.let { put("speed", it) }; odometerTenths?.let { put("odo", it) }; tripTenths?.let { put("trip", it) }
        fuel?.let { put("fuel", it) }; rangeKm?.let { put("range_km", it) }; rpm?.let { put("rpm", it) }
        instantEconomy?.let { put("inst_eco", it) }; averageEconomy?.let { put("avg_eco", it) }
    }

    private fun Cursor.int(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.dbl(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.ints(col: String) = getString(getColumnIndexOrThrow(col)).orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun Cursor.ride() = RideRecord(
        id = long("id"),
        demo = int("demo") == 1,
        summary = RideSummary(
            start = long("start"), end = long("end_t"), distanceM = int("distance_m") ?: 0, durationS = int("duration_s") ?: 0,
            movingS = int("moving_s") ?: 0, maxSpeedKmh = int("max_speed") ?: 0, avgMovingSpeedKmh = dbl("avg_speed") ?: 0.0,
            avgEconomy = dbl("avg_economy"), fuelUsed = int("fuel_used"), odometerStartTenths = int("odo_start"),
            odometerEndTenths = int("odo_end"), speedBandS = ints("bands"), hourS = ints("hours"),
        ),
    )

    private fun Cursor.sample() = RideSample(
        t = long("t"), speedKmh = int("speed"), odometerTenths = int("odo"), tripTenths = int("trip"), fuel = int("fuel"),
        rangeKm = int("range_km"), rpm = int("rpm"), instantEconomy = int("inst_eco"), averageEconomy = int("avg_eco"),
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "rides.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $T_RIDES (id INTEGER PRIMARY KEY AUTOINCREMENT, start INTEGER NOT NULL, end_t INTEGER NOT NULL,
                demo INTEGER NOT NULL DEFAULT 0, distance_m INTEGER, duration_s INTEGER, moving_s INTEGER, max_speed INTEGER,
                avg_speed REAL, avg_economy REAL, fuel_used INTEGER, odo_start INTEGER, odo_end INTEGER, bands TEXT, hours TEXT)""",
            )
            db.execSQL(
                """CREATE TABLE $T_SAMPLES (ride INTEGER NOT NULL, t INTEGER NOT NULL, speed INTEGER, odo INTEGER, trip INTEGER,
                fuel INTEGER, range_km INTEGER, rpm INTEGER, inst_eco INTEGER, avg_eco INTEGER)""",
            )
            db.execSQL("CREATE INDEX samples_ride ON $T_SAMPLES (ride, t)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val T_RIDES = "rides"
        const val T_SAMPLES = "samples"
    }
}
