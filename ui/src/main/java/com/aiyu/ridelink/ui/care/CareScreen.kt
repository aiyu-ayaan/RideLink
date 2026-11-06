package com.aiyu.ridelink.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.care.CareItem
import com.aiyu.ridelink.core.care.CareRecord
import com.aiyu.ridelink.core.care.CareStatus
import com.aiyu.ridelink.core.care.Fill
import com.aiyu.ridelink.core.care.Urgency
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.nav.appBarColors
import com.aiyu.ridelink.ui.ride.Tile
import com.aiyu.ridelink.ui.ride.cardItemColors
import com.aiyu.ridelink.ui.theme.Dimens
import com.aiyu.ridelink.ui.update.BackButton
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private fun one(x: Double) = "%.1f".format(x)
private fun km(tenths: Int) = one(tenths / 10.0)
/** Prices in rupees whatever the phone's region: the scooters are sold in India. */
private val rupees: NumberFormat = NumberFormat.getCurrencyInstance(java.util.Locale.Builder().setLanguage("en").setRegion("IN").build())
private fun money(x: Double) = rupees.format(x)
private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Typed numbers: a comma also works as the decimal mark. */
private fun parse(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

/** Fuel log with real mileage, service items by distance, paper expiry dates, and the alert switches. */
@Composable
fun CareScreen(onBack: () -> Unit, vm: CareViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    // null = closed; a Fill with id 0 = new stop.
    var editingFill by remember { mutableStateOf<Fill?>(null) }
    var editingItem by remember { mutableStateOf<CareRecord?>(null) }
    var pickingDate by remember { mutableStateOf<CareRecord?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.care_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            item { MileageCard(ui) }
            ui.pendingRefuel?.let { f ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.gutter, vertical = Dimens.xs)
                            .clickable(role = Role.Button) { editingFill = f },
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.care_card_refuel, km(f.odometerTenths))) },
                            leadingContent = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
                            colors = cardItemColors(MaterialTheme.colorScheme.onTertiaryContainer),
                        )
                    }
                }
            }

            item { Header(R.string.care_fuel_log) }
            if (ui.fills.isEmpty()) item { Hint(stringResource(R.string.care_fill_empty)) }
            items(ui.fills, key = { it.id }) { f -> FillRow(f, onClick = { editingFill = f }, onDelete = { vm.deleteFill(f.id) }) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = {
                        editingFill = Fill(0, null, System.currentTimeMillis(), ui.odometerTenths ?: 0, null, null, full = true, auto = false)
                    }) { Text(stringResource(R.string.care_add_fill)) }
                }
            }

            item { Header(R.string.care_service) }
            item { Hint(stringResource(R.string.care_service_hint)) }
            items(ui.statuses.filter { !it.record.item.byDate }, key = { it.record.item.name }) { st ->
                ItemRow(st) { editingItem = st.record }
            }

            item { Header(R.string.care_papers) }
            items(ui.statuses.filter { it.record.item.byDate }, key = { it.record.item.name }) { st ->
                ItemRow(st) { pickingDate = st.record }
            }

            item { Header(R.string.care_options) }
            item { SwitchRow(R.string.care_refuel_detect, R.string.care_refuel_detect_hint, s.refuelDetect, vm::setRefuelDetect) }
            item { SwitchRow(R.string.care_low_fuel, R.string.care_low_fuel_hint, s.lowFuelAlert, vm::setLowFuelAlert) }
            if (s.lowFuelAlert) item { LowFuelSlider(s.lowFuelKm, vm::setLowFuelKm) }
            item { SwitchRow(R.string.care_reminders, R.string.care_reminders_hint, s.careReminders, vm::setCareReminders) }
            item { Spacer(Modifier.height(Dimens.xxl)) }
        }
    }

    editingFill?.let { f ->
        FillSheet(f, onDismiss = { editingFill = null }) { saved ->
            if (saved.id == 0L) vm.addFill(saved.odometerTenths, saved.litres, saved.cost, saved.full) else vm.updateFill(saved)
            editingFill = null
        }
    }
    editingItem?.let { r ->
        ItemSheet(r, ui.odometerTenths, onDismiss = { editingItem = null }) { vm.save(it); editingItem = null }
    }
    pickingDate?.let { r ->
        ExpiryPicker(r.due, onDismiss = { pickingDate = null }) { vm.setDue(r.item, it); pickingDate = null }
    }
}

