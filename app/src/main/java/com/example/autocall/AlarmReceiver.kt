package com.example.autocall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** يعمل عند حلول موعد الاتصال، حتى والشاشة مقفلة. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val app = ctx.applicationContext
        val pending = goAsync()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hamko:alarm")
        wl.acquire(40_000L)
        Thread {
            try {
                handle(app, id)
            } catch (e: Exception) {
                AlarmStore.setResult(app, id, "تعذّر الاتصال: ${e.message ?: "خطأ"}")
            } finally {
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }.start()
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

        if (!a.autoCall) {
            AlarmStore.upsert(ctx, cur.copy(lastResult = "تم التنبيه"))
            Notifier.show(ctx, a, "حان موعد التنبيه")
            return
        }

        val n = PhoneUtil.clean(a.number)
        // نحفظ الحالة قبل الاتصال حتى لا تُستبدل نتيجة الشاشة الأمامية
        AlarmStore.upsert(ctx, cur.copy(lastResult = "جارٍ الاتصال بـ $n"))
        val result = dial(ctx, a, n)
        if (result != null) AlarmStore.setResult(ctx, a.id, result)
    }

    private fun dialCode(ctx: Context, n: String): String {
        val latch = CountDownLatch(1)
        var ok = false
        var text = ""
        CallHelper.sendCode(ctx, n) { o, t ->
            ok = o
            text = t
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        if (latch.count > 0L) return "تم إرسال الكود $n (بانتظار ردّ الشبكة)"
        if (ok) return "تم تنفيذ الكود $n" + (if (text.isNotBlank()) ": $text" else "")
        // الهاتف رفض واجهة الأكواد: نجرّب الاتصال العادي
        val err = CallHelper.place(ctx, n)
        return if (err == null) "تم إرسال الكود $n عبر الاتصال (قد يتطلب تأكيداً في تطبيق الاتصال)"
        else "تعذّر تنفيذ الكود: $text / $err"
    }

    /** يعيد نص النتيجة، أو null إن تولّت CallActivity كتابة النتيجة بنفسها. */
    private fun dial(ctx: Context, a: AlarmItem, n: String): String? {
        if (!CallHelper.hasPermission(ctx)) {
            Notifier.showCallFallback(ctx, a, "إذن الاتصال غير ممنوح")
            return "تعذّر الاتصال: إذن الاتصال غير ممنوح"
        }

        // الأكواد (* و #) تُرسل عبر واجهة الأكواد ويُقرأ ردّ الشبكة
        if (PhoneUtil.isCode(n)) return dialCode(ctx, n)

        // 1) الأوثق: فتح شاشة الاتصال مباشرة (تعمل فوق القفل) إن مُنح العرض فوق التطبيقات
        if (CallHelper.canOverlay(ctx)) {
            try {
                ctx.startActivity(
                    Intent(ctx, CallActivity::class.java)
                        .putExtra("id", a.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                return null
            } catch (e: Exception) {
                // ننتقل للطريقة التالية
            }
        }

        // 2) الاتصال المباشر من الخلفية ثم التحقق أنه بدأ فعلاً
        val err = CallHelper.place(ctx, n)
        if (err != null) {
            Notifier.showCallFallback(ctx, a, err)
            return "تعذّر الاتصال: $err"
        }
        if (PhoneUtil.isCode(n)) return "تم إرسال الكود $n"
        try {
            Thread.sleep(4000)
        } catch (e: InterruptedException) {
            // لا شيء
        }
        if (!CallHelper.inCall(ctx)) {
            Notifier.showCallFallback(ctx, a, "لم تبدأ المكالمة")
            return "تعذّر الاتصال تلقائياً، اضغط الإشعار للاتصال (فعّل «العرض فوق التطبيقات»)"
        }
        return "تم الاتصال بـ $n"
    }
}

/** يعيد جدولة كل المواعيد بعد إعادة تشغيل الجهاز أو تحديث التطبيق. */
class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmStore.reconcile(ctx)
        AlarmScheduler.rescheduleAll(ctx)
    }
}
