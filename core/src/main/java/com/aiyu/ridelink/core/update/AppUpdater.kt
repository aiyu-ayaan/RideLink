package com.aiyu.ridelink.core.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app keeping itself up to date from the GitHub releases it was built by (same design as Rewire).
 * No store, no update server: ask GitHub what exists, work out whether any of it is newer, download the APK,
 * hand it to Android's package installer. The last screen is always the system's; nothing installs silently.
 * The only thing that leaves the phone is one unauthenticated GET of the public release list.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** True while the Updates screen is open: it draws the offer itself, so the global sheet stays down. */
    val offerShownInline = MutableStateFlow(false)

    val installedName: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    private val installed: Version? = Version.parse(installedName)

    /** Debug builds are another package (`.debug`) signed with the debug key; a release APK can never replace them. */
    val supported: Boolean = !context.packageName.endsWith(".debug")

    /**
     * Ask GitHub what exists. [manual] is the Check button: it says so when nothing is new and ignores a snooze.
     * The launch check stays silent and skips if it asked within the last hour (GitHub allows 60 anonymous
     * requests an hour per address). [channel] is passed after the user changes it, before DataStore catches up.
     */
    suspend fun check(manual: Boolean = false, channel: UpdateChannel? = null): UpdateState = withContext(Dispatchers.IO) {
        val busy = _state.value
        if (!supported || busy is UpdateState.Downloading || busy is UpdateState.Ready) return@withContext busy
        val s = repo.current()
        if (!manual) {
            val now = System.currentTimeMillis()
            if (!s.updatesEnabled || now < s.updateSnoozedUntil) return@withContext busy
            if (busy is UpdateState.Available || now - s.updateLastChecked < MIN_GAP_MILLIS) return@withContext busy
        }
        _state.value = UpdateState.Checking
        val wanted = channel ?: s.updateChannel ?: UpdateChannel.forVersion(installedName)
        val next = runCatching {
            val connection = open(Releases.API)
            try {
                if (connection.responseCode !in 200..299) return@runCatching UpdateState.Failed(UpdateState.Reason.GITHUB, connection.responseCode.toString())
                val pick = Releases.pick(Releases.parse(connection.inputStream.bufferedReader().use { it.readText() }), installed, wanted)
                val apk = pick?.let(Releases::apk)
                if (pick == null || apk == null) UpdateState.UpToDate else UpdateState.Available(pick, apk)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { UpdateState.Failed(UpdateState.Reason.CHECK, it.message) }
        repo.setUpdateLastChecked(System.currentTimeMillis())
        // A silent check that failed or found nothing says nothing.
        _state.value = if (!manual && next !is UpdateState.Available) UpdateState.Idle else next
        next
    }

    /** Download in the app scope, so leaving the screen doesn't cancel it. Progress is on [state]. */
    fun download(release: Release, asset: ReleaseAsset) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch {
            runCatching { fetch(release, asset) }
                .onFailure { _state.value = UpdateState.Failed(UpdateState.Reason.DOWNLOAD, it.message) }
        }
    }

    private fun fetch(release: Release, asset: ReleaseAsset) {
        downloads.mkdirs()
        // Everything else here is a previous attempt or version.
        downloads.listFiles()?.forEach { if (it.name != asset.name) it.delete() }
        val target = File(downloads, asset.name)
        if (asset.size > 0 && target.length() == asset.size && verified(target, asset)) {
            _state.value = UpdateState.Ready(release, target)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val connection = open(asset.url)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = if (asset.size > 0) asset.size else connection.contentLengthLong
            var written = 0L
            var lastReported = 0f
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val fraction = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (fraction - lastReported >= 0.01f) { // a state emission per percent, not per 64 KB
                            lastReported = fraction
                            _state.value = UpdateState.Downloading(release, fraction)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!verified(target, asset)) {
            target.delete()
            _state.value = UpdateState.Failed(UpdateState.Reason.CHECKSUM)
            return
        }
        _state.value = UpdateState.Ready(release, target)
    }

    /** SHA-256 when GitHub records one for the asset; Android's signature check is the backstop either way. */
    private fun verified(file: File, asset: ReleaseAsset): Boolean {
        val expected = asset.sha256 ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n == -1) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected
    }

    /** "Install unknown apps" is a per-app setting, opened in system settings, not a runtime permission. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hand the APK to Android's package installer. A session rather than ACTION_VIEW so a refusal (typically an APK
     * signed with another key) comes back to the app's UpdateInstallReceiver with a reason.
     */
    fun install(apk: File) {
        scope.launch {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("ridelink.apk", 0, apk.length()).use { sink ->
                        apk.inputStream().use { it.copyTo(sink) }
                        session.fsync(sink)
                    }
                    // Explicit component: a mutable PendingIntent must not be implicit (API 34+).
                    val status = PendingIntent.getBroadcast(
                        context, id,
                        Intent(ACTION_STATUS).setComponent(ComponentName(context.packageName, RECEIVER)),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, // the system adds the status extras
                    )
                    session.commit(status.intentSender)
                }
            }.onFailure { fail(UpdateState.Reason.START, it.message) }
        }
    }

    /** "Not now": quiet for a day. */
    fun snooze() {
        _state.value = UpdateState.Idle
        scope.launch { repo.setUpdateSnoozedUntil(System.currentTimeMillis() + DAY_MILLIS) }
    }

    /** Dismissed without deciding; comes back on the next launch check. */
    fun dismiss() { _state.value = UpdateState.Idle }

    fun fail(reason: UpdateState.Reason, detail: String? = null) { _state.value = UpdateState.Failed(reason, detail) }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "RideLink/$installedName") // GitHub rejects requests without one
    }

    companion object {
        const val ACTION_STATUS = "com.aiyu.ridelink.UPDATE_INSTALL_STATUS"
        private const val RECEIVER = "com.aiyu.ridelink.service.UpdateInstallReceiver"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val MIN_GAP_MILLIS = 60L * 60 * 1000
    }
}

/** Where a check, a download or an install has got to. Messages live in the ui module; [Failed] carries a reason. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val apk: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val reason: Reason, val detail: String? = null) : UpdateState

    enum class Reason { GITHUB, CHECK, DOWNLOAD, CHECKSUM, START, SCREEN, SIGNATURE, INSTALL }
}
