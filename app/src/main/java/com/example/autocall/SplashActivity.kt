package com.example.autocall

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** مقدمة سينمائية: خلفية فضائية، شعار يدور في الأبعاد الثلاثة، عنوان يتمدد، ونص يُكتب تدريجياً. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("ci", false) && !Auth.isLoggedIn(this)) {
            Auth.register(this, "ci@test.com", "123456", "123456")
            val offs = longArrayOf(135_000L, 255_000L)
            val nums = arrayOf("٠٥٥٥ 1212 !?/", "+1 (555) 123-4567 //:'")
            for (k in 0 until 2) {
                val now = System.currentTimeMillis() + offs[k]
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                val it = AlarmItem(AlarmStore.nextId(this), "CI" + k, nums[k], cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE), AlarmItem.ONCE, emptySet(), true, AlarmItem.SCHEDULED,
                    now - 30_000L, "ci@test.com")
                AlarmStore.upsert(this, it)
                AlarmScheduler.schedule(this, it)
            }
        }
        Ui.setupWindow(this)
        val c = this
        val d = resources.displayMetrics.density

        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(
            CinematicBackground(c),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(
            col,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // توهج خلف الشعار + الشعار
        val stage = FrameLayout(c)
        val glowSize = Ui.dp(c, 340)
        val glow = View(c).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = glowSize / 2f
                colors = intArrayOf(Color.parseColor("#883D7BFF"), Color.parseColor("#2222E1FF"), Color.TRANSPARENT)
                shape = GradientDrawable.OVAL
            }
        }
        stage.addView(glow, FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER))
        val logoSize = Ui.dp(c, 220)
        val logo = ImageView(c).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            cameraDistance = 9000f * d
            rotationY = -110f
            alpha = 0f
        }
        stage.addView(logo, FrameLayout.LayoutParams(logoSize, logoSize, Gravity.CENTER))
        col.addView(stage, Ui.lp(c, w = glowSize, h = glowSize))

        val title = Ui.text(c, Ui.APP_NAME, 54f, Color.WHITE, true).apply {
            typeface = Ui.fancyFont()
            gravity = Gravity.CENTER
            setShadowLayer(26f, 0f, 0f, Ui.PRIMARY)
            alpha = 0f
            letterSpacing = 0.5f
        }
        col.addView(title, Ui.lp(c, top = 4))

        val sub = Ui.text(c, "", 15f, Ui.ACCENT).apply { gravity = Gravity.CENTER }
        col.addView(sub, Ui.lp(c, top = 6))

        // شريط تحميل رفيع أسفل الشاشة
        val barBox = FrameLayout(c).apply { background = Ui.shape(c, Ui.alpha(Color.WHITE, 30), 3) }
        val bar = View(c).apply {
            background = Ui.gradient(c, Ui.ACCENT, Ui.VIOLET, 3)
            scaleX = 0f
            pivotX = 0f
        }
        barBox.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(
            barBox,
            FrameLayout.LayoutParams(Ui.dp(c, 160), Ui.dp(c, 4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = Ui.dp(c, 56)
            }
        )

        setContentView(root)
        Ui.rtl(root)
        bar.pivotX = 0f

        // 1) الشعار يدور ويدخل
        logo.animate().rotationY(0f).alpha(1f).setDuration(1200)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
        // 2) طفو هادئ مستمر
        ObjectAnimator.ofFloat(logo, "translationY", 0f, -14f * d).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            startDelay = 1200
            start()
        }
        // 3) توهج ينبض
        ObjectAnimator.ofFloat(glow, "alpha", 0.55f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        // 4) العنوان يظهر ويتقارب حرفه
        title.animate().alpha(1f).setStartDelay(500).setDuration(900).setInterpolator(DecelerateInterpolator()).start()
        ValueAnimator.ofFloat(0.5f, 0.06f).apply {
            duration = 1400
            startDelay = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { title.letterSpacing = it.animatedValue as Float }
            start()
        }
        // 5) الشريط
        bar.animate().scaleX(1f).setDuration(2500).start()
        // 6) نص يُكتب حرفاً حرفاً
        typeText(sub, "جدولة مكالماتك وتنبيهاتك بذكاء", 1100L)

        handler.postDelayed({
            val next = if (Auth.isLoggedIn(c)) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(c, next))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2800)
    }

    private fun typeText(tv: TextView, full: String, startDelay: Long) {
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (i <= full.length) {
                    tv.text = full.substring(0, i)
                    i++
                    handler.postDelayed(this, 45)
                }
            }
        }
        handler.postDelayed(step, startDelay)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
