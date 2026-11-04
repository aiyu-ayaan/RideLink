package com.aiyu.ridelink.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.update.NoteBlock
import com.aiyu.ridelink.core.update.ReleaseNotes
import com.aiyu.ridelink.core.update.Releases
import com.aiyu.ridelink.core.update.UpdateChannel
import com.aiyu.ridelink.core.update.UpdateState
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shown over the app when the launch check found a newer release. "Not now" is quiet for a day. */
@Composable
fun UpdateHost() {
    val vm = hiltViewModel<UpdateViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val inline by vm.offerShownInline.collectAsStateWithLifecycle()
    if (!inline && (state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready)) {
        // Fully expanded: a half-open sheet hides the buttons under the system gesture bar.
        ModalBottomSheet(onDismissRequest = vm::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            UpdateOffer(state, vm, Modifier.padding(horizontal = Dimens.xl).padding(bottom = Dimens.lg).navigationBarsPadding())
        }
    }
}

/** The body shared by the sheet and the Updates screen: what's new, then the one action that fits the state. */
@Composable
fun UpdateOffer(state: UpdateState, vm: UpdateViewModel, modifier: Modifier = Modifier) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        else -> return
    }
    val context = LocalContext.current
    // "Install unknown apps" is granted in system settings; re-check when the rider comes back.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    val canInstall = remember(resumes, state) { vm.canInstall() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.update_available, release.name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.update_youre_on, vm.installedName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (release.notes.isNotBlank()) {
            ReleaseNotesView(release.notes, Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
        }
        when (state) {
            is UpdateState.Available -> Button(onClick = { vm.download(state) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_download) + if (state.apk.size > 0) stringResource(R.string.update_download_size, state.apk.size / 1_000_000.0) else "")
            }
            is UpdateState.Downloading -> {
                LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is UpdateState.Ready -> if (canInstall) {
                Button(onClick = { vm.install(state) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
            } else {
                Text(stringResource(R.string.update_install_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { context.startActivity(vm.installPermissionIntent()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_allow_installs)) }
            }
            else -> Unit
        }
        if (state !is UpdateState.Downloading) OutlinedButton(onClick = vm::notNow, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_not_now)) }
    }
}

/** Settings → Updates: what's running, what's available, whether to look on start, and which releases to accept. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<UpdateViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.offerShownInline.value = true
        onDispose { vm.offerShownInline.value = false }
    }
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val channel = vm.channel(s)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.update_title)) },
                subtitle = { Text(stringResource(R.string.update_subtitle, vm.installedName)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
                Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    if (!vm.supported) {
                        Text(stringResource(R.string.update_debug_build), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    } else when (val st = state) {
                        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> UpdateOffer(st, vm)
                        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularWavyProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.width(Dimens.md))
                            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
                        }
                        UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.titleMedium)
                        is UpdateState.Failed -> Text(failureText(st), style = MaterialTheme.typography.bodyMedium, color = c.error)
                        UpdateState.Idle -> Text(
                            if (s.updateLastChecked > 0) stringResource(R.string.update_last_checked, formatWhen(s.updateLastChecked)) else stringResource(R.string.update_not_checked),
                            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        )
                    }
                    Button(onClick = vm::check, enabled = vm.supported && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_check)) }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_auto)) },
                    supportingContent = { Text(stringResource(R.string.update_auto_desc)) },
                    trailingContent = { Switch(s.updatesEnabled, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = c.surfaceContainerLow),
                    modifier = Modifier.clickable(role = Role.Switch) { vm.setEnabled(!s.updatesEnabled) },
                )
            }

            Text(stringResource(R.string.update_channel), style = MaterialTheme.typography.titleSmall, color = c.primary, modifier = Modifier.padding(top = Dimens.sm))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UpdateChannel.entries.forEachIndexed { i, ch ->
                    SegmentedButton(
                        selected = channel == ch,
                        onClick = { vm.setChannel(ch) },
                        shape = SegmentedButtonDefaults.itemShape(i, UpdateChannel.entries.size),
                    ) { Text(stringResource(ch.label)) }
                }
            }
            Text(stringResource(channel.detail), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)

            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Releases.PAGE))) }, Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.update_all_releases))
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
}

@Composable
private fun failureText(f: UpdateState.Failed): String = when (f.reason) {
    UpdateState.Reason.GITHUB -> stringResource(R.string.update_err_github, f.detail.orEmpty())
    UpdateState.Reason.CHECK -> stringResource(R.string.update_err_check)
    UpdateState.Reason.DOWNLOAD -> stringResource(R.string.update_err_download)
    UpdateState.Reason.CHECKSUM -> stringResource(R.string.update_err_checksum)
    UpdateState.Reason.START, UpdateState.Reason.SCREEN -> stringResource(R.string.update_err_start)
    UpdateState.Reason.SIGNATURE -> stringResource(R.string.update_err_signature)
    UpdateState.Reason.INSTALL -> f.detail ?: stringResource(R.string.update_err_install)
}

private val whenFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun formatWhen(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(whenFormat)

private val UpdateChannel.label: Int get() = when (this) {
    UpdateChannel.STABLE -> R.string.update_channel_stable
    UpdateChannel.BETA -> R.string.update_channel_beta
    UpdateChannel.ALPHA -> R.string.update_channel_alpha
}

private val UpdateChannel.detail: Int get() = when (this) {
    UpdateChannel.STABLE -> R.string.update_channel_stable_desc
    UpdateChannel.BETA -> R.string.update_channel_beta_desc
    UpdateChannel.ALPHA -> R.string.update_channel_alpha_desc
}

/** A GitHub release body drawn as what it is (headings, bullets, paragraphs), not as raw `###` and `**`. */
@Composable
fun ReleaseNotesView(notes: String, modifier: Modifier = Modifier) {
    val blocks = remember(notes) { ReleaseNotes.parse(notes) }
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceContainerHighest)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                is NoteBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(Dimens.lg))
                    Text(inline(block.text, code), style = MaterialTheme.typography.bodyMedium)
                }
                is NoteBlock.Paragraph -> Text(inline(block.text, code), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")

private fun inline(text: String, code: SpanStyle): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        m.groups[1]?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.value) } }
        m.groups[2]?.let { withStyle(code) { append(it.value) } }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
