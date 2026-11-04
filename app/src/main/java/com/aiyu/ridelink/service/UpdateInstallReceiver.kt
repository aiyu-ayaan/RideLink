package com.aiyu.ridelink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.aiyu.ridelink.core.update.AppUpdater
import com.aiyu.ridelink.core.update.UpdateState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * How an install ended. A PackageInstaller session answers here:
 *  - PENDING_USER_ACTION: the system's confirmation dialog, handed back rather than shown; start it.
 *  - SUCCESS: nothing to do, this process is about to be replaced.
 *  - anything else: a reason for the Updates screen. The common one is an APK signed with another key.
 * Not exported: only the platform may answer a session this app created.
 */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var updater: AppUpdater

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppUpdater.ACTION_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // started from a receiver: no task of its own
                runCatching { context.startActivity(confirm) }.onFailure { updater.fail(UpdateState.Reason.SCREEN, it.message) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.dismiss()
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updater.fail(UpdateState.Reason.SIGNATURE)
            else -> updater.fail(UpdateState.Reason.INSTALL, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
}
