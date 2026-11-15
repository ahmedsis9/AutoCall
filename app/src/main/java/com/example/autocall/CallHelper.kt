package com.example.autocall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.WindowManager

object CallHelper {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** يضع المكالمة فعلياً. يعيد null عند النجاح أو رسالة الخطأ. */
    fun place(ctx: Context, number: String): String? {
        if (!hasPermission(ctx)) return "إذن الاتصال غير ممنوح"
        val n = number.trim()
        if (n.isEmpty()) return "رقم الهاتف فارغ"
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
}

/**
 * شاشة شفافة تظهر فوق شاشة القفل وتضع المكالمة من الواجهة الأمامية؛
 * تُستخدم كخطة بديلة إن منع النظام الاتصال من الخلفية.
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
        val err = CallHelper.place(this, a.number)
        if (err != null) {
            android.widget.Toast.makeText(this, "تعذّر الاتصال: $err", android.widget.Toast.LENGTH_LONG).show()
        }
        getSystemService(android.app.NotificationManager::class.java).cancel(a.id)
        finish()
    }
}
