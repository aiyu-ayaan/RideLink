package com.aiyu.ridelink.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.display.DisplayTemplate
import com.aiyu.ridelink.core.phone.RejectMessage
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.settings.Settings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.hasSms
import com.aiyu.ridelink.ui.smsPermissions
import com.aiyu.ridelink.ui.theme.Dimens

private val ClusterModel.label
    get() = when (this) {
        ClusterModel.NTORQ_U716 -> R.string.model_u716
        ClusterModel.NTORQ_U577_BASE -> R.string.model_u577_base
        ClusterModel.NTORQ_U577_PREMIUM -> R.string.model_u577_premium
        ClusterModel.NTORQ_U812 -> R.string.model_u812
        ClusterModel.NTORQ_N251 -> R.string.model_n251
        ClusterModel.NTORQ_U458 -> R.string.model_u458
        ClusterModel.NTORQ_U377 -> R.string.model_u377
    }

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val detected by vm.detected.collectAsStateWithLifecycle()
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    var rider by remember(s.riderName) { mutableStateOf(s.riderName) }
    var line1 by remember(s.displayLine1) { mutableStateOf(s.displayLine1) }
    var line2 by remember(s.displayLine2) { mutableStateOf(s.displayLine2) }
    // Which custom line the token chips add to; the previews refresh with the 2 s status tick.
    var editing by remember { mutableIntStateOf(1) }
    val tick by produceState(0) { while (true) { delay(2_000); value++ } }
    val link by vm.linkState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsText by remember(s.rejectSmsText) { mutableStateOf(s.rejectSmsText) }
    // Re-checked on every resume: both grants are given in system screens.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val smsOk = remember(refresh) { context.hasSms() }
    val overlayOk = remember(refresh) { AndroidSettings.canDrawOverlays(context) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val linkReady = link is LinkState.Ready

    Scaffold(topBar = { MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.tab_forwarding)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Section(R.string.settings_display_text) }
            item {
                DisplayTextField(rider, R.string.settings_rider, R.string.settings_rider_hint) { rider = it; vm.setRiderName(it) }
            }
            item {
                TemplateField(line1, R.string.settings_line1, vm.preview(line1, tick), onFocus = { editing = 1 }) {
                    line1 = it; vm.setDisplayLines(it, line2)
                }
            }
            item {
                TemplateField(line2, R.string.settings_line2, vm.preview(line2, tick), onFocus = { editing = 2 }) {
                    line2 = it; vm.setDisplayLines(line1, it)
                }
            }
            item {
                TokenChips { token ->
                    if (editing == 2) { line2 = (line2 + token).take(TEMPLATE_MAX); vm.setDisplayLines(line1, line2) }
                    else { line1 = (line1 + token).take(TEMPLATE_MAX); vm.setDisplayLines(line1, line2) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = vm::sendDisplayText, enabled = linkReady) { Text(stringResource(R.string.settings_send_text)) }
                }
            }

            item { Section(R.string.settings_forwarding) }
            item { SwitchRow(R.string.settings_nav, null, s.forwardNavigation, vm::setForwardNavigation) }
            item { SwitchRow(R.string.settings_calls, null, s.forwardCalls, vm::setForwardCalls) }
            item { SwitchRow(R.string.settings_call_buttons, R.string.settings_call_buttons_hint, s.callButtons, vm::setCallButtons) }
            if (s.callButtons) {
                item {
                    SwitchRow(R.string.settings_reject_sms, R.string.settings_reject_sms_hint, s.rejectSms) { on ->
                        if (on && !context.hasSms()) askSms.launch(smsPermissions)
                        vm.setRejectSms(on)
                    }
                }
                if (s.rejectSms) {
                    item {
                        OutlinedTextField(
                            value = smsText,
                            onValueChange = { smsText = it.take(RejectMessage.MAX); vm.setRejectSmsText(smsText) },
                            label = { Text(stringResource(R.string.settings_reject_sms_text)) },
                            placeholder = { Text(RejectMessage.DEFAULT) },
                            supportingText = { if (!smsOk) Text(stringResource(R.string.settings_reject_sms_perm), color = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
                        )
                    }
                }
            }
            item { SwitchRow(R.string.settings_voice_button, R.string.settings_voice_button_hint, s.voiceButton, vm::setVoiceButton) }
            if (s.voiceButton && !overlayOk) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_voice_overlay)) },
                        trailingContent = {
                            FilledTonalButton(onClick = {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                                )
                            }) { Text(stringResource(R.string.perm_grant)) }
                        },
                    )
                }
            }
            item { SwitchRow(R.string.settings_missed_calls, R.string.settings_missed_calls_hint, s.missedCalls, vm::setMissedCalls) }
            item { SwitchRow(R.string.settings_overspeed, R.string.settings_overspeed_hint, s.overSpeedAlert, vm::setOverSpeedAlert) }
            if (s.overSpeedAlert) {
                item { OverSpeedSlider(s.overSpeedKmh, vm::setOverSpeedKmh) }
            }
            item { SwitchRow(R.string.settings_social, R.string.settings_social_hint, s.showSocial, vm::setShowSocial) }
            item { SwitchRow(R.string.settings_weather, R.string.settings_weather_hint, s.showWeather, vm::setShowWeather) }
            item { SwitchRow(R.string.settings_location, R.string.settings_location_hint, s.showLocation, vm::setShowLocation) }
            item { SwitchRow(R.string.settings_music, R.string.settings_music_hint, s.showMusic, vm::setShowMusic) }

            item { Section(R.string.settings_in_app) }
            item { SwitchRow(R.string.settings_ride_data, R.string.settings_ride_data_hint, s.showRideData, vm::setShowRideData) }
            item { SwitchRow(R.string.settings_vehicle, R.string.settings_vehicle_hint, s.showVehicle, vm::setShowVehicle) }

            item { Section(R.string.settings_scooter) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_model)) },
                    supportingContent = { Text(stringResource(R.string.settings_model_hint)) },
                )
            }
            item {
                val auto = s.model == null
                ListItem(
                    headlineContent = { Text(stringResource(R.string.model_auto)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.model_auto_using, stringResource((detected ?: ClusterModel.DEFAULT).label)))
                            // Raw 5A 11 bytes, to match against the TVS VehicleId table (38 = U577 premium, 39 = U812).
                            val code = telemetry.vehicleModel
                            if (code != null) {
                                Text(
                                    stringResource(R.string.model_reported, code, telemetry.softwareVersion ?: 0),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    leadingContent = { RadioButton(selected = auto, onClick = null) },
                    modifier = Modifier.selectable(selected = auto, role = Role.RadioButton) { vm.setModel(null) },
                )
            }
            items(ClusterModel.entries) { m ->
                ListItem(
                    headlineContent = { Text(stringResource(m.label)) },
                    leadingContent = { RadioButton(selected = s.model == m, onClick = null) },
                    modifier = Modifier.selectable(selected = s.model == m, role = Role.RadioButton) { vm.setModel(m) },
                )
            }
            if (s.deviceAddress != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_paired, s.deviceName ?: s.deviceAddress!!)) },
                        trailingContent = {
                            OutlinedButton(
                                onClick = vm::forget,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.settings_forget)) }
                        },
                    )
                }
            }
        }
    }
}

