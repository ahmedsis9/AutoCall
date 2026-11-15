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

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
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

    fun show(ctx: Context, a: AlarmItem, text: String) {
        ensureChannel(ctx)
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
        ctx.getSystemService(NotificationManager::class.java).notify(a.id, n)
    }

    /** إشعار بملء الشاشة يفتح CallActivity ليضع المكالمة من الواجهة الأمامية. */
    fun showCallFallback(ctx: Context, a: AlarmItem, text: String) {
        ensureChannel(ctx)
        val target = if (CallHelper.hasPermission(ctx)) {
            Intent(ctx, CallActivity::class.java).putExtra("id", a.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            Intent(ctx, MainActivity::class.java)
        }
        val pi = PendingIntent.getActivity(
            ctx, 5000 + a.id, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a.name)
            .setContentText("$text — اضغط للاتصال الآن")
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(a.id, n)
    }
}
