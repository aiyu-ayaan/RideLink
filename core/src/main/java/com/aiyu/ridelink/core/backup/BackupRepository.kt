package com.aiyu.ridelink.core.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.account.AccountRepository
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.analytics.RideStore
import com.aiyu.ridelink.core.care.CareStore
import com.aiyu.ridelink.core.settings.BackupFrequency
import com.aiyu.ridelink.core.settings.GarageStore
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup run does. Only [BACKUP] runs on a schedule; the others are the rider's answer to a [Conflict]. */
enum class BackupMode {
    /** Upload what changed since the last backup. Stops with a [Conflict] when the cloud is not the one this phone last wrote. */
    BACKUP,
    /** Keep both: cloud-only items come to the phone, phone-only items go to the cloud, the phone's copy wins on a tie. */
    MERGE,
    /** Replace the phone's rides, fuel log, care, scooters and settings with the cloud backup. */
    USE_CLOUD,
    /** Replace the cloud backup with what is on this phone. */
    USE_PHONE,
    /** Delete the cloud backup; the phone keeps everything. */
    DELETE_CLOUD,
}

enum class BackupError {
    OFFLINE, QUOTA, DENIED, SIGNED_OUT, CHANGED, NO_BACKUP,
    /** The cloud backup was written by a newer RideLink with a layout this one cannot read. */
    NEEDS_UPDATE,
    UNKNOWN,
}

enum class BackupPhase { CHECKING, READING_PHONE, DOWNLOADING, RESTORING, UPLOADING, DELETING, FINISHING }

/** [total] 0 = indeterminate. */
data class BackupProgress(val phase: BackupPhase, val done: Int = 0, val total: Int = 0)

/**
 * The cloud holds a backup this phone did not write (or not last). [firstLink]: this phone never backed up to this
 * account (new phone, reinstall, other account); otherwise another phone backed up since.
 */
data class Conflict(
    val firstLink: Boolean,
    val cloudAt: Long,
    val cloudDevice: String?,
    val cloudCounts: Map<String, Int>,
    val phoneCounts: Map<String, Int>,
) {
    val phoneEmpty get() = (phoneCounts[BackupCodec.RIDES] ?: 0) + (phoneCounts[BackupCodec.FILLS] ?: 0) + (phoneCounts[BackupCodec.BIKES] ?: 0) == 0
}

data class BackupStatus(
    val lastAt: Long? = null,
    /** Approximate stored size of the backup, bytes. */
    val bytes: Long = 0,
    /** Documents per kind (BackupCodec.RIDES, ...) at the last backup. */
    val counts: Map<String, Int> = emptyMap(),
    val error: BackupError? = null,
    val errorAt: Long? = null,
    val conflict: Conflict? = null,
    /** The cloud backup was deleted (from this or another phone); scheduled backups wait for "Back up now". */
    val deleted: CloudDeleted? = null,
)

data class CloudDeleted(val at: Long, val device: String?)

sealed interface BackupOutcome {
    data object Done : BackupOutcome
    /** A scheduled or sign-in backup found the deletion marker and uploaded nothing. */
    data class Deleted(val deleted: CloudDeleted) : BackupOutcome
    data class NeedsChoice(val conflict: Conflict) : BackupOutcome
    data class Failed(val error: BackupError) : BackupOutcome
}

