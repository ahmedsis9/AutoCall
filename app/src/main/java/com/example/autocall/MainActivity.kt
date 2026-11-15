package com.example.autocall

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
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
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.READ_PHONE_STATE)
        }
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
            CinematicBackground(c).apply { intensity = 0.3f },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            col,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // ----- الترويسة: تحية كبيرة + صورة الحساب -----
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(c, 20)
            setPadding(p, Ui.dp(c, 18), p, Ui.dp(c, 10))
        }
        val titles = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(
            Ui.text(c, Ui.APP_NAME, 18f, Ui.ACCENT, true).apply {
                typeface = Ui.fancyFont()
                setShadowLayer(12f, 0f, 0f, Ui.ACCENT)
            }
        )
        val greet = Ui.text(c, "", 24f, Color.WHITE, true)
        greetingTv = greet
        titles.addView(greet, Ui.lp(c, top = 2))
        titles.addView(Ui.text(c, formatDateLong(System.currentTimeMillis()), 13f, Ui.MUTED), Ui.lp(c, top = 2))
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val initial = Auth.userName(c).trim().take(1).uppercase(Locale.ROOT).ifEmpty { "؟" }
        val avatar = Ui.text(c, initial, 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 24)
            elevation = Ui.dp(c, 6).toFloat()
            isClickable = true
            setOnClickListener { accountDialog() }
        }
        header.addView(avatar, LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)))
        col.addView(header, Ui.lp(c))

        // ----- محتوى قابل للتمرير -----
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 16)
            setPadding(p, Ui.dp(c, 6), p, Ui.dp(c, 120))
        }
        val scroll = ScrollView(c).apply {
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // بطاقة «الاتصال القادم»: تدرّج ملوّن وعدّاد كبير
        val hero = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#3D7BFF"), Color.parseColor("#7B4DFF"))
            ).apply { cornerRadius = Ui.dp(c, 28).toFloat() }
            val p = Ui.dp(c, 20)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 14).toFloat()
        }
        Ui.attachTilt(hero)
        val hl = Ui.text(c, "", 14f, Ui.alpha(Color.WHITE, 200), true)
        val hc = Ui.text(c, "--:--:--", 44f, Color.WHITE, true).apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.START
        }
        val hs = Ui.text(c, "", 15f, Color.WHITE, true)
        heroLabel = hl
        heroCount = hc
        heroSub = hs
        hero.addView(hl, Ui.lp(c))
        hero.addView(hc, Ui.lp(c, top = 4))
        hero.addView(hs, Ui.lp(c, top = 6))
        content.addView(hero, Ui.lp(c, bottom = 16))

        // شريط تبويبات على شكل حبّة
        val tabs = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.glass(c, 24, Ui.alpha(Color.WHITE, 40))
            val p = Ui.dp(c, 4)
            setPadding(p, p, p, p)
        }
        tabsRow = tabs
        content.addView(tabs, Ui.lp(c, bottom = 12))

        // لوحة فحص النظام
        val health = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        healthBox = health
        content.addView(health, Ui.lp(c, bottom = 12))

        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        listBox = list
        content.addView(list, Ui.lp(c))

        // ----- زر إضافة عائم على شكل حبّة -----
        val fab = Ui.text(c, "＋  اتصال جديد", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 30)
            val h = Ui.dp(c, 22)
            val v = Ui.dp(c, 15)
            setPadding(h, v, h, v)
            elevation = Ui.dp(c, 12).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, null) { render() } }
        }
        Ui.attachTilt(fab)
        root.addView(
            fab,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
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
        greetingTv?.text = "$g، ${Auth.userName(this)} 👋"
    }

    private fun nextUpcoming(): Pair<AlarmItem, Long>? =
        AlarmStore.mine(this)
            .filter { it.enabled }
            .mapNotNull { a -> AlarmScheduler.nextTrigger(a)?.let { t -> Pair(a, t) } }
            .minByOrNull { it.second }

    private fun updateHero() {
        val n = nextUpcoming()
        if (n == null) {
            heroLabel?.text = "📞 الاتصال القادم"
            heroCount?.text = "--:--:--"
            heroSub?.text = "لا توجد مكالمات مجدولة • اضغط «اتصال جديد»"
            return
        }
        val (a, t) = n
        var s = ((t - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        val days = s / 86400L
        s %= 86400L
        val clock = String.format(Locale.US, "%02d:%02d:%02d", s / 3600L, (s % 3600L) / 60L, s % 60L)
        heroLabel?.text = "📞 الاتصال القادم بعد"
        heroCount?.text = if (days > 0L) "${days}d $clock" else clock
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
        defs.forEach { (st, label, count) ->
            val sel = filter == st
            val tab = Ui.text(c, "$label $count", 13f, if (sel) Color.WHITE else Ui.MUTED, true).apply {
                gravity = Gravity.CENTER
                if (sel) background = Ui.gradient(c, Ui.PRIMARY, Ui.VIOLET, 20)
                val v = Ui.dp(c, 10)
                setPadding(Ui.dp(c, 4), v, Ui.dp(c, 4), v)
                isClickable = true
                setOnClickListener {
                    filter = st
                    render()
                }
            }
            row.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        if (!CallHelper.canOverlay(c)) {
            issues.add(Issue("اسمح بـ «العرض فوق التطبيقات» ليتصل Hamko مباشرة والجهاز مقفل.", "السماح") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            })
        }
        val nmgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 34 && !nmgr.canUseFullScreenIntent()) {
            issues.add(Issue("فعّل «الإشعارات بملء الشاشة» لضمان ظهور شاشة الاتصال فوق القفل.", "تفعيل") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
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
                Ui.text(c, "✅ كل شيء جاهز • ستُنفَّذ المكالمات في موعدها حتى والشاشة مقفلة", 12f, Ui.SUCCESS, true)
                    .apply { gravity = Gravity.CENTER },
                Ui.lp(c)
            )
            return
        }

        val wrap = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.glass(c, 20, Ui.alpha(Ui.WARN, 150))
            val p = Ui.dp(c, 14)
            setPadding(p, p, p, p)
        }
        wrap.addView(Ui.text(c, "⚠️ لضمان نجاح الاتصال التلقائي", 14f, Ui.WARN, true), Ui.lp(c, bottom = 6))
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
                background = Ui.shape(c, Ui.WARN, 14)
                val h = Ui.dp(c, 14)
                val v = Ui.dp(c, 7)
                setPadding(h, v, h, v)
                isClickable = true
                setOnClickListener { issue.fix() }
            }
            row.addView(fixBtn, Ui.lp(c, w = LinearLayout.LayoutParams.WRAP_CONTENT, start = 8))
            wrap.addView(row, Ui.lp(c, top = 6))
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
            val msg = if (all.isEmpty()) "📞\nلا توجد مكالمات بعد\nاضغط «اتصال جديد» لإضافة أول موعد"
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

    private fun chip(text: String, color: Int): TextView {
        val c: Context = this
        return Ui.text(c, text, 12f, color, true).apply {
            background = Ui.shape(c, Ui.alpha(color, 35), 12)
            val h = Ui.dp(c, 10)
            val v = Ui.dp(c, 4)
            setPadding(h, v, h, v)
        }
    }

    private fun action(label: String, color: Int, onClick: () -> Unit): TextView {
        val c: Context = this
        return Ui.text(c, label, 13f, color, true).apply {
            background = Ui.shape(c, Ui.alpha(color, 30), 14)
            isClickable = true
            val h = Ui.dp(c, 14)
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
            background = Ui.glass(c, 24, Ui.alpha(Color.WHITE, 45))
            val p = Ui.dp(c, 16)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 8).toFloat()
            isClickable = true
            setOnClickListener { AlarmEditor.show(this@MainActivity, a) { render() } }
        }
        card.layoutParams = Ui.lp(c, bottom = 12)
        Ui.attachTilt(card)

        // الصف الأول: أيقونة + (الاسم/الرقم) + (الوقت/الحالة)
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = Ui.text(c, "📞", 20f).apply {
            gravity = Gravity.CENTER
            background = Ui.shape(c, Ui.alpha(sc, 45), 16)
        }
        row.addView(icon, LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)))

        val mid = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(Ui.text(c, a.name, 17f, Ui.TEXT, true))
        val numTv = Ui.text(c, if (a.autoCall) a.number else "تنبيه", 13f, Ui.MUTED).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.START
        }
        mid.addView(numTv, Ui.lp(c, top = 2))
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(c, 12)
            marginEnd = Ui.dp(c, 8)
        })

        val right = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(
            Ui.text(c, formatTime(a.hour, a.minute), 22f, if (active) Ui.ACCENT else Ui.MUTED, true)
        )
        right.addView(chip("● " + statusLabel(a.status), sc), Ui.lp(c, w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 4))
        row.addView(right)
        card.addView(row)

        // التكرار والقادم/آخر تنفيذ
        card.addView(Ui.text(c, "🔁 " + repeatSummary(a), 13f, Ui.MUTED), Ui.lp(c, top = 12))
        if (active) {
            val t = AlarmScheduler.nextTrigger(a)
            if (t != null) card.addView(Ui.text(c, "⏭ القادم: " + formatStamp(t), 13f, Ui.TEXT), Ui.lp(c, top = 4))
        }
        if (a.lastRun > 0L) {
            card.addView(Ui.text(c, "🕘 آخر تنفيذ: " + formatStamp(a.lastRun), 12f, Ui.MUTED), Ui.lp(c, top = 4))
        }
        if (a.lastResult.isNotEmpty()) {
            val bad = a.lastResult.startsWith("تعذّر") || a.lastResult.startsWith("فات")
            card.addView(
                Ui.text(c, a.lastResult, 12f, if (bad) Ui.DANGER else Ui.SUCCESS, true),
                Ui.lp(c, top = 4)
            )
        }

        // أزرار
        val actions = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        fun add(v: TextView) {
            actions.addView(v, Ui.lp(c, w = LinearLayout.LayoutParams.WRAP_CONTENT, end = 8))
        }
        if (active) {
            if (a.autoCall) add(action("اتصل الآن", Ui.ACCENT) { testCall(a) })
            add(action("إلغاء", Ui.WARN) {
                AlarmScheduler.cancel(c, a.id)
                AlarmStore.upsert(c, a.copy(status = AlarmItem.CANCELED))
                render()
            })
        } else {
            add(action("إعادة جدولة", Ui.SUCCESS) {
                AlarmStore.reactivate(c, a)
                Toast.makeText(c, "تمت إعادة الجدولة", Toast.LENGTH_SHORT).show()
                render()
            })
        }
        add(action("تعديل", Ui.PRIMARY) { AlarmEditor.show(this@MainActivity, a) { render() } })
        add(action("حذف", Ui.DANGER) { confirmDelete(a) })
        card.addView(actions, Ui.lp(c, top = 12))
        return card
    }

    // ------------------------------------------------------------------ جهات الاتصال

    private var onContact: ((String, String) -> Unit)? = null

    /** يفتح جهات الاتصال ويعيد (الرقم، الاسم). لا يحتاج إذن READ_CONTACTS. */
    fun pickContact(cb: (String, String) -> Unit) {
        onContact = cb
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 401)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح جهات الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 401 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                null, null, null
            )?.use { cur ->
                if (cur.moveToFirst()) onContact?.invoke(cur.getString(0) ?: "", cur.getString(1) ?: "")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر قراءة الرقم من جهة الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ إجراءات

    private fun testCall(a: AlarmItem) {
        if (!CallHelper.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 102)
            return
        }
        Ui.confirm(this, "اتصال الآن", "سيتم الاتصال بالرقم ${PhoneUtil.clean(a.number)} الآن. متابعة؟", "اتصل") {
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
        val who = Auth.userEmail(this)
        Ui.confirm(this, "الحساب", who + "\n\nهل تريد تسجيل الخروج؟\n(ستبقى مواعيدك محفوظة وتعمل في وقتها)", "تسجيل الخروج") {
            Auth.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
