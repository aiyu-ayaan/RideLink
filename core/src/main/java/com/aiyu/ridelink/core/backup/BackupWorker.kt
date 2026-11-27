package com.aiyu.ridelink.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs one [BackupRepository.run] for WorkManager: the scheduled backup and the rider's "Back up now" / conflict
 * choices. Reaches the repository through a Hilt entry point, so the default WorkManager initializer is enough.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backup(): BackupRepository
    }

    override suspend fun doWork(): Result {
        val mode = inputData.getString(MODE)?.let { runCatching { BackupMode.valueOf(it) }.getOrNull() } ?: BackupMode.BACKUP
        val repo = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).backup()
        return when (val outcome = repo.run(mode, byRider = inputData.getBoolean(BY_RIDER, false))) {
            BackupOutcome.Done, is BackupOutcome.NeedsChoice, is BackupOutcome.Deleted -> Result.success()
            is BackupOutcome.Failed -> when (outcome.error) {
                // Lost the network mid-way: WorkManager tries again with backoff.
                BackupError.OFFLINE -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val MODE = "mode"
        /** True for "Back up now" and the conflict choices; false for the schedule and the check after sign-in. */
        const val BY_RIDER = "by_rider"
        private const val MAX_RETRIES = 3
    }
}
