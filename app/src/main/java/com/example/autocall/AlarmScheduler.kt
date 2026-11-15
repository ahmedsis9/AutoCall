package com.example.autocall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {

    private fun pi(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** أقرب وقت قادم للموعد، أو null إن لم يوجد. */
    fun nextTrigger(a: AlarmItem, from: Long = System.currentTimeMillis()): Long? {
        for (offset in 0..7) {
            val c = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, a.hour)
                set(Calendar.MINUTE, a.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= from) continue
            val ok = if (a.repeat == AlarmItem.CUSTOM) a.days.contains(c.get(Calendar.DAY_OF_WEEK)) else true
            if (ok) return c.timeInMillis
        }
        return null
    }

    /** setAlarmClock لا يحتاج إذن المنبهات الدقيقة ويعمل حتى مع وضع Doze والشاشة مقفلة. */
    fun schedule(ctx: Context, a: AlarmItem) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, a.id))
        if (!a.enabled) return
        val t = nextTrigger(a) ?: return
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi(ctx, a.id))
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, id))
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.all(ctx).forEach { schedule(ctx, it) }
    }
}
