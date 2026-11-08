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
import com.aiyu.ridelink.ui.nav.appBarColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.aiyu.ridelink.ui.hasBluetooth
import com.aiyu.ridelink.ui.hasSms
import com.aiyu.ridelink.ui.ride.PairSheet
import com.aiyu.ridelink.ui.nav.BottomBarSpacer
import com.aiyu.ridelink.ui.nav.LinkRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.aiyu.ridelink.ui.update.BackButton
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import com.aiyu.ridelink.core.notify.MessageAlert
import com.aiyu.ridelink.core.protocol.ClusterFamily
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.IconButton
import com.aiyu.ridelink.ui.smsPermissions
import com.aiyu.ridelink.ui.checkInPermissions
import com.aiyu.ridelink.ui.granted
import android.provider.ContactsContract
import com.aiyu.ridelink.ui.theme.Dimens

private val ClusterModel.label
    get() = when (this) {
        ClusterModel.NTORQ_SMARTXONNECT -> R.string.model_smartxonnect
        ClusterModel.NTORQ_U716 -> R.string.model_u716
        ClusterModel.NTORQ_U577_BASE -> R.string.model_u577_base
        ClusterModel.NTORQ_U577_PREMIUM -> R.string.model_u577_premium
        ClusterModel.NTORQ_U812 -> R.string.model_u812
        ClusterModel.NTORQ_N251 -> R.string.model_n251
        ClusterModel.NTORQ_U458 -> R.string.model_u458
        ClusterModel.NTORQ_U377 -> R.string.model_u377
    }

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    section: SettingsSection?,
    onOpenSection: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
) {
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
    val checkInOk = remember(refresh) { checkInPermissions.all { context.granted(it) } }
    val overlayOk = remember(refresh) { AndroidSettings.canDrawOverlays(context) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val linkReady = link is LinkState.Ready
    val bikes by vm.bikes.collectAsStateWithLifecycle()
    val demoNav by vm.demoNav.collectAsStateWithLifecycle()
    val bluetoothOk = remember(refresh) { context.hasBluetooth() }
    var pairing by remember { mutableStateOf(false) }
    // Bumped after an export or delete so the saved-log summary re-reads the folder.
    var logTick by remember { mutableIntStateOf(0) }
    val exportLogs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val out = uri?.let { context.contentResolver.openOutputStream(it) } ?: return@rememberLauncherForActivityResult
        vm.exportLogs(out) { ok ->
            logTick++
            Toast.makeText(context, if (ok) R.string.settings_logs_exported else R.string.settings_logs_none, Toast.LENGTH_SHORT).show()
        }
    }

    val title = section?.title ?: R.string.tab_forwarding
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (section == null) MediumFlexibleTopAppBar(colors = appBarColors(), title = { Text(stringResource(title)) }, scrollBehavior = scroll)
            else LargeFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            when (section) {
                null -> {
                    item { Section(R.string.settings_group_ride) }
                    item { SectionGroup(listOf(SettingsSection.SCOOTER, SettingsSection.DISPLAY_TEXT, SettingsSection.ON_DISPLAY, SettingsSection.CALLS, SettingsSection.IN_APP), onOpenSection) }
                    item { Section(R.string.settings_about) }
                    item {
                        Group {
                            LinkRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_title), stringResource(R.string.settings_updates_desc), onClick = onOpenUpdates)
                            LinkRow(Icons.Rounded.Info, stringResource(R.string.about_title), stringResource(R.string.settings_about_desc), onClick = onOpenAbout)
                        }
                    }
                    item { Section(R.string.settings_developer) }
                    item { SectionGroup(listOf(SettingsSection.DEVELOPER), onOpenSection) }
                    item { BottomBarSpacer() }
                }
                SettingsSection.DISPLAY_TEXT -> {
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

                }
                SettingsSection.ON_DISPLAY -> {
            item { SwitchRow(R.string.settings_nav, null, s.forwardNavigation, vm::setForwardNavigation) }
            item { SwitchRow(R.string.settings_overspeed, R.string.settings_overspeed_hint, s.overSpeedAlert, vm::setOverSpeedAlert) }
            if (s.overSpeedAlert) {
                item { OverSpeedSlider(s.overSpeedKmh, vm::setOverSpeedKmh) }
            }
            item { SwitchRow(R.string.settings_social, R.string.settings_social_hint, s.showSocial, vm::setShowSocial) }
            item { SwitchRow(R.string.settings_message_alerts, R.string.settings_message_alerts_hint, s.messageAlerts, vm::setMessageAlerts) }
            if (s.messageAlerts) {
                item { SwitchRow(R.string.settings_message_text, R.string.settings_message_text_hint, s.messageText, vm::setMessageText) }
                item { SwitchRow(R.string.settings_message_groups, null, s.messageGroups, vm::setMessageGroups) }
                messageSources.forEach { (source, label) ->
                    item(key = "msg_${source.key}") {
                        SwitchRow(label, null, source.key !in s.messageMuted) { vm.setMessageSource(source.key, it) }
                    }
                }
            }
            item { SwitchRow(R.string.settings_weather, R.string.settings_weather_hint, s.showWeather, vm::setShowWeather) }
            item { SwitchRow(R.string.settings_location, R.string.settings_location_hint, s.showLocation, vm::setShowLocation) }
            item { SwitchRow(R.string.settings_music, R.string.settings_music_hint, s.showMusic, vm::setShowMusic) }
            // Only the SmartXonnect LCD (Race XP) has these.
            if ((s.model ?: detected)?.family == ClusterFamily.SMARTXONNECT) {
                item { Section(R.string.settings_sx_title) }
                item { BrightnessSlider(s.displayBrightness, vm::setDisplayBrightness) }
                item { SwitchRow(R.string.settings_sms_alerts, R.string.settings_sms_alerts_hint, s.smsAlerts, vm::setSmsAlerts) }
                item { DateRow(R.string.settings_birthday, s.birthday, vm::setBirthday) }
                item { DateRow(R.string.settings_purchase_date, s.purchaseDate, vm::setPurchaseDate) }
                item { Section(R.string.settings_experimental) }
                item { SwitchRow(R.string.settings_led_clear, R.string.settings_led_clear_hint, s.ledClear, vm::setLedClear) }
                item { SwitchRow(R.string.settings_delivery, R.string.settings_delivery_hint, s.deliveryAlerts, vm::setDeliveryAlerts) }
                if (s.deliveryAlerts) item { DeliveryCodeSlider(s.deliveryCode, vm::setDeliveryCode) }
                item { SwitchRow(R.string.settings_carbon, R.string.settings_carbon_hint, s.carbon, vm::setCarbon) }
            }

                }
                SettingsSection.CALLS -> {
            item { SwitchRow(R.string.settings_calls, null, s.forwardCalls, vm::setForwardCalls) }
            item {
                SwitchRow(R.string.settings_auto_reply, R.string.settings_auto_reply_hint, s.autoReply) { on ->
                    if (on && !context.hasSms()) askSms.launch(smsPermissions)
                    vm.setAutoReply(on)
                }
            }
            item { SwitchRow(R.string.settings_call_buttons, R.string.settings_call_buttons_hint, s.callButtons, vm::setCallButtons) }
            if (s.callButtons) {
                item {
                    SwitchRow(R.string.settings_reject_sms, R.string.settings_reject_sms_hint, s.rejectSms) { on ->
                        if (on && !context.hasSms()) askSms.launch(smsPermissions)
                        vm.setRejectSms(on)
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
            item { Section(R.string.settings_messages) }
            item { SwitchRow(R.string.settings_chat_reply, R.string.settings_chat_reply_hint, s.chatAutoReply, vm::setChatAutoReply) }
            item {
                SwitchRow(R.string.settings_check_in, R.string.settings_check_in_hint, s.checkIn) { on ->
                    if (on && !checkInOk) askSms.launch(checkInPermissions)
                    vm.setCheckIn(on)
                }
            }
            if (s.checkIn) item { CheckInContact(s.checkInNumber, s.checkInName, checkInOk, vm::setCheckInContact) }
            // One message for every automatic reply.
            if (s.rejectSms || s.autoReply || s.chatAutoReply) {
                item {
                    OutlinedTextField(
                        value = smsText,
                        onValueChange = { smsText = it.take(RejectMessage.MAX); vm.setRejectSmsText(smsText) },
                        label = { Text(stringResource(R.string.settings_riding_message)) },
                        placeholder = { Text(RejectMessage.DEFAULT) },
                        supportingText = {
                            if ((s.rejectSms || s.autoReply) && !smsOk) Text(stringResource(R.string.settings_reject_sms_perm), color = MaterialTheme.colorScheme.error)
                            else Text(stringResource(R.string.settings_riding_message_hint))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
                    )
                }
            }
                }
                SettingsSection.IN_APP -> {
            item { SwitchRow(R.string.settings_ride_data, R.string.settings_ride_data_hint, s.showRideData, vm::setShowRideData) }
            item { SwitchRow(R.string.settings_vehicle, R.string.settings_vehicle_hint, s.showVehicle, vm::setShowVehicle) }
            item { SwitchRow(R.string.settings_record_rides, R.string.settings_record_rides_hint, s.recordRides, vm::setRecordRides) }

                }
                SettingsSection.SCOOTER -> {
            items(bikes, key = { it.address }) { b ->
                val active = b.address == s.deviceAddress
                val label = b.name ?: b.address
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(if (active) stringResource(R.string.settings_bike_active, b.address) else b.address) },
                    leadingContent = { RadioButton(selected = active, onClick = null) },
                    trailingContent = {
                        IconButton(onClick = { vm.remove(b) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.settings_remove_scooter, label),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.selectable(selected = active, role = Role.RadioButton) { vm.select(b) },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = { pairing = true }, enabled = bluetoothOk) {
                        Text(stringResource(R.string.settings_add_scooter))
                    }
                }
            }
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

                }
                SettingsSection.DEVELOPER -> {
            item { SwitchRow(R.string.settings_developer_mode, R.string.settings_developer_mode_hint, s.developerMode, vm::setDeveloperMode) }
            if (s.developerMode) {
                item { LinkRow(Icons.Rounded.Terminal, stringResource(R.string.tab_console), stringResource(R.string.settings_console_desc), onClick = onOpenConsole) }
                item { SwitchRow(R.string.settings_demo_nav, R.string.settings_demo_nav_hint, demoNav, vm::setDemoNav) }
                item { SwitchRow(R.string.settings_save_logs, R.string.settings_save_logs_hint, s.saveLogs, vm::setSaveLogs) }
                item {
                    val saved = remember(logTick) { vm.savedLogs() }
                    LinkRow(
                        Icons.Rounded.Download,
                        stringResource(R.string.settings_export_logs),
                        if (saved.isEmpty()) stringResource(R.string.settings_logs_none)
                        else stringResource(R.string.settings_logs_saved, saved.size, saved.sumOf { it.length() } / 1024.0),
                        enabled = saved.isNotEmpty(),
                    ) { exportLogs.launch("ridelink-log-${java.time.LocalDate.now()}.txt") }
                }
                item {
                    LinkRow(Icons.Rounded.DeleteSweep, stringResource(R.string.settings_clear_logs), stringResource(R.string.settings_clear_logs_desc)) {
                        vm.clearLogs { logTick++ }
                    }
                }
            }
                }
            }
            item { Spacer(Modifier.height(Dimens.xl)) }
        }
    }

    if (pairing) PairSheet(vm) { pairing = false }
}

