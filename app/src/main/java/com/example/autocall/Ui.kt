package com.example.autocall

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val PRIMARY: Int = Color.parseColor("#1E6BFF")
    val PRIMARY_DARK: Int = Color.parseColor("#070D18")
    val NAVY: Int = Color.parseColor("#0B1426")
    val ACCENT: Int = Color.parseColor("#22C1FF")
    val ACCENT_DARK: Int = Color.parseColor("#0288D1")
    val BG: Int = Color.parseColor("#F3F5FA")
    val TEXT: Int = Color.parseColor("#1F2433")
    val MUTED: Int = Color.parseColor("#6B7280")
    val DANGER: Int = Color.parseColor("#D32F2F")
    val STROKE: Int = Color.parseColor("#D7DBE6")
    val BADGE_CALL: Int = Color.parseColor("#E3ECFF")
    val BADGE_ALERT: Int = Color.parseColor("#E1F5FE")

    const val APP_NAME = "Hamko"

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun shape(ctx: Context, color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    fun rtl(v: View) {
        v.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun setupWindow(a: Activity, color: Int = PRIMARY_DARK) {
        a.window.statusBarColor = color
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
                background = shape(ctx, color, 14)
                setTextColor(Color.WHITE)
            } else {
                background = shape(ctx, Color.WHITE, 14, color)
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
            background = shape(ctx, Color.WHITE, 12, STROKE)
            val v = dp(ctx, 14)
            setPadding(v, v, v, v)
        }
}
