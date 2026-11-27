package com.aiyu.ridelink.ui.backup

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.backup.BackupCodec
import com.aiyu.ridelink.core.backup.BackupError
import com.aiyu.ridelink.core.backup.BackupMode
import com.aiyu.ridelink.core.backup.BackupPhase
import com.aiyu.ridelink.core.backup.BackupProgress
import com.aiyu.ridelink.core.backup.BackupStatus
import com.aiyu.ridelink.core.backup.Conflict
import com.aiyu.ridelink.core.settings.BackupFrequency
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.account.AccountViewModel
import com.aiyu.ridelink.ui.account.GoogleSignInButton
import com.aiyu.ridelink.ui.nav.AdaptiveTopAppBar
import com.aiyu.ridelink.ui.nav.PaneRow
import com.aiyu.ridelink.ui.nav.adaptiveScrollBehavior
import com.aiyu.ridelink.ui.nav.isTwoPane
import com.aiyu.ridelink.ui.theme.Dimens
import com.aiyu.ridelink.ui.update.BackButton

/**
 * Cloud backup, WhatsApp style: status hero with the run's progress, "Back up now", the conflict choice when the cloud
 * holds someone else's backup, the automatic schedule, what is inside, and restore / delete.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel(), accounts: AccountViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val queued by vm.queued.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val busy = progress != null || queued
    // A destructive choice waiting for its confirm dialog.
    var confirm by remember { mutableStateOf<BackupMode?>(null) }
    var pickFrequency by remember { mutableStateOf(false) }
    val scroll = adaptiveScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            AdaptiveTopAppBar(
                large = true,
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val signedIn = account != null
        val main: LazyListScope.() -> Unit = {
            item { Hero(status, progress, queued, signedIn && vm.available) }
            if (!vm.available) {
                item { Hint(stringResource(R.string.backup_unavailable)) }
            } else if (!signedIn) {
                item { Hint(stringResource(R.string.backup_signed_out_body)) }
                item {
                    Column(Modifier.padding(horizontal = Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        accounts.error?.let { ErrorText(stringResource(it)) }
                        GoogleSignInButton(accounts.busy, { accounts.signIn(context) })
                    }
                }
            } else {
                item { Details(context, status, account?.email) }
                item {
                    Button(
                        onClick = vm::backUpNow,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).heightIn(min = Dimens.minTouch),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.backup_now))
                    }
                }
                status.conflict?.takeIf { !busy }?.let { c -> item { ConflictCard(context, c, onChoose = { mode -> if (mode == BackupMode.MERGE || (mode == BackupMode.USE_CLOUD && c.phoneEmpty)) vm.run(mode) else confirm = mode }) } }
            }
        }
        val options: LazyListScope.() -> Unit = {
            if (vm.available && signedIn) {
                item { Header(R.string.backup_auto) }
                item {
                    Group {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.backup_frequency)) },
                            supportingContent = { Text(stringResource(prefs.backupFrequency.label)) },
                            leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                            colors = groupItemColors(),
                            modifier = Modifier.clickable(role = Role.Button) { pickFrequency = true },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.backup_wifi_only)) },
                            supportingContent = { Text(stringResource(R.string.backup_wifi_only_desc)) },
                            leadingContent = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                            trailingContent = { Switch(checked = prefs.backupWifiOnly, onCheckedChange = null) },
                            colors = groupItemColors(),
                            modifier = Modifier.toggleable(prefs.backupWifiOnly, role = Role.Switch, enabled = prefs.backupFrequency != BackupFrequency.MANUAL, onValueChange = vm::setWifiOnly),
                        )
                    }
                }
                item { Header(R.string.backup_contents) }
                item { Contents(status) }
                if (status.lastAt != null || status.conflict != null) {
                    item { Header(R.string.backup_manage) }
                    item {
                        Group {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.backup_restore_now)) },
                                supportingContent = { Text(stringResource(R.string.backup_restore_now_desc)) },
                                leadingContent = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                                colors = groupItemColors(),
                                modifier = Modifier.clickable(enabled = !busy, role = Role.Button) { confirm = BackupMode.USE_CLOUD },
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.backup_delete_cloud), color = MaterialTheme.colorScheme.error) },
                                supportingContent = { Text(stringResource(R.string.backup_delete_cloud_desc)) },
                                leadingContent = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                colors = groupItemColors(),
                                modifier = Modifier.clickable(enabled = !busy, role = Role.Button) { confirm = BackupMode.DELETE_CLOUD },
                            )
                        }
                    }
                }
            }
            item { Hint(stringResource(R.string.backup_privacy)) }
        }
        if (isTwoPane()) {
            PaneRow(Modifier.padding(padding)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    main()
                    item { Spacer(Modifier.height(Dimens.xxl)) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    options()
                    item { Spacer(Modifier.height(Dimens.xxl)) }
                }
            }
        } else LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            main()
            options()
            item { Spacer(Modifier.height(Dimens.xxl)) }
        }
    }

    if (pickFrequency) FrequencyDialog(prefs.backupFrequency, onDismiss = { pickFrequency = false }) {
        vm.setFrequency(it)
        pickFrequency = false
    }
    confirm?.let { mode ->
        ConfirmDialog(mode, status, onDismiss = { confirm = null }) {
            vm.run(mode)
            confirm = null
        }
    }
}

/** The one big element: the cloud in a cookie, ringed by the run's progress. */
@Composable
private fun Hero(status: BackupStatus, progress: BackupProgress?, queued: Boolean, active: Boolean) {
    val running = progress != null
    val state = when {
        !active -> HeroState.OFF
        running || queued -> HeroState.RUNNING
        status.conflict != null -> HeroState.CONFLICT
        status.error != null -> HeroState.FAILED
        status.lastAt != null -> HeroState.DONE
        else -> HeroState.NEVER
    }
    Column(
        Modifier.fillMaxWidth().padding(top = Dimens.lg, start = Dimens.gutter, end = Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
            val total = progress?.total ?: 0
            val fraction by animateFloatAsState(
                if (total > 0) progress!!.done.toFloat() / total else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "backup progress",
            )
            when {
                running && total > 0 -> CircularWavyProgressIndicator(progress = { fraction }, modifier = Modifier.size(168.dp))
                running || queued -> CircularWavyProgressIndicator(Modifier.size(168.dp))
            }
            val (container, content) = when (state) {
                HeroState.CONFLICT -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                HeroState.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                HeroState.OFF -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }
            Box(
                Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(state, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "backup icon") { s ->
                    Icon(s.icon, contentDescription = null, tint = content, modifier = Modifier.size(56.dp))
                }
            }
        }
        val title = when (state) {
            HeroState.RUNNING -> progress?.phase?.label?.let { stringResource(it) } ?: stringResource(R.string.backup_state_waiting)
            HeroState.CONFLICT -> stringResource(R.string.backup_state_conflict)
            HeroState.FAILED -> stringResource(R.string.backup_state_failed)
            HeroState.DONE -> stringResource(R.string.backup_state_done)
            HeroState.NEVER -> stringResource(R.string.backup_state_never)
            HeroState.OFF -> stringResource(R.string.backup_signed_out_title)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (running && (progress?.total ?: 0) > 0) {
            Text(
                stringResource(R.string.backup_progress_count, progress!!.done, progress.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state == HeroState.FAILED) status.error?.let { ErrorText(stringResource(it.label), center = true) }
    }
}

private enum class HeroState(val icon: ImageVector) {
    OFF(Icons.Rounded.CloudOff),
    RUNNING(Icons.Rounded.CloudSync),
    CONFLICT(Icons.Rounded.SyncProblem),
    FAILED(Icons.Rounded.SyncProblem),
    DONE(Icons.Rounded.CloudDone),
    NEVER(Icons.Rounded.CloudUpload),
}

@Composable
private fun Details(context: Context, status: BackupStatus, email: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        status.lastAt?.let {
            Text(stringResource(R.string.backup_last, when_(context, it)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.backup_size, Formatter.formatShortFileSize(context, status.bytes)), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        email?.let { Text(stringResource(R.string.backup_account, it), style = MaterialTheme.typography.bodyMedium, color = muted) }
    }
}

/** "Today, 2:04 PM" / "Yesterday, ..." / a date. */
internal fun when_(context: Context, at: Long): String =
    DateUtils.getRelativeDateTimeString(context, at, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_ABBREV_MONTH).toString()

@Composable
private fun ConflictCard(context: Context, c: Conflict, onChoose: (BackupMode) -> Unit) {
    val device = c.cloudDevice ?: stringResource(R.string.backup_conflict_device_unknown)
    val date = when_(context, c.cloudAt)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            val title = when {
                c.phoneEmpty -> R.string.backup_conflict_restore_title
                c.firstLink -> R.string.backup_conflict_first_title
                else -> R.string.backup_conflict_other_title
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(
                if (c.phoneEmpty) stringResource(R.string.backup_conflict_restore_body, device, date) else stringResource(R.string.backup_conflict_body, device, date),
                style = MaterialTheme.typography.bodyMedium,
            )
            CompareTable(c)
            if (c.phoneEmpty) {
                Button(onClick = { onChoose(BackupMode.USE_CLOUD) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.backup_restore))
                }
                TextButton(onClick = { onChoose(BackupMode.USE_PHONE) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.backup_start_fresh))
                }
            } else {
                Button(onClick = { onChoose(BackupMode.MERGE) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.backup_merge))
                }
                Text(stringResource(R.string.backup_merge_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onChoose(BackupMode.USE_CLOUD) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.backup_use_cloud))
                }
                OutlinedButton(onClick = { onChoose(BackupMode.USE_PHONE) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                    Text(stringResource(R.string.backup_use_phone))
                }
            }
        }
    }
}

