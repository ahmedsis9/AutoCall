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

    /** وقت الموعد الدقيق لمرة واحدة بتاريخ محدد. */
    fun onceTime(a: AlarmItem): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = a.date
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    /** أقرب وقت قادم للموعد، أو null إن لم يوجد. */
    fun nextTrigger(a: AlarmItem, from: Long = System.currentTimeMillis()): Long? {
        if (a.repeat == AlarmItem.ONCE && a.date > 0L) {
            val t = onceTime(a)
            return if (t > from) t else null
        }
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
    fun schedule(ctx: Context, a: AlarmItem, from: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, a.id))
        if (!a.enabled) return
        val t = nextTrigger(a, from) ?: return
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi(ctx, a.id))
        } catch (e: SecurityException) {
            // احتياط: لا ينهار التطبيق إن رفض النظام المنبّه الدقيق
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(ctx, a.id))
            } catch (e2: Exception) {
                // لا شيء
            }
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, id))
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.all(ctx).forEach { schedule(ctx, it) }
    }
}
