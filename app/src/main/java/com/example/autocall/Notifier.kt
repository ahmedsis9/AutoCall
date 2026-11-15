package com.example.autocall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

object Notifier {
    private const val CHANNEL = "hamko_alerts"

    fun show(ctx: Context, a: AlarmItem, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ch = NotificationChannel(CHANNEL, "تنبيهات Hamko", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            ctx, a.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a.name)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        nm.notify(a.id, n)
    }
}
