package com.example.autocall

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        val c: Context = this
        val register = !Auth.hasAccount(c)

        val scroll = ScrollView(c).apply {
            setBackgroundColor(Ui.PRIMARY_DARK)
            isFillViewport = true
        }
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(c, 24), Ui.dp(c, 48), Ui.dp(c, 24), Ui.dp(c, 24))
        }
        scroll.addView(col)

        // اسم التطبيق بخط مزخرف في الأعلى
        val title = Ui.text(c, Ui.APP_NAME, 48f, Color.WHITE, true).apply {
            typeface = Ui.fancyFont()
            gravity = Gravity.CENTER
            setShadowLayer(22f, 0f, 0f, Ui.PRIMARY)
        }
        col.addView(title, Ui.lp(c))

        val logoSize = Ui.dp(c, 120)
        val logo = ImageView(c).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        col.addView(logo, Ui.lp(c, w = logoSize, h = logoSize, top = 8, bottom = 16))

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.shape(c, Color.WHITE, 24)
            val p = Ui.dp(c, 22)
            setPadding(p, p, p, p)
        }
        col.addView(card, Ui.lp(c))

        card.addView(
            Ui.text(c, if (register) "إنشاء حساب" else "تسجيل الدخول", 22f, Ui.TEXT, true),
            Ui.lp(c, bottom = 4)
        )
        card.addView(
            Ui.text(
                c,
                if (register) "أنشئ حسابك على هذا الجهاز للبدء" else "مرحباً بعودتك، ${Auth.userName(c)}",
                14f, Ui.MUTED
            ),
            Ui.lp(c, bottom = 16)
        )

        var nameEt: EditText? = null
        var confirmEt: EditText? = null
        if (register) {
            val n = Ui.input(c, "الاسم")
            nameEt = n
            card.addView(n, Ui.lp(c, bottom = 10))
        }
        val passType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val passEt = Ui.input(c, "كلمة المرور", passType)
        card.addView(passEt, Ui.lp(c, bottom = 10))
        if (register) {
            val cf = Ui.input(c, "تأكيد كلمة المرور", passType)
            confirmEt = cf
            card.addView(cf, Ui.lp(c, bottom = 10))
        }

        val btn = Ui.button(c, if (register) "إنشاء الحساب" else "دخول")
        card.addView(btn, Ui.lp(c, top = 8))

        btn.setOnClickListener {
            val pass = passEt.text.toString()
            if (register) {
                val name = nameEt?.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(c, "أدخل اسمك", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pass.length < 4) {
                    Toast.makeText(c, "كلمة المرور يجب ألا تقل عن 4 أحرف", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pass != (confirmEt?.text?.toString() ?: "")) {
                    Toast.makeText(c, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Auth.register(c, name, pass)
            } else {
                if (!Auth.verify(c, pass)) {
                    Toast.makeText(c, "كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Auth.setLoggedIn(c, true)
            }
            startActivity(Intent(c, MainActivity::class.java))
            finish()
        }

        if (!register) {
            val reset = Ui.text(c, "نسيت كلمة المرور؟ إعادة تعيين الحساب", 13f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                isClickable = true
                val p = Ui.dp(c, 8)
                setPadding(p, p, p, p)
            }
            reset.setOnClickListener {
                AlertDialog.Builder(c, android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle("إعادة تعيين الحساب")
                    .setMessage("سيتم حذف الحساب وكل المواعيد المحفوظة. هل أنت متأكد؟")
                    .setPositiveButton("حذف") { _, _ ->
                        AlarmStore.clear(c)
                        Auth.reset(c)
                        recreate()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            card.addView(reset, Ui.lp(c, top = 10))
        }

        setContentView(scroll)
        Ui.rtl(scroll)
    }
}