/**
 * Cloud backup of everything RideLink keeps (rides, fuel log, care, scooters, settings) to Firestore under
 * `RideLink/{uid}`, WhatsApp style: on a schedule through WorkManager ([BackupWorker]) or "Back up now".
 *
 * Built for the free Spark plan: a regular backup costs 1 read plus 1 write per changed document, because the hash of
 * every document at the last backup is kept on the phone (`backup.db`). The summary document `RideLink/{uid}`
 * carries a revision; a different revision than this phone last wrote means another phone (or a reinstall) is
 * involved, and nothing is overwritten until the rider picks [BackupMode.MERGE], [BackupMode.USE_CLOUD] or
 * [BackupMode.USE_PHONE].
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val rides: RideStore,
    private val care: CareStore,
    private val garage: GarageStore,
    private val controller: ClusterController,
) {
    private val db = Helper(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val work by lazy { WorkManager.getInstance(context) }

    private val firestore by lazy {
        FirebaseFirestore.getInstance().apply {
            // No offline cache: a backup must see the server, and a read with no network fails fast instead of hanging.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    val available get() = accounts.available

    private val _status = MutableStateFlow(BackupStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow<BackupProgress?>(null)
    /** Non-null while a run is working, in this process. */
    val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()

    /** A "Back up now" or conflict choice is queued (e.g. waiting for a network) or running. */
    val queued: Flow<Boolean> = if (!available) flowOf(false) else work.getWorkInfosForUniqueWorkFlow(NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } }

    init {
        scope.launch(Dispatchers.IO) { _status.value = loadStatus() }
    }

    // ---- scheduling

    /** Keeps the periodic job in line with the frequency setting and sign-in. Called once from the Application. */
    fun startScheduling() {
        if (!available) return
        scope.launch {
            combine(settings.settings, accounts.account) { s, a -> Triple(s.backupFrequency, s.backupWifiOnly, a != null) }
                .distinctUntilChanged()
                .collect { (freq, wifi, signedIn) -> schedule(freq, wifi, signedIn) }
        }
    }

    private fun schedule(freq: BackupFrequency, wifiOnly: Boolean, signedIn: Boolean) {
        val days = freq.days
        if (!signedIn || days == null) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(BackupWorker.MODE to BackupMode.BACKUP.name))
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * "Back up now" and the conflict choices: runs in WorkManager as soon as there is any network, also after leaving the app.
     * [byRider] false (the check after sign-in) behaves like a scheduled run: it never brings back a deleted backup.
     */
    fun enqueue(mode: BackupMode, byRider: Boolean = true) {
        if (!available) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(BackupWorker.MODE to mode.name, BackupWorker.BY_RIDER to byRider))
            .build()
        // A choice replaces a queued plain backup; a second tap on "Back up now" does not restart a running one.
        work.enqueueUniqueWork(NOW, if (mode == BackupMode.BACKUP) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }

    // ---- the run

    /** [byRider]: the rider tapped it. Only then does a backup start again after the cloud backup was deleted. */
    suspend fun run(mode: BackupMode, byRider: Boolean = false): BackupOutcome = mutex.withLock {
        val uid = accounts.current()?.uid ?: return@withLock fail(BackupError.SIGNED_OUT)
        try {
            val outcome = withContext(Dispatchers.IO) { runSignedIn(uid, mode, byRider) }
            android.util.Log.i("RideLink", "Backup $mode byRider=$byRider -> ${when (outcome) { BackupOutcome.Done -> "done"; is BackupOutcome.NeedsChoice -> "needs choice"; is BackupOutcome.Deleted -> "cloud deleted"; is BackupOutcome.Failed -> "failed" }}")
            when (outcome) {
                is BackupOutcome.NeedsChoice -> update { it.copy(conflict = outcome.conflict, error = null) }
                is BackupOutcome.Deleted -> update { BackupStatus(deleted = outcome.deleted) }
                else -> Unit
            }
            outcome
        } catch (e: TimeoutCancellationException) {
            fail(BackupError.OFFLINE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoBackup) {
            fail(BackupError.NO_BACKUP)
        } catch (e: NewerSchema) {
            fail(BackupError.NEEDS_UPDATE)
        } catch (e: Exception) {
            android.util.Log.w("RideLink", "Backup $mode failed", e)
            fail(classify(e))
        } finally {
            _progress.value = null
        }
    }

    private suspend fun runSignedIn(uid: String, mode: BackupMode, byRider: Boolean): BackupOutcome {
        val root = firestore.collection(COLLECTION).document(uid)
        _progress.value = BackupProgress(BackupPhase.CHECKING)
        val meta = net { root.get(Source.SERVER).await() }
        val cloudRev = if (meta.exists()) meta.getLong(F_REVISION) ?: 0L else null
        val state = loadSync()
        val deleted = meta.exists() && meta.getBoolean(F_DELETED) == true
        // Replacing the cloud (or deleting it) is fine; reading or adding to a newer layout is not.
        if (meta.exists() && (meta.getLong(F_SCHEMA) ?: 0L) > BackupCodec.SCHEMA && mode != BackupMode.USE_PHONE && mode != BackupMode.DELETE_CLOUD) {
            throw NewerSchema()
        }

        if (mode == BackupMode.DELETE_CLOUD) {
            val cloud = download(root)
            _progress.value = BackupProgress(BackupPhase.DELETING, 0, cloud.size)
            write(root, BackupPlan(emptyList(), cloud.keys.toList()), BackupPhase.DELETING)
            // The summary stays as a deletion marker, so another phone's scheduled backup does not quietly upload everything
            // again; only "Back up now" (on any phone) starts a new backup and replaces the marker.
            val now = System.currentTimeMillis()
            val device = deviceName()
            net {
                root.set(mapOf(F_REVISION to (cloudRev ?: 0L) + 1, F_DELETED to true, F_UPDATED to now, F_DEVICE to device, F_SCHEMA to BackupCodec.SCHEMA.toLong())).await()
            }
            saveSync(null, 0, emptyMap())
            update { BackupStatus(deleted = CloudDeleted(now, device)) }
            return BackupOutcome.Done
        }

        if (deleted) {
            when {
                mode == BackupMode.MERGE || mode == BackupMode.USE_CLOUD -> throw NoBackup()
                mode == BackupMode.BACKUP && !byRider -> return BackupOutcome.Deleted(CloudDeleted(meta.getLong(F_UPDATED) ?: 0L, meta.getString(F_DEVICE)))
            }
        }

        _progress.value = BackupProgress(BackupPhase.READING_PHONE)
        val local = snapshot()
        val target: Map<String, BackupDoc>
        val plan: BackupPlan
        when (mode) {
            BackupMode.BACKUP -> {
                target = local
                plan = when {
                    // Nothing in the cloud yet, or the rider starts again after a deletion: upload everything.
                    cloudRev == null || deleted -> BackupCodec.mirror(local, emptyMap())
                    state.uid == uid && cloudRev == state.revision -> BackupCodec.incremental(local, state.hashes)
                    else -> return BackupOutcome.NeedsChoice(
                        Conflict(
                            firstLink = state.uid != uid,
                            cloudAt = meta.getLong(F_UPDATED) ?: 0L,
                            cloudDevice = meta.getString(F_DEVICE),
                            cloudCounts = countsOf(meta.get(F_COUNTS)),
                            phoneCounts = BackupCodec.counts(local.keys),
                        ),
                    )
                }
            }
            BackupMode.USE_PHONE -> {
                target = local
                plan = BackupCodec.mirror(local, download(root).mapValues { it.value.hash })
            }
            BackupMode.MERGE, BackupMode.USE_CLOUD -> {
                if (cloudRev == null) throw NoBackup()
                val cloud = download(root)
                target = if (mode == BackupMode.MERGE) BackupCodec.merge(local, cloud) else cloud
                _progress.value = BackupProgress(BackupPhase.RESTORING)
                restore(target.values)
                plan = BackupCodec.mirror(target, cloud.mapValues { it.value.hash })
            }
            BackupMode.DELETE_CLOUD -> error("handled above")
        }

        // shortcut: documents are written before the revision check below, so a phone backing up at the same moment can be
        // partly overwritten (the run then fails with CHANGED and the next one asks again); a lock document would fix it.
        // A first backup that dies before the summary write leaves documents without a summary; the next run mirrors over them.
        write(root, plan, BackupPhase.UPLOADING)
        _progress.value = BackupProgress(BackupPhase.FINISHING)
        val sizes = target.mapValues { approxBytes(BackupCodec.toCloud(it.value)) }
        val counts = BackupCodec.counts(target.keys)
        val now = System.currentTimeMillis()
        // Nothing to send and nobody else wrote: no summary write either (free-plan writes).
        val revision = if (plan.size == 0 && cloudRev != null && (mode == BackupMode.BACKUP || mode == BackupMode.USE_CLOUD)) {
            cloudRev
        } else {
            net {
                firestore.runTransaction { tx ->
                    val cur = tx.get(root)
                    val rev = if (cur.exists()) cur.getLong(F_REVISION) ?: 0L else null
                    if (rev != cloudRev) throw ChangedElsewhere()
                    val next = (rev ?: 0L) + 1
                    tx.set(
                        root,
                        mapOf(
                            F_REVISION to next, F_UPDATED to now, F_DEVICE to deviceName(),
                            F_SCHEMA to BackupCodec.SCHEMA.toLong(), F_COUNTS to counts, F_BYTES to sizes.values.sum(),
                        ),
                    )
                    next
                }.await()
            }
        }
        saveSync(uid, revision, target.mapValues { it.value.hash }, sizes)
        update { BackupStatus(lastAt = now, bytes = sizes.values.sum(), counts = counts) }
        return BackupOutcome.Done
    }

    private suspend fun snapshot(): Map<String, BackupDoc> =
        BackupCodec.docs(rides.backupRows(), care.backupRows(), garage.backupRows(), settings.exportPrefs())

    private suspend fun restore(docs: Collection<BackupDoc>) {
        val d = BackupCodec.local(docs)
        val before = settings.current().deviceAddress
        rides.replaceAll(d.rides)
        care.replaceAll(d.care)
        garage.replaceAll(d.bikes)
        d.prefs?.let { settings.importPrefs(it) }
        // The paired scooters come back with the one that was selected. When that one is not among them (or none was
        // selected), the first restored scooter becomes the active one, so AutoConnect connects to it right away.
        val selected = settings.current().deviceAddress
        if (d.bikes.none { it["address"] == selected }) d.bikes.firstOrNull()?.let { b ->
            settings.setModel((b["model"] as? String)?.let { runCatching { ClusterModel.valueOf(it) }.getOrNull() })
            settings.setDevice(b["address"] as? String, b["name"] as? String)
        }
        // Like switching scooters in Settings: end the old session so the new one connects.
        if (before != null && before != settings.current().deviceAddress) controller.stopForBikeSwitch()
    }

    /** Every backup document of this account. One read per document: only for the rider's conflict choices. */
    private suspend fun download(root: DocumentReference): Map<String, BackupDoc> = buildMap {
        BackupCodec.KINDS.forEachIndexed { i, kind ->
            _progress.value = BackupProgress(BackupPhase.DOWNLOADING, i, BackupCodec.KINDS.size)
            val snap = net { root.collection(kind).get(Source.SERVER).await() }
            for (doc in snap.documents) {
                val data = doc.data.orEmpty().mapValues { (_, v) -> if (v is Blob) v.toBytes() else v }
                put("$kind/${doc.id}", BackupCodec.fromCloud("$kind/${doc.id}", data))
            }
        }
    }

    /** Writes and deletes in small batches so the progress bar moves and one batch stays far below the 10 MiB request cap. */
    private suspend fun write(root: DocumentReference, plan: BackupPlan, phase: BackupPhase) {
        val ops: List<Pair<String, BackupDoc?>> = plan.upload.map { it.path to it } + plan.delete.map { it to null }
        var done = 0
        _progress.value = BackupProgress(phase, 0, ops.size)
        for (chunk in ops.chunked(BATCH)) {
            val batch = firestore.batch()
            for ((path, doc) in chunk) {
                val ref = root.collection(path.substringBefore('/')).document(path.substringAfter('/'))
                if (doc == null) batch.delete(ref)
                else batch.set(ref, BackupCodec.toCloud(doc).mapValues { (_, v) -> if (v is ByteArray) Blob.fromBytes(v) else v })
            }
            net { batch.commit().await() }
            done += chunk.size
            _progress.value = BackupProgress(phase, done, ops.size)
        }
    }

    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun classify(e: Throwable): BackupError {
        val chain = generateSequence(e) { it.cause }
        if (chain.any { it is ChangedElsewhere }) return BackupError.CHANGED
        return when ((chain.firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BackupError.OFFLINE
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> BackupError.QUOTA
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED -> BackupError.DENIED
            else -> BackupError.UNKNOWN
        }
    }

    private suspend fun <T> net(block: suspend () -> T): T = withTimeout(NET_TIMEOUT_MS) { block() }

    private fun fail(e: BackupError): BackupOutcome {
        update { it.copy(error = e, errorAt = System.currentTimeMillis()) }
        return BackupOutcome.Failed(e)
    }

    // ---- local sync state (backup.db)

    private class Sync(val uid: String?, val revision: Long, val hashes: Map<String, String>)

    private fun loadSync(): Sync {
        val r = db.readableDatabase
        val hashes = r.query(T_SYNCED, null, null, null, null, null, null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        return Sync(meta(KEY_UID), meta(KEY_REVISION)?.toLongOrNull() ?: 0L, hashes)
    }

    private fun saveSync(uid: String?, revision: Long, hashes: Map<String, String>, sizes: Map<String, Long> = emptyMap()) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete(T_SYNCED, null, null)
            hashes.forEach { (path, h) ->
                w.insert(T_SYNCED, null, ContentValues().apply { put("path", path); put("hash", h); put("bytes", sizes[path] ?: 0L) })
            }
            putMeta(w, KEY_UID, uid)
            putMeta(w, KEY_REVISION, revision.toString())
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private fun update(change: (BackupStatus) -> BackupStatus) {
        val s = change(_status.value)
        _status.value = s
        putMeta(db.writableDatabase, KEY_STATUS, statusJson(s).toString())
    }

    private fun loadStatus(): BackupStatus = meta(KEY_STATUS)?.let { runCatching { statusFrom(JSONObject(it)) }.getOrNull() } ?: BackupStatus()

    private fun meta(k: String): String? =
        db.readableDatabase.query(T_META, arrayOf("v"), "k = ?", arrayOf(k), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putMeta(w: SQLiteDatabase, k: String, v: String?) {
        if (v == null) w.delete(T_META, "k = ?", arrayOf(k))
        else w.insertWithOnConflict(T_META, null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun statusJson(s: BackupStatus) = JSONObject().apply {
        s.lastAt?.let { put("lastAt", it) }
        put("bytes", s.bytes)
        put("counts", JSONObject(s.counts))
        s.error?.let { put("error", it.name) }
        s.errorAt?.let { put("errorAt", it) }
        s.deleted?.let { d -> put("deleted", JSONObject().apply { put("at", d.at); d.device?.let { put("device", it) } }) }
        s.conflict?.let { c ->
            put(
                "conflict",
                JSONObject().apply {
                    put("firstLink", c.firstLink); put("cloudAt", c.cloudAt); c.cloudDevice?.let { put("cloudDevice", it) }
                    put("cloudCounts", JSONObject(c.cloudCounts)); put("phoneCounts", JSONObject(c.phoneCounts))
                },
            )
        }
    }

    private fun statusFrom(j: JSONObject): BackupStatus {
        fun counts(o: JSONObject?) = o?.keys()?.asSequence()?.associateWith { o.getInt(it) }.orEmpty()
        return BackupStatus(
            lastAt = j.optLong("lastAt").takeIf { j.has("lastAt") },
            bytes = j.optLong("bytes"),
            counts = counts(j.optJSONObject("counts")),
            error = j.optString("error").takeIf { it.isNotEmpty() }?.let { runCatching { BackupError.valueOf(it) }.getOrNull() },
            errorAt = j.optLong("errorAt").takeIf { j.has("errorAt") },
            deleted = j.optJSONObject("deleted")?.let { d -> CloudDeleted(d.optLong("at"), d.optString("device").takeIf { it.isNotEmpty() }) },
            conflict = j.optJSONObject("conflict")?.let { c ->
                Conflict(
                    firstLink = c.optBoolean("firstLink"),
                    cloudAt = c.optLong("cloudAt"),
                    cloudDevice = c.optString("cloudDevice").takeIf { it.isNotEmpty() },
                    cloudCounts = counts(c.optJSONObject("cloudCounts")),
                    phoneCounts = counts(c.optJSONObject("phoneCounts")),
                )
            },
        )
    }

    private class ChangedElsewhere : Exception()
    private class NoBackup : Exception()
    private class NewerSchema : Exception()

    private class Helper(context: Context) : SQLiteOpenHelper(context, "backup.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $T_SYNCED (path TEXT PRIMARY KEY, hash TEXT NOT NULL, bytes INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE $T_META (k TEXT PRIMARY KEY, v TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        /** Top-level Firestore collection; one document per Google account uid. */
        const val COLLECTION = "RideLink"
        const val PERIODIC = "backup_periodic"
        const val NOW = "backup_now"
        private const val BATCH = 50
        private const val NET_TIMEOUT_MS = 60_000L
        private const val F_REVISION = "revision"
        private const val F_UPDATED = "updatedAt"
        private const val F_DEVICE = "device"
        private const val F_SCHEMA = "schema"
        private const val F_COUNTS = "counts"
        private const val F_BYTES = "bytes"
        /** Set on the summary by "Delete cloud backup"; the next full backup's summary write drops it. */
        private const val F_DELETED = "deleted"
        private const val T_SYNCED = "synced"
        private const val T_META = "meta"
        private const val KEY_UID = "uid"
        private const val KEY_REVISION = "revision"
        private const val KEY_STATUS = "status"

        private fun countsOf(v: Any?): Map<String, Int> =
            (v as? Map<*, *>)?.entries?.mapNotNull { (k, n) -> (n as? Number)?.let { k.toString() to it.toInt() } }?.toMap().orEmpty()

        /** Close to Firestore's storage size rules: names and strings +1, numbers 8, bytes as is, 32 per document. */
        internal fun approxBytes(v: Any?): Long = when (v) {
            null, is Boolean -> 1
            is Number -> 8
            is String -> v.length + 1L
            is ByteArray -> v.size.toLong()
            is Map<*, *> -> v.entries.sumOf { it.key.toString().length + 1L + approxBytes(it.value) } + 32
            is Collection<*> -> v.sumOf { approxBytes(it) }
            else -> v.toString().length + 1L
        }
    }
}
