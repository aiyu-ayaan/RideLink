package com.aiyu.ridelink.core.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** One SQLite row as column -> value (Long, Double, String or null), in column order. */
typealias Row = Map<String, Any?>

/**
 * Plain column maps for the cloud backup. Rows go to Firestore as they are, so a new column is backed up
 * without touching the backup code; [insertRow] drops columns this install does not have (backup from a newer app).
 */
internal fun SQLiteDatabase.rows(table: String, where: String? = null, args: Array<String>? = null, drop: Set<String> = emptySet()): List<Row> =
    query(table, null, where, args, null, null, null).use { c ->
        buildList { while (c.moveToNext()) add(c.row(drop)) }
    }

internal fun Cursor.row(drop: Set<String> = emptySet()): Row = buildMap {
    for (i in 0 until columnCount) {
        val name = getColumnName(i)
        if (name in drop) continue
        put(
            name,
            when (getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                Cursor.FIELD_TYPE_STRING -> getString(i)
                else -> null
            },
        )
    }
}

internal fun SQLiteDatabase.columns(table: String): Set<String> =
    rawQuery("PRAGMA table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }

internal fun SQLiteDatabase.insertRow(table: String, row: Row, columns: Set<String>, extra: Row = emptyMap()): Long {
    val v = ContentValues()
    for ((k, value) in row + extra) {
        if (k !in columns) continue
        when (value) {
            null -> v.putNull(k)
            is Long -> v.put(k, value)
            is Int -> v.put(k, value)
            is Double -> v.put(k, value)
            is Boolean -> v.put(k, if (value) 1 else 0)
            else -> v.put(k, value.toString())
        }
    }
    return insertOrThrow(table, null, v)
}
