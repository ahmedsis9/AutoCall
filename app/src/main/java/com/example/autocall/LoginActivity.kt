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

/** تسجيل دخول للحسابات المسجّلة + إنشاء حساب جديد + نسيت كلمة المرور + ربط Google. */
class LoginActivity : Activity() {

    private enum class Mode { LOGIN, REGISTER, FORGOT }

    private var mode = Mode.LOGIN
    private lateinit var card: LinearLayout
    private var question = ""
    private var forgotEmail: String? = null   // بعد نجاح الخطوة الأولى في «نسيت كلمة المرور»
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

        mode = if (Auth.hasAccounts(c)) Mode.LOGIN else Mode.REGISTER
        render()

        setContentView(root)
        Ui.rtl(root)
    }

    // ------------------------------------------------------------------ العرض

    private fun render() {
        card.removeAllViews()
        when (mode) {
            Mode.LOGIN -> renderLogin()
            Mode.REGISTER -> renderRegister()
            Mode.FORGOT -> renderForgot()
        }
    }

    private fun heading(title: String, sub: String) {
        val c: Context = this
        card.addView(Ui.text(c, title, 22f, Ui.TEXT, true), Ui.lp(c, bottom = 4))
        card.addView(Ui.text(c, sub, 14f, Ui.MUTED), Ui.lp(c, bottom = 16))
    }

    private fun emailField(): EditText =
        Ui.input(this, "البريد الإلكتروني", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

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

    private fun renderLogin() {
        val c: Context = this
        heading("تسجيل الدخول", "مرحباً بعودتك، أدخل بيانات حسابك")

        val email = emailField()
        card.addView(email, Ui.lp(c, bottom = 10))
        val pass = passField("كلمة المرور")
        card.addView(pass, Ui.lp(c, bottom = 6))

        card.addView(link("هل نسيت كلمة المرور؟", Ui.WARN) {
            forgotEmail = null
            mode = Mode.FORGOT
            render()
        }, Ui.lp(c, bottom = 6))

        val btn = Ui.button(c, "دخول")
        card.addView(btn, Ui.lp(c, top = 4))
        btn.setOnClickListener {
            val err = Auth.login(c, email.text.toString(), pass.text.toString())
            if (err != null) toast(err) else enter()
        }

        addGoogle()

        card.addView(link("ليس لديك حساب؟ أنشئ حساباً جديداً") {
            mode = Mode.REGISTER
            render()
        }, Ui.lp(c, top = 6))
    }

    private fun renderRegister() {
        val c: Context = this
        heading("إنشاء حساب جديد", "أنشئ حسابك للبدء بجدولة مكالماتك")

        val name = Ui.input(c, "الاسم")
        card.addView(name, Ui.lp(c, bottom = 10))
        val email = emailField()
        card.addView(email, Ui.lp(c, bottom = 10))
        val pass = passField("كلمة المرور (${Auth.MIN_PASS} أحرف على الأقل)")
        card.addView(pass, Ui.lp(c, bottom = 10))
        val confirm = passField("تأكيد كلمة المرور")
        card.addView(confirm, Ui.lp(c, bottom = 10))

        // سؤال الأمان لاسترجاع كلمة المرور لاحقاً
        val qBtn = Ui.text(c, if (question.isEmpty()) "اختر سؤال الأمان ▾" else question, 15f,
            if (question.isEmpty()) Ui.MUTED else Ui.TEXT).apply {
            background = Ui.shape(c, Ui.INPUT_BG, 14, Ui.STROKE)
            val v = Ui.dp(c, 14)
            setPadding(v, v, v, v)
            isClickable = true
        }
        card.addView(qBtn, Ui.lp(c, bottom = 10))
        val answer = Ui.input(c, "جواب سؤال الأمان")
        card.addView(answer, Ui.lp(c, bottom = 4))
        card.addView(
            Ui.text(c, "يُستخدم السؤال لاستعادة كلمة المرور إن نسيتها.", 12f, Ui.MUTED),
            Ui.lp(c, bottom = 10)
        )
        qBtn.setOnClickListener {
            Ui.showDialog(
                this,
                Ui.builder(this).setTitle("سؤال الأمان")
                    .setItems(Auth.QUESTIONS.toTypedArray()) { _, i ->
                        question = Auth.QUESTIONS[i]
                        qBtn.text = question
                        qBtn.setTextColor(Ui.TEXT)
                    }
            )
        }

        val btn = Ui.button(c, "إنشاء الحساب")
        card.addView(btn, Ui.lp(c, top = 4))
        btn.setOnClickListener {
            if (pass.text.toString() != confirm.text.toString()) {
                toast("كلمتا المرور غير متطابقتين")
                return@setOnClickListener
            }
            val err = Auth.register(
                c, name.text.toString(), email.text.toString(), pass.text.toString(),
                question, answer.text.toString()
            )
            if (err != null) toast(err) else enter()
        }

        addGoogle()

        if (Auth.hasAccounts(c)) {
            card.addView(link("لديك حساب؟ سجّل الدخول") {
                mode = Mode.LOGIN
                render()
            }, Ui.lp(c, top = 6))
        }
    }

    private fun renderForgot() {
        val c: Context = this
        heading("استعادة كلمة المرور", "أجب عن سؤال الأمان لتعيين كلمة مرور جديدة")

        val email = emailField()
        val fe = forgotEmail
        if (fe == null) {
            card.addView(email, Ui.lp(c, bottom = 10))
            val next = Ui.button(c, "التالي")
            card.addView(next, Ui.lp(c, top = 4))
            next.setOnClickListener {
                val e = email.text.toString()
                if (!Auth.exists(c, e)) {
                    toast("لا يوجد حساب بهذا البريد")
                    return@setOnClickListener
                }
                if (Auth.questionFor(c, e) == null) {
                    toast("هذا الحساب مرتبط بـ Google ولا يملك كلمة مرور، استخدم زر Google")
                    return@setOnClickListener
                }
                forgotEmail = e
                render()
            }
        } else {
            card.addView(Ui.text(c, fe, 14f, Ui.ACCENT, true), Ui.lp(c, bottom = 8))
            card.addView(
                Ui.text(c, Auth.questionFor(c, fe) ?: "", 16f, Ui.TEXT, true),
                Ui.lp(c, bottom = 8)
            )
            val answer = Ui.input(c, "جواب سؤال الأمان")
            card.addView(answer, Ui.lp(c, bottom = 10))
            val np = passField("كلمة المرور الجديدة")
            card.addView(np, Ui.lp(c, bottom = 10))
            val cp = passField("تأكيد كلمة المرور الجديدة")
            card.addView(cp, Ui.lp(c, bottom = 10))
            val btn = Ui.button(c, "تعيين كلمة مرور جديدة")
            card.addView(btn, Ui.lp(c, top = 4))
            btn.setOnClickListener {
                if (np.text.toString() != cp.text.toString()) {
                    toast("كلمتا المرور غير متطابقتين")
                    return@setOnClickListener
                }
                val err = Auth.resetPassword(c, fe, answer.text.toString(), np.text.toString())
                if (err != null) toast(err) else {
                    toast("تم تعيين كلمة المرور الجديدة")
                    enter()
                }
            }
        }
        card.addView(link("العودة لتسجيل الدخول", Ui.MUTED) {
            forgotEmail = null
            mode = Mode.LOGIN
            render()
        }, Ui.lp(c, top = 8))
    }

    // ------------------------------------------------------------------ Google

    private fun addGoogle() {
        val c: Context = this
        val divider = Ui.text(c, "— أو —", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        card.addView(divider, Ui.lp(c, top = 14, bottom = 10))

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

    // ------------------------------------------------------------------ أدوات

    private fun enter() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
