package com.aiyu.ridelink.core.diag

import android.content.Context
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Developer option "Save logs": every Console line also goes to `files/logs/ridelink-<date>.log`, one file a day,
 * the last [KEEP_DAYS] kept. Stays on the phone until the rider exports it. Off by default (frames carry names
 * and numbers of callers).
 */
@Singleton
class LogFile @Inject constructor(@ApplicationContext context: Context, settings: SettingsRepository) {
    private val dir = File(context.filesDir, "logs")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enabled = settings.settings.map { it.saveLogs }.stateIn(io, SharingStarted.Eagerly, false)
    // One writer, so lines land in the order they were logged.
    private val lines = Channel<String>(Channel.UNLIMITED)

    init {
        io.launch {
            for (line in lines) runCatching {
                dir.mkdirs()
                File(dir, "ridelink-${LocalDate.now()}.log").appendText("${LocalDate.now()} $line\n")
            }
        }
        io.launch { prune() }
    }

    fun write(line: String) {
        if (enabled.value) lines.trySend(line)
    }

    /** Saved files, oldest first. */
    fun files(): List<File> = dir.listFiles { f -> f.name.endsWith(".log") }?.sortedBy { it.name }.orEmpty()

    /** All saved days into [out] (a file the rider picked), oldest first. False when nothing is saved yet. */
    suspend fun exportTo(out: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val files = files()
        out.use { o -> files.forEach { f -> f.inputStream().use { it.copyTo(o) } } }
        files.isNotEmpty()
    }

    suspend fun clear() = withContext(Dispatchers.IO) { files().forEach { it.delete() } }

    private fun prune() {
        val oldest = "ridelink-${LocalDate.now().minusDays(KEEP_DAYS - 1)}.log"
        files().filter { it.name < oldest }.forEach { it.delete() }
    }

    private companion object {
        const val KEEP_DAYS = 7L
    }
}
