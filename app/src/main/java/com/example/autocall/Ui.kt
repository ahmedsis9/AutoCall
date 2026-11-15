package com.example.autocall

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    // ألوان السمة السينمائية الداكنة
    val BG: Int = Color.parseColor("#050816")
    val PRIMARY: Int = Color.parseColor("#3D7BFF")
    val PRIMARY_DARK: Int = Color.parseColor("#050816")
    val VIOLET: Int = Color.parseColor("#8A5CFF")
    val ACCENT: Int = Color.parseColor("#22E1FF")
    val TEXT: Int = Color.parseColor("#EAF0FF")
    val MUTED: Int = Color.parseColor("#8FA0C4")
    val DANGER: Int = Color.parseColor("#FF5C7A")
    val SUCCESS: Int = Color.parseColor("#2EE6A6")
    val WARN: Int = Color.parseColor("#FFB74D")
    val STROKE: Int = Color.parseColor("#3A4A7A")
    val SURFACE_SOLID: Int = Color.parseColor("#0F1733")
    val INPUT_BG: Int = Color.parseColor("#141E40")

    const val APP_NAME = "Hamko"

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun alpha(color: Int, a: Int): Int = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun statusColor(status: Int): Int = when (status) {
        AlarmItem.COMPLETED -> SUCCESS
        AlarmItem.CANCELED -> DANGER
        else -> Color.parseColor("#4DA3FF")
    }

    fun shape(ctx: Context, color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    /** بطاقة زجاجية داكنة بتدرّج مائل وحدّ مضيء. */
    fun glass(ctx: Context, radiusDp: Int, stroke: Int = Color.parseColor("#4D7FA6FF")): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#E01C2858"), Color.parseColor("#E00A1130"))
        ).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setStroke(dp(ctx, 1), stroke)
        }

    fun gradient(ctx: Context, c1: Int, c2: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    fun rtl(v: View) {
        v.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun setupWindow(a: Activity) {
        a.window.statusBarColor = BG
        a.window.navigationBarColor = BG
    }

    /** خط مزخرف لاسم التطبيق (cursive المتوفر في أندرويد). */
    fun fancyFont(): Typeface = Typeface.create("cursive", Typeface.BOLD)

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

    fun text(ctx: Context, s: String, size: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun button(ctx: Context, s: String, filled: Boolean = true, color: Int = PRIMARY): TextView =
        TextView(ctx).apply {
            text = s
            textSize = 16f
            gravity = Gravity.CENTER
            isClickable = true
            setTypeface(typeface, Typeface.BOLD)
            val v = dp(ctx, 14)
            setPadding(v, v, v, v)
            if (filled) {
                background = gradient(ctx, color, VIOLET, 16)
                setTextColor(Color.WHITE)
                elevation = dp(ctx, 6).toFloat()
            } else {
                background = shape(ctx, alpha(color, 30), 16, color)
                setTextColor(color)
            }
        }

    fun input(ctx: Context, hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(ctx).apply {
            hint = hintText
            inputType = type
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = shape(ctx, INPUT_BG, 14, STROKE)
            val v = dp(ctx, 14)
            setPadding(v, v, v, v)
        }

    /** تأثير ثلاثي الأبعاد: تميل البطاقة نحو نقطة اللمس وتصغر قليلاً. */
    fun attachTilt(v: View) {
        v.cameraDistance = 12000f * v.resources.displayMetrics.density
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val w = view.width.coerceAtLeast(1).toFloat()
                    val h = view.height.coerceAtLeast(1).toFloat()
                    val nx = (e.x / w - 0.5f).coerceIn(-0.5f, 0.5f)
                    val ny = (e.y / h - 0.5f).coerceIn(-0.5f, 0.5f)
                    view.animate().rotationY(nx * 14f).rotationX(-ny * 14f)
                        .scaleX(0.975f).scaleY(0.975f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).setDuration(240).start()
            }
            false
        }
    }

    /** نافذة حوار داكنة موحّدة. */
    fun showDialog(act: Activity, builder: AlertDialog.Builder): AlertDialog {
        val dlg = builder.create()
        dlg.show()
        dlg.window?.setBackgroundDrawable(shape(act, SURFACE_SOLID, 24, STROKE))
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
