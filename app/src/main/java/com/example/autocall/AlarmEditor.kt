package com.example.autocall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** نافذة إضافة / تعديل موعد. */
object AlarmEditor {

    fun show(act: Activity, existing: AlarmItem?, onSaved: () -> Unit) {
        val c: Context = act

        var hour = existing?.hour ?: 8
        var minute = existing?.minute ?: 0
        var repeat = existing?.repeat ?: AlarmItem.DAILY
        var autoCall = existing?.autoCall ?: true
        val days: MutableSet<Int> = (existing?.days ?: emptySet()).toMutableSet()

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 20)
            setPadding(p, p, p, p)
        }

        root.addView(
            Ui.text(c, if (existing == null) "موعد جديد" else "تعديل الموعد", 20f, Ui.TEXT, true),
            Ui.lp(c, bottom = 14)
        )

        // ----- الاسم -----
        val nameEt = Ui.input(c, "اسم التنبيه أو الاتصال").apply { setText(existing?.name ?: "") }
        root.addView(nameEt, Ui.lp(c, bottom = 14))

        // ----- النوع: اتصال تلقائي / تنبيه فقط -----
        root.addView(Ui.text(c, "نوع الموعد", 14f, Ui.MUTED, true), Ui.lp(c, bottom = 4))
        val modeGroup = RadioGroup(c).apply { orientation = RadioGroup.VERTICAL }
        val rbCall = RadioButton(c).apply {
            text = "📞 اتصال تلقائي"
            id = View.generateViewId()
            textSize = 16f
        }
        val rbAlert = RadioButton(c).apply {
            text = "🔔 تنبيه فقط"
            id = View.generateViewId()
            textSize = 16f
        }
        modeGroup.addView(rbCall)
        modeGroup.addView(rbAlert)
        modeGroup.check(if (autoCall) rbCall.id else rbAlert.id)
        root.addView(modeGroup, Ui.lp(c, bottom = 8))

        // ----- الرقم -----
        val numberEt = Ui.input(c, "رقم الهاتف", InputType.TYPE_CLASS_PHONE).apply {
            setText(existing?.number ?: "")
        }
        root.addView(numberEt, Ui.lp(c, bottom = 14))
        numberEt.visibility = if (autoCall) View.VISIBLE else View.GONE

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            autoCall = checkedId == rbCall.id
            numberEt.visibility = if (autoCall) View.VISIBLE else View.GONE
        }

        // ----- الوقت (صباحاً / مساءً) -----
        root.addView(Ui.text(c, "الوقت", 14f, Ui.MUTED, true), Ui.lp(c, bottom = 4))
        val timeBtn = Ui.button(c, formatTime(hour, minute), filled = false)
        timeBtn.textSize = 22f
        timeBtn.setOnClickListener {
            TimePickerDialog(
                act,
                TimePickerDialog.OnTimeSetListener { _, h, m ->
                    hour = h
                    minute = m
                    timeBtn.text = formatTime(h, m)
                },
                hour, minute, false
            ).show()
        }
        root.addView(timeBtn, Ui.lp(c, bottom = 14))

        // ----- التكرار -----
        root.addView(Ui.text(c, "التكرار", 14f, Ui.MUTED, true), Ui.lp(c, bottom = 4))
        val repeatGroup = RadioGroup(c).apply { orientation = RadioGroup.VERTICAL }
        val rbOnce = RadioButton(c).apply { text = "مرة واحدة"; id = View.generateViewId(); textSize = 16f }
        val rbDaily = RadioButton(c).apply { text = "يومياً"; id = View.generateViewId(); textSize = 16f }
        val rbCustom = RadioButton(c).apply { text = "أيام محددة"; id = View.generateViewId(); textSize = 16f }
        repeatGroup.addView(rbOnce)
        repeatGroup.addView(rbDaily)
        repeatGroup.addView(rbCustom)
        repeatGroup.check(
            when (repeat) {
                AlarmItem.ONCE -> rbOnce.id
                AlarmItem.CUSTOM -> rbCustom.id
                else -> rbDaily.id
            }
        )
        root.addView(repeatGroup, Ui.lp(c, bottom = 6))

        // ----- أيام التكرار -----
        val daysRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (repeat == AlarmItem.CUSTOM) View.VISIBLE else View.GONE
        }
        fun paint(chip: TextView, selected: Boolean) {
            if (selected) {
                chip.background = Ui.shape(c, Ui.PRIMARY, 10)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.background = Ui.shape(c, Color.WHITE, 10, Ui.STROKE)
                chip.setTextColor(Ui.TEXT)
            }
        }
        DAY_ORDER.forEach { d ->
            val chip = TextView(c).apply {
                text = dayShort(d)
                textSize = 12f
                gravity = Gravity.CENTER
                isClickable = true
            }
            paint(chip, days.contains(d))
            chip.setOnClickListener {
                if (days.contains(d)) days.remove(d) else days.add(d)
                paint(chip, days.contains(d))
            }
            val lp = LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f).apply {
                marginStart = Ui.dp(c, 2)
                marginEnd = Ui.dp(c, 2)
            }
            daysRow.addView(chip, lp)
        }
        root.addView(daysRow, Ui.lp(c, bottom = 6))

        repeatGroup.setOnCheckedChangeListener { _, checkedId ->
            repeat = when (checkedId) {
                rbOnce.id -> AlarmItem.ONCE
                rbCustom.id -> AlarmItem.CUSTOM
                else -> AlarmItem.DAILY
            }
            daysRow.visibility = if (repeat == AlarmItem.CUSTOM) View.VISIBLE else View.GONE
        }

        val scroll = ScrollView(c).apply { addView(root) }
        Ui.rtl(scroll)

        val dlg = AlertDialog.Builder(act, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setView(scroll)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dlg.show()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = numberEt.text.toString().trim()
            if (autoCall && number.isEmpty()) {
                Toast.makeText(c, "أدخل رقم الهاتف", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (repeat == AlarmItem.CUSTOM && days.isEmpty()) {
                Toast.makeText(c, "اختر يوماً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = nameEt.text.toString().trim().ifEmpty { if (autoCall) "اتصال تلقائي" else "تنبيه" }

            val item = AlarmItem(
                id = existing?.id ?: AlarmStore.nextId(c),
                name = name,
                number = number,
                hour = hour,
                minute = minute,
                repeat = repeat,
                days = if (repeat == AlarmItem.CUSTOM) days.toSet() else emptySet(),
                autoCall = autoCall,
                enabled = true
            )
            AlarmStore.upsert(c, item)
            AlarmScheduler.schedule(c, item)

            if (autoCall && act.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                act.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 100)
            }

            val t = AlarmScheduler.nextTrigger(item)
            val msg = if (t != null) {
                "تم الحفظ، الموعد القادم: " +
                    SimpleDateFormat("EEEE d/M h:mm a", Locale("ar")).format(Date(t))
            } else "تم الحفظ"
            Toast.makeText(c, msg, Toast.LENGTH_LONG).show()

            dlg.dismiss()
            onSaved()
        }
    }
}
