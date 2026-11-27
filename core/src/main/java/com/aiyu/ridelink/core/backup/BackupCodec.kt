package com.aiyu.ridelink.core.backup

import com.aiyu.ridelink.core.analytics.RideRows
import com.aiyu.ridelink.core.care.CareRows
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One document of the cloud backup. [path] is "kind/id" under `RideLink/{uid}`, [fields] are plain values
 * (ride samples as CSV text), [hash] fingerprints [fields] so a backup only uploads what changed.
 */
data class BackupDoc(val path: String, val fields: Map<String, Any?>, val hash: String = BackupCodec.hash(fields)) {
    val kind get() = path.substringBefore('/')
}

/** What the phone holds, split back out of backup documents for the stores' replaceAll. */
data class LocalData(val rides: List<RideRows>, val care: CareRows, val bikes: List<Row>, val prefs: Map<String, Any>?)

/** What to send: documents to write and paths to delete. */
data class BackupPlan(val upload: List<BackupDoc>, val delete: List<String>) {
    val size get() = upload.size + delete.size
}

/**
 * Turns the phone's SQLite rows and preferences into Firestore documents and back, and plans each kind of sync.
 * Pure Kotlin: the Firestore calls live in [BackupRepository].
 *
 * Layout under `RideLink/{uid}`: `rides/{start ms}`, `fills/{bike}_{t}`, `care/{bike}_{item}`, `readings/{bike}`,
 * `bikes/{address}`, `settings/prefs`. Ids come from the data itself (not local row ids), so two phones name
 * the same ride the same way and a merge lines them up.
 */
object BackupCodec {
    /** Bumped when the layout changes in a way an older app cannot read. Stored in the summary document. */
    const val SCHEMA = 1
    const val RIDES = "rides"
    const val FILLS = "fills"
    const val CARE = "care"
    const val READINGS = "readings"
    const val BIKES = "bikes"
    const val SETTINGS = "settings"
    val KINDS = listOf(RIDES, FILLS, CARE, READINGS, BIKES, SETTINGS)
    /** Field holding [BackupDoc.hash] in the cloud copy. */
    const val HASH = "h"
    private const val SAMPLES = "samples"
    private const val TURNS = "turns"
    private const val PREFS = "prefs"

    // ---- phone -> documents

    fun docs(rides: List<RideRows>, care: CareRows, bikes: List<Row>, prefs: Map<String, Any>): Map<String, BackupDoc> = buildList {
        rides.forEach { add(ride(it)) }
        care.fills.forEach { add(BackupDoc("$FILLS/${id("${it["bike"] ?: "none"}_${it["t"]}")}", it)) }
        care.items.forEach { add(BackupDoc("$CARE/${id("${it["bike"]}_${it["item"]}")}", it)) }
        care.readings.forEach { add(BackupDoc("$READINGS/${id(it["bike"].toString())}", it)) }
        bikes.forEach { add(BackupDoc("$BIKES/${id(it["address"].toString())}", it)) }
        add(BackupDoc("$SETTINGS/$PREFS", mapOf(PREFS to encodePrefs(prefs))))
    }.associateBy { it.path }

    private fun ride(r: RideRows) = BackupDoc(
        "$RIDES/${r.ride["start"]}",
        r.ride + mapOf(SAMPLES to samplesCsv(r.samples), TURNS to r.turns),
    )

    // ---- documents -> phone

    @Suppress("UNCHECKED_CAST")
    fun local(docs: Collection<BackupDoc>): LocalData {
        val by = docs.groupBy { it.kind }
        fun rows(kind: String) = by[kind].orEmpty().map { it.fields }
        return LocalData(
            rides = by[RIDES].orEmpty().map { d ->
                RideRows(
                    ride = d.fields - SAMPLES - TURNS,
                    samples = parseCsv(d.fields[SAMPLES] as? String),
                    turns = (d.fields[TURNS] as? List<Row>).orEmpty(),
                )
            },
            care = CareRows(rows(FILLS), rows(CARE), rows(READINGS)),
            bikes = rows(BIKES),
            prefs = by[SETTINGS]?.firstOrNull()?.let { decodePrefs(it.fields[PREFS] as? Map<String, Any?>) },
        )
    }

    // ---- cloud form: samples gzipped (Firestore Blob), hash stored beside the fields

    fun toCloud(d: BackupDoc): Map<String, Any?> {
        val f = d.fields.toMutableMap()
        (f[SAMPLES] as? String)?.let { f[SAMPLES] = gzip(it) }
        f[HASH] = d.hash
        return f
    }

    fun fromCloud(path: String, data: Map<String, Any?>): BackupDoc {
        val f = data.toMutableMap()
        val h = f.remove(HASH) as? String
        (f[SAMPLES] as? ByteArray)?.let { f[SAMPLES] = gunzip(it) }
        return if (h != null) BackupDoc(path, f, h) else BackupDoc(path, f)
    }

    // ---- plans

    /** Regular backup: what changed since [synced] (path -> hash at the last backup), and what was deleted on the phone. */
    fun incremental(local: Map<String, BackupDoc>, synced: Map<String, String>) =
        BackupPlan(local.values.filter { synced[it.path] != it.hash }, (synced.keys - local.keys).toList())

