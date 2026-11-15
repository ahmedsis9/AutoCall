package com.example.autocall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private var list: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Auth.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        Ui.setupWindow(this)
        buildUi()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    override fun onResume() {
        super.onResume()
        if (list != null) render()
    }

    private fun buildUi() {
        val c: Context = this

        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ----- الترويسة -----
        val r = Ui.dp(c, 26).toFloat()
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.NAVY)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            val p = Ui.dp(c, 20)
            setPadding(p, Ui.dp(c, 18), p, Ui.dp(c, 24))
            elevation = Ui.dp(c, 4).toFloat()
        }
        val titles = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(
            Ui.text(c, Ui.APP_NAME, 34f, Color.WHITE, true).apply {
                typeface = Ui.fancyFont()
                setShadowLayer(16f, 0f, 0f, Ui.PRIMARY)
            }
        )
        titles.addView(Ui.text(c, "مرحباً، ${Auth.userName(c)}", 14f, Ui.ACCENT), Ui.lp(c, top = 2))
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val logout = Ui.text(c, "خروج", 14f, Color.WHITE, true).apply {
            background = Ui.shape(c, Color.parseColor("#26FFFFFF"), 12)
            val h = Ui.dp(c, 16)
            val v = Ui.dp(c, 8)
            setPadding(h, v, h, v)
            isClickable = true
            setOnClickListener {
                Auth.setLoggedIn(c, false)
                startActivity(Intent(c, LoginActivity::class.java))
                finish()
            }
        }
        header.addView(logout)
        col.addView(header, Ui.lp(c))

        // ----- القائمة -----
        val listLayout = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 16)
            setPadding(p, Ui.dp(c, 20), p, Ui.dp(c, 110))
        }
        list = listLayout
        val scroll = ScrollView(c).apply {
            clipToPadding = false
            addView(listLayout)
        }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ----- زر + -----
        val fabSize = Ui.dp(c, 64)
        val fab = TextView(c).apply {
            text = "+"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = Ui.shape(c, Ui.PRIMARY, 32)
            elevation = Ui.dp(c, 8).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, null) { render() } }
        }
        val fabLp = FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = Ui.dp(c, 22)
            bottomMargin = Ui.dp(c, 24)
        }
        root.addView(fab, fabLp)

        setContentView(root)
        Ui.rtl(root)
        render()
    }

    private fun render() {
        val container = list ?: return
        val c: Context = this
        container.removeAllViews()

        val items = AlarmStore.all(c)
            .sortedWith(compareBy<AlarmItem>({ !it.enabled }, { it.hour }, { it.minute }))

        if (items.isEmpty()) {
            val empty = Ui.text(c, "لا توجد مواعيد بعد\nاضغط + لإضافة أول موعد", 16f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.3f)
                setPadding(0, Ui.dp(c, 60), 0, Ui.dp(c, 40))
            }
            container.addView(empty, Ui.lp(c))
        } else {
            items.forEach { container.addView(card(it)) }
        }
        container.addView(tip())
    }

    private fun card(a: AlarmItem): View {
        val c: Context = this

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.shape(c, Color.WHITE, 18)
            val p = Ui.dp(c, 16)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 2).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, a) { render() } }
        }
        card.layoutParams = Ui.lp(c, bottom = 12)

        // السطر العلوي: الوقت + مفتاح التفعيل
        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            Ui.text(c, formatTime(a.hour, a.minute), 30f, if (a.enabled) Ui.PRIMARY else Ui.MUTED, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val sw = Switch(c).apply { isChecked = a.enabled }
        sw.setOnCheckedChangeListener { _, on ->
            val n = a.copy(enabled = on)
            AlarmStore.upsert(c, n)
            AlarmScheduler.schedule(c, n)
            render()
        }
        top.addView(sw)
        card.addView(top)

        // الاسم
        card.addView(Ui.text(c, a.name, 17f, Ui.TEXT, true), Ui.lp(c, top = 4))

        // النوع + الرقم
        val info = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = Ui.text(
            c,
            if (a.autoCall) "📞 اتصال تلقائي" else "🔔 تنبيه فقط",
            13f,
            if (a.autoCall) Ui.PRIMARY else Ui.ACCENT_DARK,
            true
        ).apply {
            background = Ui.shape(c, if (a.autoCall) Ui.BADGE_CALL else Ui.BADGE_ALERT, 10)
            val h = Ui.dp(c, 10)
            val v = Ui.dp(c, 4)
            setPadding(h, v, h, v)
        }
        info.addView(badge)
        if (a.autoCall) {
            info.addView(Ui.text(c, a.number, 14f, Ui.MUTED), Ui.lp(c, w = LinearLayout.LayoutParams.WRAP_CONTENT, start = 10))
        }
        card.addView(info, Ui.lp(c, top = 8))

        // التكرار
        card.addView(Ui.text(c, "🔁 " + repeatSummary(a), 13f, Ui.MUTED), Ui.lp(c, top = 8))

        // أزرار
        val actions = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val edit = Ui.text(c, "تعديل", 14f, Ui.PRIMARY, true).apply {
            isClickable = true
            val h = Ui.dp(c, 12)
            val v = Ui.dp(c, 8)
            setPadding(h, v, h, v)
            setOnClickListener { AlarmEditor.show(this@MainActivity, a) { render() } }
        }
        val del = Ui.text(c, "حذف", 14f, Ui.DANGER, true).apply {
            isClickable = true
            val h = Ui.dp(c, 12)
            val v = Ui.dp(c, 8)
            setPadding(h, v, h, v)
            setOnClickListener { confirmDelete(a) }
        }
        actions.addView(edit)
        actions.addView(del)
        card.addView(actions, Ui.lp(c, top = 4))
        return card
    }

    private fun confirmDelete(a: AlarmItem) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("حذف الموعد")
            .setMessage("هل تريد حذف «${a.name}»؟")
            .setPositiveButton("حذف") { _, _ ->
                AlarmScheduler.cancel(this, a.id)
                AlarmStore.delete(this, a.id)
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun tip(): View {
        val c: Context = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.shape(c, Ui.BADGE_CALL, 14)
            val p = Ui.dp(c, 14)
            setPadding(p, p, p, p)
        }
        box.layoutParams = Ui.lp(c, top = 8)
        box.addView(
            Ui.text(
                c,
                "💡 لتعمل المواعيد والجهاز مقفل، استثنِ Hamko من «توفير البطارية» وامنحه إذن الاتصال.",
                13f, Ui.TEXT
            )
        )
        val open = Ui.text(c, "فتح إعدادات البطارية", 13f, Ui.PRIMARY, true).apply {
            isClickable = true
            setPadding(0, Ui.dp(c, 8), 0, 0)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        box.addView(open)
        return box
    }
}
