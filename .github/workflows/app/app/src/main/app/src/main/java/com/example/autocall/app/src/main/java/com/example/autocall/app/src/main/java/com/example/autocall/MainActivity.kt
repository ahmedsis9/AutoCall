package com.example.autocall

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = EditText(this).apply {
            hint = "رقم الهاتف"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(CallScheduler.number(this@MainActivity))
        }
        val picker = TimePicker(this).apply { setIs24HourView(true) }
        val save = Button(this).apply { text = "تفعيل الاتصال اليومي" }
        val stop = Button(this).apply { text = "إيقاف" }

        save.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS), 1)

            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                Toast.makeText(this, "فعّل «المنبهات والتذكيرات» ثم ارجع واضغط تفعيل", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            CallScheduler.save(this, number.text.toString().trim(), picker.hour, picker.minute)
            CallScheduler.scheduleNext(this)
            Toast.makeText(this, "تم الجدولة يومياً الساعة %02d:%02d".format(picker.hour, picker.minute), Toast.LENGTH_LONG).show()
        }
        stop.setOnClickListener {
            CallScheduler.cancel(this)
            Toast.makeText(this, "تم الإيقاف", Toast.LENGTH_SHORT).show()
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 96, 48, 48)
            addView(number); addView(picker); addView(save); addView(stop)
        })
    }
}
