package com.fusionone.app.core.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules/cancels a real, local kickoff reminder via WorkManager — no backend involved.
 * WorkManager's own persisted work state doubles as our "is this match reminder set?"
 * store, so there's no need for a separate database table.
 */
@Singleton
class MatchReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    private fun uniqueWorkName(matchId: Long) = "kickoff_reminder_$matchId"

    fun schedule(matchId: Long, homeTeam: String, awayTeam: String, kickoffUtc: String, leadTimeMinutes: Long = 10) {
        val kickoffInstant = runCatching { OffsetDateTime.parse(kickoffUtc).toInstant() }.getOrNull() ?: return
        val fireAt = kickoffInstant.minusSeconds(leadTimeMinutes * 60)
        val delayMillis = fireAt.toEpochMilli() - Instant.now().toEpochMilli()
        if (delayMillis <= 0) return // Kickoff (minus lead time) has already passed

        val data = Data.Builder()
            .putLong(MatchReminderWorker.KEY_MATCH_ID, matchId)
            .putString(MatchReminderWorker.KEY_HOME_TEAM, homeTeam)
            .putString(MatchReminderWorker.KEY_AWAY_TEAM, awayTeam)
            .build()

        val request = OneTimeWorkRequestBuilder<MatchReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(uniqueWorkName(matchId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(matchId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(matchId))
    }

    suspend fun isScheduled(matchId: Long): Boolean {
        val infos = workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(matchId)).first()
        return infos.any { it.state == WorkInfo.State.ENQUEUED }
    }
}