/**
 * Who gets the check-in texts: typed, or picked from Contacts. The phone-number picker grants a one-time read of
 * the picked row, so no contacts permission is needed.
 */
@Composable
private fun CheckInContact(number: String, name: String, permsOk: Boolean, onChange: (String, String) -> Unit) {
    val context = LocalContext.current
    var typed by remember(number) { mutableStateOf(number) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) onChange(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
        }
    }
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { v -> typed = v.filter { it.isDigit() || it in "+ -" }.take(20); onChange(typed, "") },
            label = { Text(stringResource(R.string.settings_check_in_number)) },
            supportingText = {
                if (!permsOk) Text(stringResource(R.string.settings_check_in_perm), color = MaterialTheme.colorScheme.error)
                else if (name.isNotBlank()) Text(name)
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                runCatching { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
            }) { Text(stringResource(R.string.settings_check_in_pick)) }
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

/** SmartXonnect backlight, stock levels 2 (low) to 5 (high); saved when the finger lifts. */
@Composable
private fun BrightnessSlider(saved: Int, onSave: (Int) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
    val range = Settings.BRIGHTNESS_RANGE
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(stringResource(R.string.settings_brightness, value.roundToInt() - range.first + 1, range.last - range.first + 1), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onSave(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

/** Delivery alert code 1..20; which one the display shows is found with the Console alert sweep. */
@Composable
private fun DeliveryCodeSlider(saved: Int, onSave: (Int) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved.toFloat().coerceIn(1f, 20f)) }
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(stringResource(R.string.settings_delivery_code, value.roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onSave(value.roundToInt()) }, valueRange = 1f..20f, steps = 18)
    }
}

