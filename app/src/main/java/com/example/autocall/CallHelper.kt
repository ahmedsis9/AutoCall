package com.example.autocall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.WindowManager
import android.widget.Toast

/** تنظيف الأرقام: يقبل أي أرقام (حتى العربية) ويُبقي فقط ما تستطيع الشبكة طلبه. */
object PhoneUtil {
    /**
     * يُبقي: الأرقام (تُحوَّل للّاتينية) + * # وفاصلتَي التوقّف , ;
     * ويحذف أي رمز آخر (مسافات - ( ) / : ' " ! ? = _ & $ @ ٪ ...) لأن الشبكة لا تستطيع طلبه.
     */
    fun clean(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            val d = Character.digit(ch, 10)
            when {
                d >= 0 -> sb.append(('0'.code + d).toChar())
                ch == '+' || ch == '*' || ch == '#' || ch == ',' || ch == ';' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isDialable(clean: String): Boolean = clean.any { it in '0'..'9' }

    /** أكواد USSD/الخدمة (تحوي * أو #) لا تُنشئ مكالمة عادية. */
    fun isCode(clean: String): Boolean = clean.contains('*') || clean.contains('#')
}

object CallHelper {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** يضع المكالمة فعلياً. يعيد null عند النجاح أو رسالة الخطأ. */
    fun place(ctx: Context, number: String): String? {
        if (!hasPermission(ctx)) return "إذن الاتصال غير ممنوح"
        val n = PhoneUtil.clean(number)
        if (!PhoneUtil.isDialable(n)) return "الرقم غير صالح"
        // fromParts يشفّر # و * و + بشكل صحيح داخل tel:
        val uri = Uri.fromParts("tel", n, null)
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(uri, Bundle())
            return null
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            } catch (e2: Exception) {
                return e2.message ?: e.message ?: "خطأ غير معروف"
            }
        }
    }

    /** هل توجد مكالمة جارية/قيد الطلب؟ (إن لم نملك إذن القراءة نفترض النجاح). */
    fun inCall(ctx: Context): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return true
        return try {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).isInCall
        } catch (e: Exception) {
            true
        }
    }
}

/**
 * شاشة شفافة تظهر فوق شاشة القفل وتضع المكالمة من الواجهة الأمامية (الأكثر موثوقية).
 * تُفتح مباشرة عند الموعد إن مُنح إذن «العرض فوق التطبيقات»، أو عبر إشعار بملء الشاشة.
 */
class CallActivity : Activity() {
    private var item: AlarmItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val a = AlarmStore.get(this, intent.getIntExtra("id", -1))
        if (a == null || a.number.isBlank()) {
            finish()
            return
        }
        item = a
        if (!CallHelper.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 200)
            return
        }
        go(a)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val a = item
        if (a != null && CallHelper.hasPermission(this)) go(a) else finish()
    }

    private fun go(a: AlarmItem) {
        val n = PhoneUtil.clean(a.number)
        val err = CallHelper.place(this, n)
        if (err != null) {
            Toast.makeText(this, "تعذّر الاتصال: $err", Toast.LENGTH_LONG).show()
            AlarmStore.setResult(this, a.id, "تعذّر الاتصال: $err")
        } else {
            AlarmStore.setResult(this, a.id, "تم الاتصال بـ $n")
        }
        getSystemService(android.app.NotificationManager::class.java).cancel(a.id)
        finish()
    }
}