    /** Make the cloud equal to [local]: upload what differs, delete what the phone does not have. */
    fun mirror(local: Map<String, BackupDoc>, cloud: Map<String, String>) =
        BackupPlan(local.values.filter { cloud[it.path] != it.hash }, (cloud.keys - local.keys).toList())

    /**
     * Both sides kept: every document from either side. Where both have the same ride, fuel stop, care item or bike,
     * the phone's copy wins (its edits are the ones the rider just saw). Settings merge key by key, so a fresh phone
     * with two saved preferences does not wipe the backed-up ones; the newer display reading wins.
     */
    @Suppress("UNCHECKED_CAST")
    fun merge(local: Map<String, BackupDoc>, cloud: Map<String, BackupDoc>): Map<String, BackupDoc> =
        (cloud + local).mapValues { (path, mine) ->
            val theirs = cloud[path]
            when {
                theirs == null || theirs === mine -> mine
                mine.kind == SETTINGS -> BackupDoc(
                    path,
                    mapOf(PREFS to (theirs.fields[PREFS] as? Map<String, Any?>).orEmpty() + (mine.fields[PREFS] as? Map<String, Any?>).orEmpty()),
                )
                mine.kind == READINGS -> if (time(theirs) > time(mine)) theirs else mine
                else -> mine
            }
        }

    private fun time(d: BackupDoc) = (d.fields["t"] as? Number)?.toLong() ?: 0L

    fun counts(paths: Collection<String>): Map<String, Int> = paths.groupingBy { it.substringBefore('/') }.eachCount()

    /** True when the phone has nothing worth keeping (settings alone do not count): a fresh install. */
    fun isEmpty(local: Map<String, BackupDoc>) = local.values.none { it.kind == RIDES || it.kind == FILLS || it.kind == BIKES }

    // ---- helpers

    /** Firestore ids: no '/', not "." or "..", not empty. */
    fun id(raw: String) = raw.replace('/', '_').let { if (it.isEmpty() || it == "." || it == "..") "_$it" else it }

    fun hash(fields: Map<String, Any?>): String =
        MessageDigest.getInstance("SHA-1").digest(canonical(fields).toByteArray()).joinToString("") { "%02x".format(it) }

    /** Key order and Int/Long/Double boxing do not change the text, so a restored row hashes the same as the original. */
    private fun canonical(v: Any?): String = when (v) {
        null -> "null"
        is Map<*, *> -> v.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") { "${it.key}=${canonical(it.value)}" }
        is Collection<*> -> v.joinToString(",", "[", "]") { canonical(it) }
        is Int -> v.toLong().toString()
        is Float -> v.toDouble().toString()
        else -> v.toString()
    }

    /** Samples are numbers only; one header line of column names, then one line per sample, empty for null. */
    internal fun samplesCsv(rows: List<Row>): String {
        if (rows.isEmpty()) return ""
        val cols = rows.first().keys.toList()
        return buildString {
            append(cols.joinToString(","))
            rows.forEach { r -> append('\n').append(cols.joinToString(",") { r[it]?.toString().orEmpty() }) }
        }
    }

    internal fun parseCsv(csv: String?): List<Row> {
        if (csv.isNullOrEmpty()) return emptyList()
        val lines = csv.split('\n')
        val cols = lines.first().split(',')
        return lines.drop(1).map { line ->
            val values = line.split(',')
            cols.indices.associate { i -> cols[i] to values.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: it.toDoubleOrNull() } }
        }
    }

    /** DataStore values keep their type through Firestore (which only has long and double): {"t": type, "v": value}. */
    private fun encodePrefs(prefs: Map<String, Any>): Map<String, Any?> = prefs.toSortedMap().mapValues { (_, v) ->
        when (v) {
            is Boolean -> mapOf("t" to "b", "v" to v)
            is Int -> mapOf("t" to "i", "v" to v.toLong())
            is Long -> mapOf("t" to "l", "v" to v)
            is Float -> mapOf("t" to "d", "v" to v.toDouble())
            is Double -> mapOf("t" to "d", "v" to v)
            is Set<*> -> mapOf("t" to "ss", "v" to v.map { it.toString() }.sorted())
            else -> mapOf("t" to "s", "v" to v.toString())
        }
    }

    private fun decodePrefs(encoded: Map<String, Any?>?): Map<String, Any> = buildMap {
        encoded?.forEach { (k, raw) ->
            val e = raw as? Map<*, *> ?: return@forEach
            val v = e["v"] ?: return@forEach
            when (e["t"]) {
                "b" -> (v as? Boolean)?.let { put(k, it) }
                "i" -> (v as? Number)?.let { put(k, it.toInt()) }
                "l" -> (v as? Number)?.let { put(k, it.toLong()) }
                "d" -> (v as? Number)?.let { put(k, it.toDouble()) }
                "ss" -> (v as? List<*>)?.let { put(k, it.map(Any?::toString).toSet()) }
                "s" -> put(k, v.toString())
            }
        }
    }

    internal fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

    internal fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).use { String(it.readBytes()) }
}