/** Ride screen entry: the one thing that needs attention, else the mileage. */
@Composable
fun CareCard(onOpen: () -> Unit, vm: CareViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val due = ui.statuses.filter { it.urgency == Urgency.OVERDUE || it.urgency == Urgency.SOON }
        .minByOrNull { if (it.urgency == Urgency.OVERDUE) 0 else 1 }
    val pending = ui.pendingRefuel
    val avg = ui.averageKmpl
    val (text, attention) = when {
        pending != null -> stringResource(R.string.care_card_refuel, km(pending.odometerTenths)) to true
        due != null -> statusLine(due) to true
        avg != null -> stringResource(R.string.care_card_mileage, one(avg)) to false
        else -> stringResource(R.string.care_card_empty) to false
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.care_title)) },
            supportingContent = { Text(text) },
            leadingContent = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
            colors = cardItemColors(if (attention) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun statusLine(st: CareStatus): String {
    val name = stringResource(st.record.item.label)
    val kmLeft = st.kmLeft
    val days = st.daysLeft
    return when {
        kmLeft != null && kmLeft < 0 -> stringResource(R.string.care_overdue_km, name, -kmLeft)
        kmLeft != null -> stringResource(R.string.care_left_km, name, kmLeft)
        days != null && days < 0 -> stringResource(R.string.care_expired, name)
        days != null -> stringResource(R.string.care_left_days, name, days.toInt())
        else -> name
    }
}

@Composable
private fun MileageCard(ui: CareUi) {
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.care_mileage), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
            val avg = ui.averageKmpl
            if (avg != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(one(avg), style = MaterialTheme.typography.displayMediumEmphasized, color = content)
                    Text(" " + stringResource(R.string.unit_kmpl), style = MaterialTheme.typography.titleMedium, color = content)
                }
            } else {
                Text(stringResource(R.string.care_mileage_none), style = MaterialTheme.typography.titleMedium, color = content)
            }
            Text(stringResource(R.string.care_mileage_hint), style = MaterialTheme.typography.bodySmall, color = content)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.care_last_fill), ui.lastKmpl?.let { one(it) } ?: "--", stringResource(R.string.unit_kmpl), content, Modifier.weight(1f))
                Tile(stringResource(R.string.care_cost_per_km), ui.costPerKm?.let { money(it) } ?: "--", "", content, Modifier.weight(1f))
                Tile(stringResource(R.string.care_odometer), ui.odometerTenths?.let { (it / 10).toString() } ?: "--", stringResource(R.string.unit_km), content, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FillRow(f: Fill, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = Instant.ofEpochMilli(f.t).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
    val tags = listOfNotNull(
        stringResource(R.string.care_fill_auto).takeIf { f.auto },
        stringResource(R.string.care_fill_partial).takeIf { !f.full },
    )
    ListItem(
        headlineContent = {
            Text(
                f.litres?.let { stringResource(R.string.care_fill_line, one(it), f.cost?.let(::money) ?: "--") }
                    ?: stringResource(R.string.care_fill_line_unknown),
            )
        },
        supportingContent = {
            Text((listOf(stringResource(R.string.care_fill_meta, km(f.odometerTenths), date)) + tags).joinToString(" · "))
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.care_delete_fill), tint = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ItemRow(st: CareStatus, onClick: () -> Unit) {
    val r = st.record
    val c = MaterialTheme.colorScheme
    val statusColor = when (st.urgency) {
        Urgency.OVERDUE -> c.error
        Urgency.SOON -> c.tertiary
        else -> c.onSurfaceVariant
    }
    val kmLeft = st.kmLeft
    val days = st.daysLeft
    val status = when {
        kmLeft != null && kmLeft < 0 -> stringResource(R.string.care_status_overdue, -kmLeft)
        kmLeft != null -> stringResource(R.string.care_status_km, kmLeft)
        days != null && days < 0 -> stringResource(R.string.care_status_expired)
        days != null -> stringResource(R.string.care_status_days, days.toInt())
        else -> null
    }
    val detail = when {
        r.item.byDate -> r.due?.let { stringResource(R.string.care_expires, it.format(dateFormat)) } ?: stringResource(R.string.care_date_none)
        r.doneOdometerTenths == null -> stringResource(R.string.care_not_set)
        else -> stringResource(R.string.care_every, r.everyKm ?: 0)
    }
    ListItem(
        headlineContent = { Text(stringResource(r.item.label)) },
        supportingContent = { Text(detail) },
        trailingContent = { status?.let { Text(it, color = statusColor, style = MaterialTheme.typography.labelLarge) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FillSheet(fill: Fill, onDismiss: () -> Unit, onSave: (Fill) -> Unit) {
    var odo by remember { mutableStateOf(if (fill.odometerTenths > 0) km(fill.odometerTenths) else "") }
    var litres by remember { mutableStateOf(fill.litres?.toString().orEmpty()) }
    var cost by remember { mutableStateOf(fill.cost?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
    var full by remember { mutableStateOf(fill.full) }
    val odoValue = parse(odo)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.care_add_fill), style = MaterialTheme.typography.headlineSmall)
            NumberField(odo, R.string.care_fill_odometer) { odo = it }
            NumberField(litres, R.string.care_fill_litres) { litres = it }
            NumberField(cost, R.string.care_fill_cost) { cost = it }
            ListItem(
                headlineContent = { Text(stringResource(R.string.care_fill_full)) },
                supportingContent = { Text(stringResource(R.string.care_fill_full_hint)) },
                trailingContent = { Switch(checked = full, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = full, role = Role.Switch) { full = it },
            )
            Button(
                onClick = {
                    onSave(
                        fill.copy(
                            odometerTenths = ((odoValue ?: 0.0) * 10).roundToInt(),
                            litres = parse(litres)?.takeIf { it > 0 },
                            cost = parse(cost)?.takeIf { it > 0 },
                            full = full,
                        ),
                    )
                },
                enabled = odoValue != null && odoValue > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.care_save)) }
        }
    }
}

@Composable
private fun ItemSheet(record: CareRecord, odometerTenths: Int?, onDismiss: () -> Unit, onSave: (CareRecord) -> Unit) {
    var every by remember { mutableStateOf(record.everyKm?.toString().orEmpty()) }
    var done by remember { mutableStateOf(record.doneOdometerTenths?.let { (it / 10).toString() }.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(record.item.label), style = MaterialTheme.typography.headlineSmall)
            NumberField(every, R.string.care_interval) { every = it }
            NumberField(done, R.string.care_done_at) { done = it }
            if (odometerTenths != null) {
                OutlinedButton(onClick = { done = (odometerTenths / 10).toString() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.care_done_now, km(odometerTenths)))
                }
            }
            Button(
                onClick = {
                    onSave(
                        record.copy(
                            everyKm = parse(every)?.roundToInt()?.takeIf { it > 0 } ?: record.item.defaultEveryKm,
                            doneOdometerTenths = parse(done)?.let { (it * 10).roundToInt() },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.care_save)) }
        }
    }
}

@Composable
private fun ExpiryPicker(current: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: current)
            }) { Text(stringResource(R.string.settings_date_ok)) }
        },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.settings_date_clear)) } },
    ) { DatePicker(state) }
}

@Composable
private fun NumberField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Header(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.gutter, top = Dimens.xl, bottom = Dimens.xs),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.xs),
    )
}

@Composable
private fun SwitchRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** Range limit in 5 km steps; saved when the finger lifts. */
@Composable
private fun LowFuelSlider(saved: Int, onSave: (Int) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
    val range = Settings.LOW_FUEL_RANGE
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(stringResource(R.string.care_low_fuel_limit, value.roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onSave(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1,
        )
    }
}
