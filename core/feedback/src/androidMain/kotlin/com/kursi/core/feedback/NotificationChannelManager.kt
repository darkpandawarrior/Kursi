package com.kursi.core.feedback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannelManager {
    const val CHANNEL_GAME_INVITES = "game_invites"
    const val CHANNEL_SYSTEM = "system"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_GAME_INVITES,
                    "Game Invites",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifications for game invitations and match alerts"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "General app notifications"
                },
            ),
        )
    }
}
