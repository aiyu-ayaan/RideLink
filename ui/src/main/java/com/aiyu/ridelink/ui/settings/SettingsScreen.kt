package com.aiyu.ridelink.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.protocol.ClusterModel
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
    var rider by remember(s.riderName) { mutableStateOf(s.riderName) }

    Scaffold(topBar = { MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.tab_forwarding)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Section(R.string.settings_forwarding) }
            item { SwitchRow(R.string.settings_nav, null, s.forwardNavigation, vm::setForwardNavigation) }
            item { SwitchRow(R.string.settings_calls, null, s.forwardCalls, vm::setForwardCalls) }
            item { SwitchRow(R.string.settings_messages, R.string.settings_messages_hint, s.forwardMessages, vm::setForwardMessages) }

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
                        Text(stringResource(R.string.model_auto_using, stringResource((detected ?: ClusterModel.DEFAULT).label)))
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
