package com.example.autocall

import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** شاشة بسيطة: تسجيل دخول (معرّف + كلمة مرور) أو حساب جديد (معرّف + كلمة مرور + تأكيدها). */
class LoginActivity : Activity() {

    private var registerMode = false
    private var prefill = ""
    private lateinit var card: LinearLayout
    private val rcGoogle = 301

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        val c: Context = this

        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(
            CinematicBackground(c).apply { intensity = 0.7f },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val scroll = ScrollView(c).apply { isFillViewport = true }
        root.addView(
            scroll,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(c, 22), Ui.dp(c, 36), Ui.dp(c, 22), Ui.dp(c, 28))
        }
        scroll.addView(
            col,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )

        val logo = ImageView(c).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        col.addView(logo, Ui.lp(c, w = Ui.dp(c, 110), h = Ui.dp(c, 110)))

        col.addView(
            Ui.text(c, Ui.APP_NAME, 44f, Color.WHITE, true).apply {
                typeface = Ui.fancyFont()
                gravity = Gravity.CENTER
                setShadowLayer(22f, 0f, 0f, Ui.PRIMARY)
            },
            Ui.lp(c, bottom = 14)
        )

        card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.glass(c, 26)
            val p = Ui.dp(c, 22)
            setPadding(p, p, p, p)
            elevation = Ui.dp(c, 12).toFloat()
        }
        Ui.attachTilt(card)
        col.addView(card, Ui.lp(c))

        registerMode = !Auth.hasAccounts(c)
        render()

        setContentView(root)
        Ui.rtl(root)
    }

    private fun idField(): EditText =
        Ui.input(this, "البريد أو اسم المستخدم", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

    private fun passField(hint: String): EditText =
        Ui.input(this, hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

    private fun link(text: String, color: Int = Ui.ACCENT, onClick: () -> Unit): TextView {
        val c: Context = this
        return Ui.text(c, text, 14f, color, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            val p = Ui.dp(c, 8)
            setPadding(p, p, p, p)
            setOnClickListener { onClick() }
        }
    }

    private fun render() {
        val c: Context = this
        card.removeAllViews()

        card.addView(
            Ui.text(c, if (registerMode) "إنشاء حساب" else "تسجيل الدخول", 22f, Ui.TEXT, true),
            Ui.lp(c, bottom = 14)
        )

        val id = idField()
        if (prefill.isNotEmpty()) {
            id.setText(prefill)
            prefill = ""
        }
        card.addView(id, Ui.lp(c, bottom = 10))

        if (registerMode) {
            val pass = passField("كلمة مرور جديدة")
            card.addView(pass, Ui.lp(c, bottom = 10))
            val confirm = passField("تأكيد كلمة المرور")
            card.addView(confirm, Ui.lp(c, bottom = 10))

            val btn = Ui.button(c, "إنشاء الحساب")
            card.addView(btn, Ui.lp(c, top = 4))
            btn.setOnClickListener {
                val err = Auth.register(c, id.text.toString(), pass.text.toString(), confirm.text.toString())
                if (err != null) toast(err) else enter()
            }
        } else {
            val pass = passField("كلمة المرور")
            card.addView(pass, Ui.lp(c, bottom = 6))

            card.addView(link("هل نسيت كلمة المرور؟", Ui.WARN) { forgot(id.text.toString()) }, Ui.lp(c, bottom = 6))

            val btn = Ui.button(c, "دخول")
            card.addView(btn, Ui.lp(c, top = 4))
            btn.setOnClickListener {
                val err = Auth.login(c, id.text.toString(), pass.text.toString())
                if (err != null) toast(err) else enter()
            }
        }

        addGoogle()

        card.addView(
            link(if (registerMode) "لديك حساب؟ سجّل الدخول" else "ليس لديك حساب؟ أنشئ حساباً جديداً") {
                registerMode = !registerMode
                render()
            },
            Ui.lp(c, top = 6)
        )
    }

    /** نسيت كلمة المرور: يُعاد إنشاء الحساب بكلمة جديدة وتبقى مواعيده. */
    private fun forgot(idText: String) {
        if (idText.isBlank()) {
            toast("اكتب البريد أو اسم المستخدم أولاً ثم اضغط «نسيت كلمة المرور»")
            return
        }
        if (!Auth.exists(this, idText)) {
            toast("لا يوجد حساب بهذا الاسم")
            return
        }
        Ui.confirm(
            this, "كلمة مرور جديدة",
            "سيُفتح لك إنشاء كلمة مرور جديدة للحساب «${Auth.normalize(idText)}» وستبقى مواعيدك محفوظة.",
            "متابعة"
        ) {
            Auth.resetAccount(this, idText)
            prefill = Auth.normalize(idText)
            registerMode = true
            render()
        }
    }

    // ------------------------------------------------------------------ Google

    private fun addGoogle() {
        val c: Context = this
        card.addView(
            Ui.text(c, "— أو —", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER },
            Ui.lp(c, top = 14, bottom = 10)
        )
        val label = "G   المتابعة عبر حساب Google"
        val sp = SpannableString(label).apply {
            setSpan(ForegroundColorSpan(Color.parseColor("#4285F4")), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val g = TextView(c).apply {
            text = sp
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1F2433"))
            background = Ui.shape(c, Color.WHITE, 16)
            val v = Ui.dp(c, 13)
            setPadding(v, v, v, v)
            isClickable = true
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { pickGoogle() }
        }
        card.addView(g, Ui.lp(c))
    }

    /** يفتح منتقي حسابات Google الموجودة على الجهاز (أو يتيح إضافة حساب). */
    private fun pickGoogle() {
        try {
            val i = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            startActivityForResult(i, rcGoogle)
        } catch (e: ActivityNotFoundException) {
            toast("خدمات Google غير متوفرة على هذا الجهاز")
        } catch (e: Exception) {
            toast("تعذّر فتح حسابات Google")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == rcGoogle && resultCode == RESULT_OK) {
            val email = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (email.isNullOrBlank()) {
                toast("لم يتم اختيار حساب")
            } else {
                Auth.loginWithGoogle(this, email)
                enter()
            }
        }
    }

    private fun enter() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
