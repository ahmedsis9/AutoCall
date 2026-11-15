package com.example.autocall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private class Issue(val message: String, val fixLabel: String, val fix: () -> Unit)

    private var listBox: LinearLayout? = null
    private var tabsRow: LinearLayout? = null
    private var healthBox: LinearLayout? = null
    private var heroLabel: TextView? = null
    private var heroCount: TextView? = null
    private var heroSub: TextView? = null
    private var greetingTv: TextView? = null

    private var filter = -1   // -1 = الكل، وإلا حالة محددة
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateHero()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Auth.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        Ui.setupWindow(this)
        Notifier.ensureChannel(this)
        AlarmStore.claimOrphans(this, Auth.currentKey(this))
        buildUi()

        // اطلب الأذونات من البداية كي تكون جاهزة قبل أول موعد
        val need = ArrayList<String>()
        if (!CallHelper.hasPermission(this)) need.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 101)
    }

    override fun onResume() {
        super.onResume()
        if (listBox == null) return
        AlarmStore.reconcile(this)
        AlarmScheduler.rescheduleAll(this)
        render()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (listBox != null) render()
    }

    // ------------------------------------------------------------------ بناء الواجهة

    private fun buildUi() {
        val c: Context = this

        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(
            CinematicBackground(c).apply { intensity = 0.55f },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            col,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // ----- الترويسة -----
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(c, 20)
            setPadding(p, Ui.dp(c, 16), p, Ui.dp(c, 6))
        }
        val titles = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(
            Ui.text(c, Ui.APP_NAME, 34f, Color.WHITE, true).apply {
                typeface = Ui.fancyFont()
                setShadowLayer(18f, 0f, 0f, Ui.PRIMARY)
            }
        )
        val greet = Ui.text(c, "", 14f, Ui.ACCENT)
        greetingTv = greet
        titles.addView(greet, Ui.lp(c, top = 2))
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val initial = Auth.userName(c).trim().take(1).uppercase(Locale.ROOT).ifEmpty { "؟" }
        val avatar = Ui.text(c, initial, 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 22)
            elevation = Ui.dp(c, 6).toFloat()
            isClickable = true
            setOnClickListener { accountDialog() }
        }
        header.addView(avatar, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        col.addView(header, Ui.lp(c))

        // ----- محتوى قابل للتمرير -----
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 16)
            setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 120))
        }
        val scroll = ScrollView(c).apply {
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // بطاقة «المكالمة القادمة» الذكية
        val hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.glass(c, 26, Ui.alpha(Ui.ACCENT, 140))
            val p = Ui.dp(c, 18)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 14).toFloat()
        }
        Ui.attachTilt(hero)
        val hl = Ui.text(c, "", 13f, Ui.MUTED, true).apply { gravity = Gravity.CENTER }
        val hc = Ui.text(c, "--:--:--", 40f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setShadowLayer(20f, 0f, 0f, Ui.ACCENT)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val hs = Ui.text(c, "", 14f, Ui.TEXT).apply { gravity = Gravity.CENTER }
        heroLabel = hl
        heroCount = hc
        heroSub = hs
        hero.addView(hl, Ui.lp(c))
        hero.addView(hc, Ui.lp(c, top = 2))
        hero.addView(hs, Ui.lp(c, top = 4))
        content.addView(hero, Ui.lp(c, bottom = 14))

        // تبويبات الحالات
        val tabs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        tabsRow = tabs
        content.addView(tabs, Ui.lp(c, bottom = 12))

        // لوحة فحص النظام
        val health = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        healthBox = health
        content.addView(health, Ui.lp(c, bottom = 12))

        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        listBox = list
        content.addView(list, Ui.lp(c))

        // ----- زر + -----
        val fabSize = Ui.dp(c, 66)
        val fab = TextView(c).apply {
            text = "+"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 33)
            elevation = Ui.dp(c, 12).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, null) { render() } }
        }
        Ui.attachTilt(fab)
        root.addView(
            fab,
            FrameLayout.LayoutParams(fabSize, fabSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = Ui.dp(c, 22)
                bottomMargin = Ui.dp(c, 26)
            }
        )

        setContentView(root)
        Ui.rtl(root)
        render()
    }

    // ------------------------------------------------------------------ العرض

    private fun render() {
        if (listBox == null) return
        updateGreeting()
        renderTabs()
        renderHealth()
        renderList()
        updateHero()
    }

    private fun updateGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val g = when {
            h in 5..11 -> "صباح الخير"
            h in 12..16 -> "نهارك سعيد"
            h in 17..21 -> "مساء الخير"
            else -> "ليلة هادئة"
        }
        greetingTv?.text = "$g، ${Auth.userName(this)}"
    }

    private fun nextUpcoming(): Pair<AlarmItem, Long>? =
        AlarmStore.mine(this)
            .filter { it.enabled }
            .mapNotNull { a -> AlarmScheduler.nextTrigger(a)?.let { t -> Pair(a, t) } }
            .minByOrNull { it.second }

    private fun updateHero() {
        val n = nextUpcoming()
        if (n == null) {
            heroLabel?.text = "المكالمة القادمة"
            heroCount?.text = "--:--:--"
            heroSub?.text = "لا توجد مواعيد مجدولة • اضغط + لإضافة موعد"
            return
        }
        val (a, t) = n
        var s = ((t - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        val days = s / 86400L
        s %= 86400L
        val clock = String.format(Locale.US, "%02d:%02d:%02d", s / 3600L, (s % 3600L) / 60L, s % 60L)
        heroLabel?.text = if (a.autoCall) "📞 المكالمة القادمة بعد" else "🔔 التنبيه القادم بعد"
        heroCount?.text = if (days > 0L) "$days ي  $clock" else clock
        heroSub?.text = a.name + " • " + formatStamp(t)
    }

    private fun renderTabs() {
        val row = tabsRow ?: return
        val c: Context = this
        row.removeAllViews()
        val items = AlarmStore.mine(c)
        val defs = listOf(
            Triple(-1, "الكل", items.size),
            Triple(AlarmItem.SCHEDULED, "مجدولة", items.count { it.status == AlarmItem.SCHEDULED }),
            Triple(AlarmItem.COMPLETED, "مكتملة", items.count { it.status == AlarmItem.COMPLETED }),
            Triple(AlarmItem.CANCELED, "ملغية", items.count { it.status == AlarmItem.CANCELED })
        )
        defs.forEachIndexed { i, (st, label, count) ->
            val sel = filter == st
            val col = if (st == -1) Ui.PRIMARY else Ui.statusColor(st)
            val tab = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = if (sel) Ui.gradient(c, Ui.alpha(col, 230), Ui.alpha(Ui.VIOLET, 200), 16)
                else Ui.glass(c, 16, Ui.alpha(col, 90))
                val v = Ui.dp(c, 8)
                setPadding(v, v, v, v)
                elevation = Ui.dp(c, if (sel) 8 else 2).toFloat()
                isClickable = true
                setOnClickListener {
                    filter = st
                    render()
                }
            }
            tab.addView(Ui.text(c, count.toString(), 20f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            tab.addView(Ui.text(c, label, 12f, if (sel) Color.WHITE else Ui.MUTED, true).apply { gravity = Gravity.CENTER })
            Ui.attachTilt(tab)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = Ui.dp(c, 8)
            }
            row.addView(tab, lp)
        }
    }

    private fun renderHealth() {
        val box = healthBox ?: return
        val c: Context = this
        box.removeAllViews()

        val issues = ArrayList<Issue>()
        if (!CallHelper.hasPermission(c)) {
            issues.add(Issue("إذن الاتصال غير ممنوح، لن تتم المكالمات التلقائية.", "منح الإذن") {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 102)
            })
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            issues.add(Issue("الإشعارات متوقفة، لن تظهر لك التنبيهات.", "السماح") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 103)
            })
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            issues.add(Issue("لضمان العمل والجهاز مقفل، اجعل البطارية لـ Hamko «بدون قيود».", "الإعدادات") {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            })
        }

        if (issues.isEmpty()) {
            box.addView(
                Ui.text(c, "✅ النظام جاهز • ستُنفَّذ المكالمات في موعدها حتى والشاشة مقفلة", 12f, Ui.SUCCESS, true)
                    .apply { gravity = Gravity.CENTER },
                Ui.lp(c)
            )
            return
        }

        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.glass(c, 18, Ui.alpha(Ui.WARN, 150))
            val p = Ui.dp(c, 14)
            setPadding(p, p, p, p)
        }
        wrap.addView(Ui.text(c, "⚠️ فحص ذكي للنظام", 14f, Ui.WARN, true), Ui.lp(c, bottom = 6))
        issues.forEach { issue ->
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                Ui.text(c, issue.message, 13f, Ui.TEXT),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            val fixBtn = Ui.text(c, issue.fixLabel, 13f, Ui.BG, true).apply {
                background = Ui.shape(c, Ui.WARN, 12)
                val h = Ui.dp(c, 12)
                val v = Ui.dp(c, 7)
                setPadding(h, v, h, v)
                isClickable = true
                setOnClickListener { issue.fix() }
            }
            row.addView(fixBtn, Ui.lp(c, w = LinearLayout.LayoutParams.WRAP_CONTENT, start = 8))
            wrap.addView(row, Ui.lp(c, top = 4))
        }
        box.addView(wrap, Ui.lp(c))
    }

    private fun renderList() {
        val container = listBox ?: return
        val c: Context = this
        container.removeAllViews()

        val all = AlarmStore.mine(c)
        val shown = all.filter { filter == -1 || it.status == filter }
            .sortedWith(
                compareBy<AlarmItem>(
                    { it.status },
                    { AlarmScheduler.nextTrigger(it) ?: Long.MAX_VALUE },
                    { -it.lastRun }
                )
            )

        if (shown.isEmpty()) {
            val msg = if (all.isEmpty()) "لا توجد مواعيد بعد\nاضغط + لإضافة أول موعد"
            else "لا توجد مواعيد في هذا التبويب"
            container.addView(
                Ui.text(c, msg, 16f, Ui.MUTED).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(0f, 1.3f)
                    setPadding(0, Ui.dp(c, 50), 0, Ui.dp(c, 40))
                },
                Ui.lp(c)
            )
            return
        }
        shown.forEach { container.addView(card(it)) }
    }

    private fun action(label: String, color: Int, onClick: () -> Unit): TextView {
        val c: Context = this
        return Ui.text(c, label, 14f, color, true).apply {
            isClickable = true
            val h = Ui.dp(c, 12)
            val v = Ui.dp(c, 8)
            setPadding(h, v, h, v)
            setOnClickListener { onClick() }
        }
    }

    private fun card(a: AlarmItem): View {
        val c: Context = this
        val sc = Ui.statusColor(a.status)
        val active = a.status == AlarmItem.SCHEDULED

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.glass(c, 22, Ui.alpha(sc, 130))
            val p = Ui.dp(c, 16)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 10).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, a) { render() } }
        }
        card.layoutParams = Ui.lp(c, bottom = 14)
        Ui.attachTilt(card)

        // السطر العلوي: الوقت + شارة الحالة
        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            Ui.text(c, formatTime(a.hour, a.minute), 30f, if (active) Ui.ACCENT else Ui.MUTED, true).apply {
                if (active) setShadowLayer(14f, 0f, 0f, Ui.ACCENT)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        top.addView(
            Ui.text(c, "● " + statusLabel(a.status), 13f, sc, true).apply {
                background = Ui.shape(c, Ui.alpha(sc, 35), 12, Ui.alpha(sc, 120))
                val h = Ui.dp(c, 12)
                val v = Ui.dp(c, 5)
                setPadding(h, v, h, v)
            }
        )
        card.addView(top)

        card.addView(Ui.text(c, a.name, 17f, Ui.TEXT, true), Ui.lp(c, top = 4))

        // النوع + الرقم
        val kind = if (a.autoCall) "📞 اتصال تلقائي • " + a.number else "🔔 تنبيه فقط"
        card.addView(Ui.text(c, kind, 14f, Ui.MUTED), Ui.lp(c, top = 6))

        // التكرار
        card.addView(Ui.text(c, "🔁 " + repeatSummary(a), 13f, Ui.MUTED), Ui.lp(c, top = 4))

        // القادم / آخر تنفيذ
        if (active) {
            val t = AlarmScheduler.nextTrigger(a)
            if (t != null) card.addView(Ui.text(c, "⏭ القادم: " + formatStamp(t), 13f, Ui.TEXT), Ui.lp(c, top = 4))
        }
        if (a.lastRun > 0L) {
            card.addView(Ui.text(c, "🕘 آخر تنفيذ: " + formatStamp(a.lastRun), 12f, Ui.MUTED), Ui.lp(c, top = 4))
            if (a.lastResult.isNotEmpty()) {
                val bad = a.lastResult.startsWith("تعذّر")
                card.addView(
                    Ui.text(c, a.lastResult, 12f, if (bad) Ui.DANGER else Ui.SUCCESS, true),
                    Ui.lp(c, top = 2)
                )
            }
        } else if (a.lastResult.isNotEmpty()) {
            card.addView(Ui.text(c, a.lastResult, 12f, Ui.DANGER, true), Ui.lp(c, top = 4))
        }

        // أزرار
        val actions = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (active) {
            if (a.autoCall) actions.addView(action("اختبار الآن", Ui.ACCENT) { testCall(a) })
            actions.addView(action("إلغاء", Ui.WARN) {
                AlarmScheduler.cancel(c, a.id)
                AlarmStore.upsert(c, a.copy(status = AlarmItem.CANCELED))
                render()
            })
        } else {
            actions.addView(action("إعادة جدولة", Ui.SUCCESS) {
                AlarmStore.reactivate(c, a)
                Toast.makeText(c, "تمت إعادة الجدولة", Toast.LENGTH_SHORT).show()
                render()
            })
        }
        actions.addView(action("تعديل", Ui.PRIMARY) { AlarmEditor.show(this@MainActivity, a) { render() } })
        actions.addView(action("حذف", Ui.DANGER) { confirmDelete(a) })
        card.addView(actions, Ui.lp(c, top = 6))
        return card
    }

    // ------------------------------------------------------------------ إجراءات

    private fun testCall(a: AlarmItem) {
        if (!CallHelper.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 102)
            return
        }
        Ui.confirm(this, "اختبار الاتصال", "سيتم الاتصال بالرقم ${a.number} الآن. متابعة؟", "اتصل") {
            val err = CallHelper.place(this, a.number)
            if (err != null) Toast.makeText(this, "تعذّر الاتصال: $err", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(a: AlarmItem) {
        Ui.confirm(this, "حذف الموعد", "هل تريد حذف «${a.name}»؟", "حذف") {
            AlarmScheduler.cancel(this, a.id)
            AlarmStore.delete(this, a.id)
            render()
        }
    }

    private fun accountDialog() {
        val who = Auth.userName(this) + "\n" + Auth.userEmail(this)
        Ui.confirm(this, "الحساب", who + "\n\nهل تريد تسجيل الخروج؟\n(ستبقى مواعيدك محفوظة وتعمل في وقتها)", "تسجيل الخروج") {
            Auth.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
