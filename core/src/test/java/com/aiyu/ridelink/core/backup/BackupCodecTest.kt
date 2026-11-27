package com.aiyu.ridelink.core.backup

import com.aiyu.ridelink.core.analytics.RideRows
import com.aiyu.ridelink.core.care.CareRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val ride = RideRows(
        ride = mapOf("start" to 1_700_000_000_000L, "end_t" to 1_700_000_600_000L, "avg_speed" to 31.5, "name" to null, "starred" to 1L),
        samples = listOf(
            mapOf("t" to 1L, "speed" to 30L, "odo" to null),
            mapOf("t" to 2L, "speed" to 41L, "odo" to 12_345L),
        ),
        turns = listOf(mapOf("t" to 1L, "instruction" to "Turn left", "maneuver" to "LEFT", "to_turn" to 200L, "left_m" to 900L)),
    )
    private val care = CareRows(
        fills = listOf(mapOf("bike" to "AA:BB", "t" to 5L, "odo" to 100L, "litres" to 4.2, "cost" to null, "full" to 1L, "auto" to 0L)),
        items = listOf(mapOf("bike" to "AA:BB", "item" to "ENGINE_OIL", "every_km" to 3000L, "done_odo" to null, "due" to null)),
        readings = listOf(mapOf("bike" to "AA:BB", "odo" to 100L, "fuel" to 3L, "t" to 9L)),
    )
    private val bikes = listOf(mapOf("address" to "AA:BB", "name" to "TVS", "model" to null, "added" to 1L))
    private val prefs = mapOf<String, Any>("rider_name" to "Ayaan", "overspeed_kmh" to 80, "parked_lat" to 12.5, "message_muted" to setOf("SMS"), "show_music" to false)

    private fun docs() = BackupCodec.docs(listOf(ride), care, bikes, prefs)

    @Test
    fun paths_come_from_the_data() {
        assertEquals(
            setOf("rides/1700000000000", "fills/AA:BB_5", "care/AA:BB_ENGINE_OIL", "readings/AA:BB", "bikes/AA:BB", "settings/prefs"),
            docs().keys,
        )
    }

    @Test
    fun round_trips_through_the_cloud_form() {
        val cloud = docs().values.map { BackupCodec.fromCloud(it.path, BackupCodec.toCloud(it)) }
        val back = BackupCodec.local(cloud)
        assertEquals(listOf(ride), back.rides)
        assertEquals(care, back.care)
        assertEquals(bikes, back.bikes)
        assertEquals(prefs, back.prefs)
        // The restored data hashes the same, so the next backup uploads nothing.
        val again = BackupCodec.docs(back.rides, back.care, back.bikes, back.prefs!!)
        assertEquals(docs().mapValues { it.value.hash }, again.mapValues { it.value.hash })
    }

    @Test
    fun samples_are_gzipped_in_the_cloud() {
        val cloud = BackupCodec.toCloud(docs().getValue("rides/1700000000000"))
        assertTrue(cloud["samples"] is ByteArray)
        assertTrue(cloud[BackupCodec.HASH] is String)
    }

    @Test
    fun incremental_sends_only_changes_and_deletions() {
        val local = docs()
        val synced = local.mapValues { it.value.hash } + ("rides/1" to "x") - "bikes/AA:BB"
        val plan = BackupCodec.incremental(local, synced)
        assertEquals(listOf("bikes/AA:BB"), plan.upload.map { it.path })
        assertEquals(listOf("rides/1"), plan.delete)
        assertEquals(0, BackupCodec.incremental(local, local.mapValues { it.value.hash }).size)
    }

    @Test
    fun merge_keeps_both_and_the_phone_wins_a_tie() {
        val local = docs()
        val renamed = BackupDoc("bikes/AA:BB", bikes.first() + ("name" to "Cloud name"))
        val other = BackupDoc("rides/42", mapOf("start" to 42L))
        val merged = BackupCodec.merge(local, mapOf(renamed.path to renamed, other.path to other))
        assertEquals(local.size + 1, merged.size)
        assertEquals("TVS", merged.getValue("bikes/AA:BB").fields["name"])
        val plan = BackupCodec.mirror(merged, mapOf(renamed.path to renamed.hash, other.path to other.hash))
        assertTrue(plan.delete.isEmpty())
        assertEquals(local.size, plan.upload.size)
    }

    @Test
    fun mirror_deletes_what_the_phone_lacks() {
        val plan = BackupCodec.mirror(docs(), mapOf("rides/9" to "h"))
        assertEquals(listOf("rides/9"), plan.delete)
        assertEquals(docs().size, plan.upload.size)
    }

    @Test
    fun ids_are_firestore_safe() {
        assertEquals("a_b", BackupCodec.id("a/b"))
        assertEquals("_..", BackupCodec.id(".."))
        assertEquals("_", BackupCodec.id(""))
    }

    @Test
    fun merge_on_a_fresh_phone_keeps_the_backed_up_settings() {
        val fresh = BackupCodec.docs(emptyList(), CareRows(emptyList(), emptyList(), emptyList()), emptyList(), mapOf("backup_wifi_only" to true))
        val cloud = docs()
        val merged = BackupCodec.merge(fresh, cloud)
        val back = BackupCodec.local(merged.values)
        assertEquals(prefs + ("backup_wifi_only" to true), back.prefs)
        assertEquals(listOf(ride), back.rides)
    }

    @Test
    fun merge_takes_the_newer_reading() {
        val older = BackupDoc("readings/AA:BB", mapOf("bike" to "AA:BB", "odo" to 1L, "fuel" to 1L, "t" to 1L))
        val newer = BackupDoc("readings/AA:BB", mapOf("bike" to "AA:BB", "odo" to 2L, "fuel" to 2L, "t" to 2L))
        assertEquals(newer, BackupCodec.merge(mapOf(older.path to older), mapOf(newer.path to newer))[older.path])
        assertEquals(newer, BackupCodec.merge(mapOf(newer.path to newer), mapOf(older.path to older))[older.path])
    }
}