/** 17 characters max, the display's text frame width; the counter shows what is left. */
@Composable
private fun DisplayTextField(value: String, label: Int, hint: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(TEXT_MAX)) },
        label = { Text(stringResource(label)) },
        supportingText = {
            Row {
                Text(stringResource(hint), modifier = Modifier.weight(1f))
                Text("${value.length}/$TEXT_MAX")
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
    )
}

private const val TEXT_MAX = 17

/** Templates can be longer than the 17 characters the display shows; the rendered text is cut, not the template. */
private const val TEMPLATE_MAX = 60

/** A custom-line template with the live result underneath, exactly as the display will show it. */
@Composable
private fun TemplateField(value: String, label: Int, preview: String, onFocus: () -> Unit, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(TEMPLATE_MAX)) },
        label = { Text(stringResource(label)) },
        supportingText = {
            Text(
                if (value.isBlank()) stringResource(R.string.settings_line_off) else stringResource(R.string.settings_line_preview, preview),
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter)
            .onFocusChanged { if (it.isFocused) onFocus() },
    )
}

/** Tap to insert a live value into the line being edited. */
@Composable
private fun TokenChips(onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(
            stringResource(R.string.settings_tokens_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SuggestionChip(onClick = { onPick("{caller|rider}") }, label = { Text("{caller|rider}") })
            DisplayTemplate.Token.entries.forEach { t ->
                SuggestionChip(onClick = { onPick("{${t.key}}") }, label = { Text("{${t.key}}") })
            }
        }
    }
}

/** Limit in 5 km/h steps; saved when the finger lifts so DataStore is not written on every frame. */
@Composable
private fun OverSpeedSlider(saved: Int, onSave: (Int) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
    val range = Settings.OVERSPEED_RANGE
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(
            stringResource(R.string.settings_overspeed_limit, value.roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onSave(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1,
        )
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.gutter, top = Dimens.xl, bottom = Dimens.sm),
    )
}

@Composable
private fun SwitchRow(title: Int, hint: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = hint?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
