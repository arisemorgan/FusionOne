package com.fusionone.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val KICKOFF_CHANNEL_ID = "fusionone_kickoff_reminders"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                KICKOFF_CHANNEL_ID,
                "Kickoff reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders shortly before a match you've flagged kicks off"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
