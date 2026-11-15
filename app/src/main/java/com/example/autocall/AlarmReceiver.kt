package com.example.autocall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

/** يعمل عند حلول موعد المؤقت، حتى والشاشة مقفلة. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val a = AlarmStore.get(ctx, id) ?: return
        if (!a.enabled) return

        // جدولة الموعد التالي (أو إيقافه إن كان مرة واحدة)
        if (a.repeat == AlarmItem.ONCE) {
            AlarmStore.upsert(ctx, a.copy(enabled = false))
        } else {
            AlarmScheduler.schedule(ctx, a)
        }

        if (a.autoCall) {
            if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Notifier.show(ctx, a, "تعذّر الاتصال: إذن الاتصال غير ممنوح")
                return
            }
            try {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(Uri.fromParts("tel", a.number, null), Bundle())
                Notifier.show(ctx, a, "جارٍ الاتصال بـ ${a.number}")
            } catch (e: Exception) {
                Notifier.show(ctx, a, "تعذّر الاتصال: ${e.message ?: ""}")
            }
        } else {
            Notifier.show(ctx, a, "حان موعد التنبيه")
        }
    }
}

/** يعيد جدولة كل المواعيد بعد إعادة تشغيل الجهاز أو تحديث التطبيق. */
class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}
