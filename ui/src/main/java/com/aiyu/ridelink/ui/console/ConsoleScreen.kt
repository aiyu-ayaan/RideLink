package com.aiyu.ridelink.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import com.aiyu.ridelink.ui.nav.appBarColors
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.update.BackButton
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens

/** Manual frame sender and log; used to test against tools/fake_cluster.py or the real display. */
@Composable
fun ConsoleScreen(vm: MainViewModel, onBack: () -> Unit) {
    val log by vm.log.collectAsStateWithLifecycle()
    val caller by vm.caller.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val sweep by vm.arrowSweep.collectAsStateWithLifecycle()
    val alerts by vm.alertSweep.collectAsStateWithLifecycle()
    val riding by vm.pretendRiding.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.tab_console)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Oldest first, so a pasted log reads in time order.
                    IconButton(onClick = {
                        val text = log.asReversed().joinToString("\n") { it.line() }
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("RideLink log", text))
                    }, enabled = log.isNotEmpty()) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.console_copy))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = Dimens.gutter), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(17) },
                    label = { Text(stringResource(R.string.console_text_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.sendText(text) }, enabled = text.isNotBlank(), modifier = Modifier.padding(start = Dimens.sm)) {
                    Text(stringResource(R.string.console_send))
                }
            }
            Row(
                Modifier.padding(Dimens.gutter).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { vm.sendTestTurn() },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.console_test_turn)) }
                ToggleButton(
                    checked = caller != null,
                    onCheckedChange = vm::testCall,
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.console_test_call)) }
                ToggleButton(
                    checked = demo,
                    onCheckedChange = vm::setDemo,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.console_demo)) }
            }
            // One button per TFT frame, so each can be checked on the display on its own.
            FlowRow(
                Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                TestFrameChip(R.string.console_weather) { vm.sendTest(ClusterController.TestFrame.WEATHER) }
                TestFrameChip(R.string.console_location) { vm.sendTest(ClusterController.TestFrame.LOCATION) }
                TestFrameChip(R.string.console_social) { vm.sendTest(ClusterController.TestFrame.SOCIAL) }
                TestFrameChip(R.string.console_music) { vm.sendTest(ClusterController.TestFrame.MUSIC) }
                TestFrameChip(R.string.console_default_screen) { vm.sendTest(ClusterController.TestFrame.DEFAULT_SCREEN) }
                // Raw arrow codes with "CODE n" on the display, to see which numbers this display draws.
                FilterChip(selected = sweep, onClick = { vm.setArrowSweep(!sweep) }, label = { Text(stringResource(R.string.console_arrow_sweep)) })
                FilterChip(selected = alerts, onClick = { vm.setAlertSweep(!alerts) }, label = { Text(stringResource(R.string.console_alert_sweep)) })
            }
            // Phone-side features: same code path as the real trigger; results show as notes in the log below.
            Text(
                stringResource(R.string.console_features),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.gutter, top = Dimens.sm),
            )
            FlowRow(
                Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                TestFrameChip(R.string.console_test_refuel, vm::testRefuel)
                TestFrameChip(R.string.console_test_low_fuel, vm::testLowFuel)
                TestFrameChip(R.string.console_test_due, vm::testDue)
                TestFrameChip(R.string.console_test_parked, vm::testParked)
                TestFrameChip(R.string.console_test_check_in, vm::testCheckIn)
                FilterChip(selected = riding, onClick = { vm.setPretendRiding(!riding) }, label = { Text(stringResource(R.string.console_pretend_riding)) })
            }
            if (log.isEmpty()) {
                Text(
                    stringResource(R.string.console_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.gutter),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(log) { f ->
                    ListItem(
                        overlineContent = { Text(f.time) },
                        headlineContent = { Text(f.hex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
                        supportingContent = f.decoded?.let { d -> { Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                        leadingContent = {
                            Icon(
                                when {
                                    f.rx -> Icons.Rounded.Download
                                    f.ok -> Icons.Rounded.CheckCircle
                                    else -> Icons.Rounded.ErrorOutline
                                },
                                contentDescription = stringResource(
                                    when {
                                        f.rx -> R.string.console_received
                                        f.ok -> R.string.console_delivered
                                        else -> R.string.console_failed
                                    },
                                ),
                                tint = when {
                                    f.rx -> MaterialTheme.colorScheme.tertiary
                                    f.ok -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TestFrameChip(label: Int, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(stringResource(label)) })
}
