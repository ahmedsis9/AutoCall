package com.example.autocall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifier {
    private const val CH_CALL = "hamko_calls_v2"
    private const val CH_STATUS = "hamko_status_v2"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_CALL) == null) {
            // قناة للحالات التي تحتاج تدخّلك: صوت عادي (وليس صوت منبّه)
            nm.createNotificationChannel(
                NotificationChannel(CH_CALL, "مكالمات Hamko (تحتاج تدخلك)", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (nm.getNotificationChannel(CH_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_STATUS, "حالة Hamko", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** إشعار هادئ للمعلومات. */
    fun show(ctx: Context, a: AlarmItem, text: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, a.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(a.name)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
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
        val n = Notification.Builder(ctx, CH_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(a.name)
            .setContentText("$text — اضغط للاتصال الآن")
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(a.id, n)
    }
}
