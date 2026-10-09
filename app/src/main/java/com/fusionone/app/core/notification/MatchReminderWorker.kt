package com.fusionone.app.core.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires a single local notification for a match reminder. This is a genuine, local-only
 * notification scheduled by WorkManager — no push server, no backend, exactly what
 * "kickoff reminders" can honestly mean without a server component.
 */
@HiltWorker
class MatchReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val homeTeam = inputData.getString(KEY_HOME_TEAM) ?: "Home"
        val awayTeam = inputData.getString(KEY_AWAY_TEAM) ?: "Away"
        val matchId = inputData.getLong(KEY_MATCH_ID, 0L)

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return Result.success() // Nothing we can do without the permission

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.KICKOFF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Kicking off soon")
            .setContentText("$homeTeam vs $awayTeam is about to start")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(matchId.toInt(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_MATCH_ID = "match_id"
        const val KEY_HOME_TEAM = "home_team"
        const val KEY_AWAY_TEAM = "away_team"
    }
}
