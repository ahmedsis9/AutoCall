package com.example.autocall

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** نافذة إضافة / تعديل موعد اتصال (تبقى داخل التطبيق وتُحفظ فيه). */
object AlarmEditor {

    private fun radio(c: Context, label: String): RadioButton =
        RadioButton(c).apply {
            text = label
            id = View.generateViewId()
            textSize = 16f
            setTextColor(Ui.TEXT)
            buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
        }

    private fun startOfDay(ms: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun show(act: MainActivity, existing: AlarmItem?, onSaved: () -> Unit) {
        val c: Context = act

        var hour = existing?.hour ?: 8
        var minute = existing?.minute ?: 0
        var repeat = existing?.repeat ?: AlarmItem.DAILY
        val days: MutableSet<Int> = (existing?.days ?: emptySet()).toMutableSet()
        var dateMs: Long = existing?.date ?: 0L

        fun ensureDate() {
            if (dateMs <= 0L) {
                val probe = AlarmItem(0, "", "", hour, minute, AlarmItem.DAILY, emptySet(), false)
                dateMs = startOfDay(AlarmScheduler.nextTrigger(probe) ?: System.currentTimeMillis())
            }
        }
        if (repeat == AlarmItem.ONCE) ensureDate()

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 20)
            setPadding(p, p, p, p)
        }

        root.addView(
            Ui.text(c, if (existing == null) "اتصال تلقائي جديد" else "تعديل الموعد", 20f, Ui.TEXT, true),
            Ui.lp(c, bottom = 14)
        )

        // ----- الاسم -----
        val nameEt = Ui.input(c, "الاسم (مثلاً: ماما)").apply { setText(existing?.name ?: "") }
        root.addView(nameEt, Ui.lp(c, bottom = 14))

        // ----- الرقم -----
        root.addView(Ui.text(c, "الرقم أو الكود", 14f, Ui.MUTED, true), Ui.lp(c, bottom = 4))
        val numberEt = Ui.input(c, "07xx xxx xxxx  /  +964...  /  *123#").apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.number ?: "")
        }
        root.addView(numberEt, Ui.lp(c, bottom = 6))

        val preview = Ui.text(c, "", 12f, Ui.ACCENT)
        root.addView(preview, Ui.lp(c, bottom = 6))
        fun refreshPreview() {
            val raw = numberEt.text.toString()
            val n = PhoneUtil.clean(raw)
            preview.text = when {
                raw.isBlank() -> "الرموز المدعومة: الأرقام + * # , ;  (أي رمز آخر يُحذف تلقائياً)"
                !PhoneUtil.isDialable(n) -> "⚠️ لا يوجد رقم صالح"
                n != raw.trim() -> "سيتم طلب: $n"
                else -> "✓ سيتم طلب: $n"
            }
        }
        refreshPreview()
        numberEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, aft: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bef: Int, cnt: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshPreview()
        })

        val pick = Ui.button(c, "👤 اختيار من جهات الاتصال", filled = false, color = Ui.ACCENT)
        pick.textSize = 14f
        pick.setOnClickListener {
            act.pickContact { number, name ->
                numberEt.setText(number)
                if (nameEt.text.toString().isBlank() && name.isNotBlank()) nameEt.setText(name)
            }
        }
        root.addView(pick, Ui.lp(c, bottom = 14))

        // ----- الوقت -----
        root.addView(Ui.text(c, "الوقت", 14f, Ui.MUTED, true), Ui.lp(c, bottom = 4))
        val timeBtn = Ui.button(c, formatTime(hour, minute), filled = false, color = Ui.ACCENT)
        timeBtn.textSize = 22f
        timeBtn.setOnClickListener {
            TimePickerDialog(
                act, android.R.style.Theme_Material_Dialog_Alert,
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
        val rbOnce = radio(c, "مرة واحدة")
        val rbDaily = radio(c, "يومياً")
        val rbCustom = radio(c, "أيام محددة")
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

        // ----- التاريخ (لمرة واحدة) -----
        val dateBtn = Ui.button(
            c,
            if (dateMs > 0L) "📅 " + formatDateLong(dateMs) else "📅 اختر التاريخ",
            filled = false, color = Ui.VIOLET
        )
        dateBtn.textSize = 15f
        dateBtn.visibility = if (repeat == AlarmItem.ONCE) View.VISIBLE else View.GONE
        dateBtn.setOnClickListener {
            ensureDate()
            val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
            val dlg = DatePickerDialog(
                act, android.R.style.Theme_Material_Dialog_Alert,
                DatePickerDialog.OnDateSetListener { _, y, mo, d ->
                    dateMs = Calendar.getInstance().apply {
                        set(y, mo, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    dateBtn.text = "📅 " + formatDateLong(dateMs)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            )
            dlg.datePicker.minDate = startOfDay(System.currentTimeMillis())
            dlg.show()
        }
        root.addView(dateBtn, Ui.lp(c, bottom = 8))

        // ----- أيام التكرار -----
        val daysRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (repeat == AlarmItem.CUSTOM) View.VISIBLE else View.GONE
        }
        fun paint(chip: TextView, selected: Boolean) {
            if (selected) {
                chip.background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 10)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.background = Ui.shape(c, Ui.INPUT_BG, 10, Ui.STROKE)
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
            dateBtn.visibility = if (repeat == AlarmItem.ONCE) View.VISIBLE else View.GONE
            if (repeat == AlarmItem.ONCE) {
                ensureDate()
                dateBtn.text = "📅 " + formatDateLong(dateMs)
            }
        }

        val scroll = ScrollView(c).apply { addView(root) }
        Ui.rtl(scroll)

        val dlg = Ui.showDialog(
            act,
            Ui.builder(act).setView(scroll)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
        )

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = PhoneUtil.clean(numberEt.text.toString())
            if (!PhoneUtil.isDialable(number)) {
                Toast.makeText(c, "أدخل رقماً صالحاً أو اختره من جهات الاتصال", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (repeat == AlarmItem.CUSTOM && days.isEmpty()) {
                Toast.makeText(c, "اختر يوماً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = nameEt.text.toString().trim().ifEmpty { "اتصال تلقائي" }

            val item = AlarmItem(
                id = existing?.id ?: AlarmStore.nextId(c),
                name = name,
                number = number,
                hour = hour,
                minute = minute,
                repeat = repeat,
                days = if (repeat == AlarmItem.CUSTOM) days.toSet() else emptySet(),
                autoCall = true,
                status = AlarmItem.SCHEDULED,
                date = if (repeat == AlarmItem.ONCE) dateMs else 0L,
                owner = existing?.owner?.ifEmpty { Auth.currentKey(c) } ?: Auth.currentKey(c),
                lastRun = existing?.lastRun ?: 0L,
                lastResult = existing?.lastResult ?: "",
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )

            val t = AlarmScheduler.nextTrigger(item)
            if (t == null) {
                Toast.makeText(c, "اختر وقتاً في المستقبل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlarmStore.upsert(c, item)
            AlarmScheduler.schedule(c, item)

            if (!CallHelper.hasPermission(c)) {
                act.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 100)
                Toast.makeText(c, "امنح إذن الاتصال لتعمل المكالمات في موعدها", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(c, "تمت الجدولة • الاتصال القادم: " + formatStamp(t), Toast.LENGTH_LONG).show()
            }

            dlg.dismiss()
            onSaved()
        }
    }
}