/** A date the display greets on; tap to pick, "Clear" to stop sending it. Stored as yyyy-MM-dd. */
@Composable
private fun DateRow(title: Int, iso: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val date = remember(iso) { runCatching { java.time.LocalDate.parse(iso) }.getOrNull() }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(date?.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) ?: stringResource(R.string.settings_date_none)) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text(stringResource(R.string.settings_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { onPick(""); open = false }) { Text(stringResource(R.string.settings_date_clear)) }
            },
        ) { DatePicker(state) }
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

/** Pop-up filters, one switch per source. */
private val messageSources = listOf(
    MessageAlert.Source.WHATSAPP to R.string.settings_message_whatsapp,
    MessageAlert.Source.INSTAGRAM to R.string.settings_message_instagram,
    MessageAlert.Source.FACEBOOK to R.string.settings_message_facebook,
    MessageAlert.Source.SMS to R.string.settings_message_sms,
    MessageAlert.Source.OTHER to R.string.settings_message_other,
    MessageAlert.Source.CALL to R.string.settings_message_missed_calls,
    MessageAlert.Source.DELIVERY to R.string.settings_message_delivery,
)

@Composable
private fun SwitchRow(title: Int, hint: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = hint?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** Settings home groups these; each opens as its own page. */
enum class SettingsSection(val title: Int, val summary: Int, val icon: ImageVector) {
    SCOOTER(R.string.settings_scooter, R.string.settings_scooter_desc, Icons.Rounded.TwoWheeler),
    DISPLAY_TEXT(R.string.settings_display_text, R.string.settings_display_text_desc, Icons.Rounded.TextFields),
    ON_DISPLAY(R.string.settings_forwarding, R.string.settings_forwarding_desc, Icons.Rounded.Navigation),
    CALLS(R.string.settings_calls_section, R.string.settings_calls_section_desc, Icons.Rounded.Call),
    IN_APP(R.string.settings_in_app, R.string.settings_in_app_desc, Icons.Rounded.PhoneAndroid),
    DEVELOPER(R.string.settings_developer, R.string.settings_developer_desc, Icons.Rounded.Code),
}

@Composable
private fun SectionGroup(sections: List<SettingsSection>, onOpen: (SettingsSection) -> Unit) = Group {
    sections.forEach { sec -> LinkRow(sec.icon, stringResource(sec.title), stringResource(sec.summary)) { onOpen(sec) } }
}

/** Rounded card holding a run of rows, like Rewire's Profile and About. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column { content() } }
}
