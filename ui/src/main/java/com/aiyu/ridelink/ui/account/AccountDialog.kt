package com.aiyu.ridelink.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens

/**
 * Google account switcher, Play Store style: a wide card near the top with the account, "Manage backup", and the
 * account actions. Signed out it holds the sign-in button. Replaces the old Settings -> Google account page.
 */
@Composable
fun AccountDialog(
    accounts: AccountViewModel,
    backupLine: String,
    onOpenBackup: () -> Unit,
    onDeleteCloud: () -> Unit,
    onDismiss: () -> Unit,
) {
    val account by accounts.account.collectAsStateWithLifecycle()
    val photo by accounts.photo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Tapping outside the card closes it, like the Play Store account sheet.
        Box(
            Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = null).safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = Dimens.lg, vertical = Dimens.xxl)
                    .widthIn(max = Dimens.contentMaxWidth)
                    .fillMaxWidth()
                    .clickable(onClick = {}, indication = null, interactionSource = null),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(Dimens.sm)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch), contentAlignment = Alignment.Center) {
                        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.account_close))
                        }
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm),
                    ) {
                        val a = account
                        if (a == null) {
                            Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.size(Dimens.md))
                                    Text(stringResource(R.string.settings_account_signed_out), style = MaterialTheme.typography.titleMedium)
                                }
                                Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                accounts.error?.let {
                                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                GoogleSignInButton(accounts.busy, { accounts.signIn(context) })
                            }
                        } else {
                            Column {
                                ListItem(
                                    headlineContent = { Text(a.name ?: a.email.orEmpty(), style = MaterialTheme.typography.titleMedium) },
                                    supportingContent = a.email?.takeIf { a.name != null }?.let { { Text(it) } },
                                    leadingContent = { ProfileAvatar(a, photo, 40.dp) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                                OutlinedButton(
                                    onClick = onOpenBackup,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Dimens.md).heightIn(min = Dimens.minTouch),
                                ) { Text(stringResource(R.string.account_manage_backup)) }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Icons.Rounded.CloudUpload, stringResource(R.string.backup_title), backupLine, onClick = onOpenBackup)
                                Row(Icons.Rounded.Delete, stringResource(R.string.account_delete_cloud), null, danger = true, onClick = onDeleteCloud)
                                Row(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.account_sign_out), stringResource(R.string.account_sign_out_desc)) {
                                    accounts.signOut()
                                    onDismiss()
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.settings_account_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Dimens.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun Row(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
