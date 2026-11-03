package com.aiyu.ridelink.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.settings.Settings
import kotlin.math.roundToInt
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.R
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

    Scaffold(topBar = { MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.tab_forwarding)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Section(R.string.settings_forwarding) }
            item { SwitchRow(R.string.settings_nav, null, s.forwardNavigation, vm::setForwardNavigation) }
            item { SwitchRow(R.string.settings_calls, null, s.forwardCalls, vm::setForwardCalls) }
            item { SwitchRow(R.string.settings_call_buttons, R.string.settings_call_buttons_hint, s.callButtons, vm::setCallButtons) }
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
                OutlinedTextField(
                    value = rider,
                    onValueChange = { rider = it.take(17); vm.setRiderName(rider) },
                    label = { Text(stringResource(R.string.settings_rider)) },
                    supportingText = { Text(stringResource(R.string.settings_rider_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
                )
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
