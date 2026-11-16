package com.example.autocall

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    // لوحة ألوان هادئة وغير لامعة: نيلي رمادي + لمسات سماوية/وردية/رملية خافتة
    val BG: Int = Color.parseColor("#0E1424")
    val PRIMARY: Int = Color.parseColor("#6B7BDB")      // نيلي ناعم
    val PRIMARY_DARK: Int = Color.parseColor("#0E1424")
    val VIOLET: Int = Color.parseColor("#4C9AA8")       // فيروزي هادئ (نهاية التدرّج)
    val ACCENT: Int = Color.parseColor("#86CDC4")       // نعناعي فاتح
    val TEXT: Int = Color.parseColor("#E4E8F3")
    val MUTED: Int = Color.parseColor("#8B95B0")
    val DANGER: Int = Color.parseColor("#D8707D")
    val SUCCESS: Int = Color.parseColor("#6DBE9A")
    val WARN: Int = Color.parseColor("#D8A762")
    val STROKE: Int = Color.parseColor("#2A3552")
    val SURFACE_SOLID: Int = Color.parseColor("#18203A")
    val INPUT_BG: Int = Color.parseColor("#1A2340")

    const val APP_NAME = "Auto Caller"

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun alpha(color: Int, a: Int): Int = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun statusColor(status: Int): Int = when (status) {
        AlarmItem.COMPLETED -> SUCCESS
        AlarmItem.CANCELED -> DANGER
        else -> Color.parseColor("#7BA3E0")
    }

    fun shape(ctx: Context, color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    /** سطح بطاقة هادئ: تدرّج خفيف جداً وحدّ رفيع. */
    fun glass(ctx: Context, radiusDp: Int, stroke: Int = Color.parseColor("#2F3B5C")): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#F01F2846"), Color.parseColor("#F0182039"))
        ).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setStroke(dp(ctx, 1), stroke)
        }

    fun gradient(ctx: Context, c1: Int, c2: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    /** تموّج لمس خفيف فوق أي خلفية (يتطلب أن يكون العنصر clickable). */
    fun ripple(content: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(alpha(Color.WHITE, 46)), content, null)

    fun rtl(v: View) {
        v.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun setupWindow(a: Activity) {
        a.window.statusBarColor = BG
        a.window.navigationBarColor = BG
    }

    /** خط عنوان نظيف (بدون زخرفة). */
    fun fancyFont(): Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    fun lp(
        ctx: Context,
        w: Int = LinearLayout.LayoutParams.MATCH_PARENT,
        h: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply {
            topMargin = dp(ctx, top)
            bottomMargin = dp(ctx, bottom)
            marginStart = dp(ctx, start)
            marginEnd = dp(ctx, end)
        }

    fun text(ctx: Context, s: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    /** زر رئيسي (تدرّج هادئ) أو ثانوي (خلفية خافتة + حدّ). حجم مدمج وتفاعل لمس. */
    fun button(ctx: Context, s: String, filled: Boolean = true, color: Int = PRIMARY): TextView =
        TextView(ctx).apply {
            text = s
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            setTypeface(typeface, Typeface.BOLD)
            val h = dp(ctx, 16)
            val v = dp(ctx, 11)
            setPadding(h, v, h, v)
            if (filled) {
                background = ripple(gradient(ctx, color, VIOLET, 14))
                setTextColor(Color.WHITE)
            } else {
                background = ripple(shape(ctx, alpha(color, 26), 14, alpha(color, 120)))
                setTextColor(color)
            }
            attachPress(this, 0.96f)
        }

    /** حبّة صغيرة قابلة للنقر (للإجراءات داخل البطاقات). */
    fun pill(ctx: Context, label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(color)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isClickable = true
            background = ripple(shape(ctx, alpha(color, 28), 12))
            val h = dp(ctx, 12)
            val v = dp(ctx, 6)
            setPadding(h, v, h, v)
            attachPress(this, 0.93f)
            setOnClickListener { onClick() }
        }

    fun input(ctx: Context, hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(ctx).apply {
            hint = hintText
            inputType = type
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = shape(ctx, INPUT_BG, 12, STROKE)
            val v = dp(ctx, 12)
            setPadding(v, v, v, v)
        }

    /** تفاعل لمس: يصغر العنصر قليلاً عند الضغط ويعود عند الرفع. */
    fun attachPress(v: View, scale: Float) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    /** للبطاقات: ضغطة ناعمة جداً. */
    fun attachTilt(v: View) = attachPress(v, 0.985f)

    /** نافذة حوار داكنة موحّدة. */
    fun showDialog(act: Activity, builder: AlertDialog.Builder): AlertDialog {
        val dlg = builder.create()
        dlg.show()
        dlg.window?.setBackgroundDrawable(shape(act, SURFACE_SOLID, 20, STROKE))
        dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ACCENT)
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(MUTED)
        return dlg
    }

    fun builder(act: Activity): AlertDialog.Builder =
        AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)

    fun confirm(act: Activity, title: String, msg: String, yes: String, onYes: () -> Unit) {
        showDialog(
            act,
            builder(act).setTitle(title).setMessage(msg)
                .setPositiveButton(yes) { _, _ -> onYes() }
                .setNegativeButton("إلغاء", null)
        )
    }
}
