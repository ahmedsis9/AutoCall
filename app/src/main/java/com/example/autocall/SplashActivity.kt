package com.example.autocall

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.PRIMARY_DARK)
            setPadding(Ui.dp(c, 24), Ui.dp(c, 64), Ui.dp(c, 24), Ui.dp(c, 24))
        }

        // اسم التطبيق بخط مزخرف في أعلى الشاشة
        val title = Ui.text(c, Ui.APP_NAME, 56f, Color.WHITE, true).apply {
            typeface = Ui.fancyFont()
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 0f, Ui.PRIMARY)
        }
        root.addView(title, Ui.lp(c))

        val sub = Ui.text(c, "جدولة مكالماتك وتنبيهاتك بسهولة", 14f, Ui.ACCENT).apply {
            gravity = Gravity.CENTER
        }
        root.addView(sub, Ui.lp(c, top = 4))

        val logo = ImageView(c).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val size = Ui.dp(c, 260)
        root.addView(logo, Ui.lp(c, w = size, h = size, top = 48))

        setContentView(root)
        Ui.rtl(root)

        root.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 700 })

        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (Auth.isLoggedIn(c)) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(c, next))
            finish()
        }, 1600)
    }
}
