package com.example.autocall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/** يعمل عند حلول موعد المؤقت، حتى والشاشة مقفلة. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hamko:alarm")
        wl.acquire(30_000L)
        try {
            handle(ctx, id)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun handle(ctx: Context, id: Int) {
        val a = AlarmStore.get(ctx, id) ?: return
        if (a.status != AlarmItem.SCHEDULED) return

        val now = System.currentTimeMillis()
        var cur = a.copy(lastRun = now)

        // مرة واحدة => مكتمل. متكرر => جدولة الموعد التالي ويبقى «مجدولاً».
        if (a.repeat == AlarmItem.ONCE) {
            cur = cur.copy(status = AlarmItem.COMPLETED)
        } else {
            AlarmScheduler.schedule(ctx, cur, now + 60_000L)
        }

        val result: String
        if (a.autoCall) {
            val err = CallHelper.place(ctx, a.number)
            if (err == null) {
                result = "تم الاتصال بـ ${a.number}"
                Notifier.show(ctx, a, "جارٍ الاتصال بـ ${a.number}")
            } else {
                result = "تعذّر الاتصال: $err"
                Notifier.showCallFallback(ctx, a, "تعذّر الاتصال تلقائياً ($err)")
            }
        } else {
            result = "تم التنبيه"
            Notifier.show(ctx, a, "حان موعد التنبيه")
        }
        AlarmStore.upsert(ctx, cur.copy(lastResult = result))
    }
}

/** يعيد جدولة كل المواعيد بعد إعادة تشغيل الجهاز أو تحديث التطبيق. */
class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmStore.reconcile(ctx)
        AlarmScheduler.rescheduleAll(ctx)
    }
}