/** What each side holds, so the rider sees what a choice keeps. */
@Composable
private fun CompareTable(c: Conflict) {
    val rows = listOf(
        R.string.backup_kind_rides to BackupCodec.RIDES,
        R.string.backup_kind_fills to BackupCodec.FILLS,
        R.string.backup_kind_bikes to BackupCodec.BIKES,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        Row {
            Spacer(Modifier.weight(1.4f))
            Text(stringResource(R.string.backup_col_phone), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(stringResource(R.string.backup_col_cloud), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        rows.forEach { (label, kind) ->
            Row {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                Text("${c.phoneCounts[kind] ?: 0}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("${c.cloudCounts[kind] ?: 0}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun Contents(status: BackupStatus) {
    val known = status.lastAt != null
    fun count(kind: String) = if (known) "${status.counts[kind] ?: 0}" else null
    Group {
        ContentRow(Icons.Rounded.Route, R.string.backup_kind_rides, count(BackupCodec.RIDES))
        ContentRow(Icons.Rounded.LocalGasStation, R.string.backup_kind_fills, count(BackupCodec.FILLS))
        ContentRow(Icons.Rounded.Build, R.string.backup_kind_care, count(BackupCodec.CARE))
        ContentRow(Icons.Rounded.TwoWheeler, R.string.backup_kind_bikes, count(BackupCodec.BIKES))
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_kind_settings)) },
            supportingContent = { Text(stringResource(R.string.backup_kind_settings_desc)) },
            leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            colors = groupItemColors(),
        )
    }
}

@Composable
private fun ContentRow(icon: ImageVector, label: Int, count: String?) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = count?.let { { Text(it, style = MaterialTheme.typography.titleMedium) } },
        colors = groupItemColors(),
    )
}

@Composable
private fun FrequencyDialog(current: BackupFrequency, onDismiss: () -> Unit, onPick: (BackupFrequency) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_frequency)) },
        text = {
            Column {
                BackupFrequency.entries.forEach { f ->
                    ListItem(
                        headlineContent = { Text(stringResource(f.label)) },
                        leadingContent = { RadioButton(selected = f == current, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(selected = f == current, role = Role.RadioButton) { onPick(f) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Confirm for the choices that throw data away, with the counts each side holds. */
@Composable
private fun ConfirmDialog(mode: BackupMode, status: BackupStatus, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = status.conflict
    // Without a pending conflict, the last backup's counts stand for the cloud and are the best guess for the phone.
    val cloud = c?.cloudCounts ?: status.counts
    val phone = c?.phoneCounts ?: status.counts
    fun n(m: Map<String, Int>, k: String) = m[k] ?: 0
    val (title, body, action) = when (mode) {
        BackupMode.USE_CLOUD -> Triple(
            R.string.backup_confirm_cloud_title,
            stringResource(R.string.backup_confirm_cloud_body, n(phone, BackupCodec.RIDES), n(phone, BackupCodec.FILLS), n(cloud, BackupCodec.RIDES), n(cloud, BackupCodec.FILLS)),
            R.string.backup_replace,
        )
        BackupMode.USE_PHONE -> Triple(
            R.string.backup_confirm_phone_title,
            stringResource(R.string.backup_confirm_phone_body, n(cloud, BackupCodec.RIDES), n(cloud, BackupCodec.FILLS), n(phone, BackupCodec.RIDES), n(phone, BackupCodec.FILLS)),
            R.string.backup_replace,
        )
        else -> Triple(R.string.backup_confirm_delete_title, stringResource(R.string.backup_confirm_delete_body), R.string.backup_delete)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Header(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.gutter, top = Dimens.lg, bottom = Dimens.xs),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.sm),
    )
}

@Composable
private fun ErrorText(text: String, center: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = if (center) TextAlign.Center else null,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column { content() } }
}

@Composable
private fun groupItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

internal val BackupFrequency.label
    get() = when (this) {
        BackupFrequency.MANUAL -> R.string.backup_freq_manual
        BackupFrequency.DAILY -> R.string.backup_freq_daily
        BackupFrequency.WEEKLY -> R.string.backup_freq_weekly
        BackupFrequency.MONTHLY -> R.string.backup_freq_monthly
    }

private val BackupPhase.label
    get() = when (this) {
        BackupPhase.CHECKING -> R.string.backup_phase_checking
        BackupPhase.READING_PHONE -> R.string.backup_phase_reading
        BackupPhase.DOWNLOADING -> R.string.backup_phase_downloading
        BackupPhase.RESTORING -> R.string.backup_phase_restoring
        BackupPhase.UPLOADING -> R.string.backup_phase_uploading
        BackupPhase.DELETING -> R.string.backup_phase_deleting
        BackupPhase.FINISHING -> R.string.backup_phase_finishing
    }

private val BackupError.label
    get() = when (this) {
        BackupError.OFFLINE -> R.string.backup_err_offline
        BackupError.QUOTA -> R.string.backup_err_quota
        BackupError.DENIED -> R.string.backup_err_denied
        BackupError.SIGNED_OUT -> R.string.backup_err_signed_out
        BackupError.CHANGED -> R.string.backup_err_changed
        BackupError.NO_BACKUP -> R.string.backup_err_no_backup
        BackupError.NEEDS_UPDATE -> R.string.backup_err_needs_update
        BackupError.UNKNOWN -> R.string.backup_err_unknown
    }
