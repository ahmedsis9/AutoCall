package com.example.autocall

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * خلفية سينمائية متحركة: سديم متدرّج، نجوم بثلاث طبقات عمق، وأرضية شبكية بمنظور ثلاثي الأبعاد.
 * تتفاعل مع ميلان الهاتف (باراللاكس) عبر مستشعر التسارع.
 */
class CinematicBackground(ctx: Context) : View(ctx), SensorEventListener {

    private class Star(val x: Float, val y: Float, val z: Float, val tw: Float)

    /** شدّة الشبكة والتوهج (أقل في الشاشات المزدحمة بالنصوص). */
    var intensity: Float = 1f

    private val rnd = Random(7)
    private val stars = Array(150) {
        Star(rnd.nextFloat(), rnd.nextFloat(), 0.2f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 6.28f)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    private var base: Shader? = null
    private var vignette: Shader? = null
    private var glow: Shader? = null
    private var nebula1: Shader? = null
    private var nebula2: Shader? = null
    private var nebula3: Shader? = null
    private var nebulaR = 1f

    private var tx = 0f
    private var ty = 0f
    private var sx = 0f
    private var sy = 0f
    private var time = 0f
    private val t0 = SystemClock.uptimeMillis()

    private val cyan = Color.parseColor("#22E1FF")
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = (SystemClock.uptimeMillis() - t0) / 1000f
            sx += (tx - sx) * 0.08f
            sy += (ty - sy) * 0.08f
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        base = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#03050F"), Color.parseColor("#0A1030"), Color.parseColor("#1A0F3C")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        nebulaR = max(wf, hf) * 0.55f
        nebula1 = RadialGradient(0f, 0f, nebulaR, Color.parseColor("#665A2EFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        nebula2 = RadialGradient(0f, 0f, nebulaR, Color.parseColor("#4D12B8FF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        nebula3 = RadialGradient(0f, 0f, nebulaR, Color.parseColor("#40FF3DA5"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        val y0 = hf * 0.66f
        glow = LinearGradient(
            0f, y0 - hf * 0.14f, 0f, y0 + hf * 0.05f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#552AD4FF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        vignette = RadialGradient(
            wf / 2, hf / 2, max(wf, hf) * 0.78f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#B0000000")),
            floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = base
        canvas.drawRect(0f, 0f, w, h, paint)

        // السديم: ثلاث كتل ضوئية تنجرف ببطء
        drawBlob(canvas, nebula1, w * (0.25f + 0.08f * sin(time * 0.17f)) + sx * 25f * d, h * (0.22f + 0.05f * sin(time * 0.11f)))
        drawBlob(canvas, nebula2, w * (0.85f + 0.06f * sin(time * 0.13f + 1f)) + sx * 15f * d, h * (0.45f + 0.06f * sin(time * 0.09f)))
        drawBlob(canvas, nebula3, w * (0.4f + 0.07f * sin(time * 0.1f + 2f)) - sx * 20f * d, h * (0.78f + 0.04f * sin(time * 0.15f)))

        // النجوم
        paint.shader = null
        paint.color = Color.WHITE
        for (s in stars) {
            val px = ((s.x + time * 0.006f * s.z) % 1f) * w + sx * 30f * d * s.z
            val py = s.y * h * 0.8f + sy * 20f * d * s.z
            val tw = 0.55f + 0.45f * sin(time * 1.5f + s.tw)
            paint.alpha = (255f * tw * s.z).toInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, (0.5f + s.z * 1.7f) * d, paint)
        }

        // الأرضية الشبكية بمنظور ثلاثي الأبعاد
        val y0 = h * 0.66f
        paint.shader = glow
        paint.alpha = 255
        canvas.drawRect(0f, y0 - h * 0.14f, w, y0 + h * 0.05f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * d
        paint.color = cyan
        val n = 12
        val off = (time * 0.22f) % 1f
        for (i in 0 until n) {
            val f = (i + off) / n
            val y = y0 + (h - y0) * f * f
            paint.alpha = (120f * f * intensity).toInt().coerceIn(0, 255)
            canvas.drawLine(0f, y, w, y, paint)
        }
        val vx = w / 2 + sx * 40f * d
        paint.alpha = (70f * intensity).toInt().coerceIn(0, 255)
        for (i in -8..8) {
            val xb = w / 2 + i * (w / 5.5f) + sx * 60f * d
            canvas.drawLine(vx, y0, xb, h, paint)
        }

        // تعتيم الأطراف
        paint.style = Paint.Style.FILL
        paint.shader = vignette
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawBlob(canvas: Canvas, shader: Shader?, cx: Float, cy: Float) {
        paint.shader = shader
        paint.alpha = 255
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, nebulaR, paint)
        canvas.restore()
        paint.shader = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
            // بدون مستشعر: تبقى الخلفية متحركة دون باراللاكس
        }
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(this)
        } catch (e: Exception) {
            // لا شيء
        }
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(e: SensorEvent) {
        tx = (-e.values[0] / 9.8f).coerceIn(-1f, 1f)
        ty = (e.values[1] / 9.8f).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
