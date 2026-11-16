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
 * خلفية التطبيق (شاشة البداية وما بعد فتح التطبيق): أزرق كهربائي عميق بألوان الشعار،
 * شفق متدرّج (أزرق/سماوي/أخضر/ذهبي)، أشعة ضوء قطرية تتمايل، وفقاعات ضوئية تصعد ببطء.
 * تتفاعل مع ميلان الهاتف (باراللاكس).
 */
class CinematicBackground(ctx: Context) : View(ctx), SensorEventListener {

    private class Orb(val x: Float, val speed: Float, val phase: Float, val size: Float, val kind: Int)

    /** شدّة الأشعة والفقاعات (تُخفَّف في الشاشات المزدحمة). */
    var intensity: Float = 1f

    private val rnd = Random(11)
    private val orbs = Array(46) {
        Orb(rnd.nextFloat(), 0.012f + rnd.nextFloat() * 0.03f, rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.75f, rnd.nextInt(3))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    private var base: Shader? = null
    private var vignette: Shader? = null
    private var blue: Shader? = null
    private var cyan: Shader? = null
    private var green: Shader? = null
    private var gold: Shader? = null
    private var ray: Shader? = null
    private var blobR = 1f
    private var rayLen = 1f

    private var tx = 0f
    private var ty = 0f
    private var sx = 0f
    private var sy = 0f
    private var time = 0f
    private val t0 = SystemClock.uptimeMillis()

    private val orbColors = intArrayOf(Color.parseColor("#5AB4FF"), Color.parseColor("#22E1FF"), Color.parseColor("#FFFFFF"))

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
        val wf = max(w, 1).toFloat()
        val hf = max(h, 1).toFloat()
        base = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#040B26"), Color.parseColor("#0A2C94"), Color.parseColor("#061A66"), Color.parseColor("#030B2E")),
            floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        blobR = max(wf, hf) * 0.6f
        blue = RadialGradient(0f, 0f, blobR, Color.parseColor("#802F7BFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cyan = RadialGradient(0f, 0f, blobR, Color.parseColor("#5522E1FF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        green = RadialGradient(0f, 0f, blobR * 0.8f, Color.parseColor("#3C2BFF6A"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        gold = RadialGradient(0f, 0f, blobR * 0.55f, Color.parseColor("#33FFC94A"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        rayLen = max(wf, hf) * 1.5f
        ray = LinearGradient(0f, 0f, rayLen, 0f, Color.parseColor("#40BFE3FF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        vignette = RadialGradient(
            wf / 2, hf / 2, max(wf, hf) * 0.8f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#99000000")),
            floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = base
        canvas.drawRect(0f, 0f, w, h, paint)

        // الشفق: كتل ضوئية كبيرة تنجرف ببطء
        blob(canvas, blue, w * (0.78f + 0.07f * sin(time * 0.16f)) + sx * 25f * d, h * (0.18f + 0.05f * sin(time * 0.12f)))
        blob(canvas, cyan, w * (0.15f + 0.08f * sin(time * 0.11f + 1f)) + sx * 15f * d, h * (0.42f + 0.06f * sin(time * 0.09f)))
        blob(canvas, green, w * (0.85f + 0.05f * sin(time * 0.13f + 2f)) - sx * 20f * d, h * (0.7f + 0.05f * sin(time * 0.1f)))
        blob(canvas, gold, w * (0.3f + 0.06f * sin(time * 0.1f + 3f)), h * (0.92f + 0.03f * sin(time * 0.14f)))

        // أشعة ضوء قطرية من الزاوية العلوية تتمايل
        paint.shader = ray
        for (i in 0 until 4) {
            val ang = 118f + i * 11f + 5f * sin(time * 0.25f + i)
            paint.alpha = ((70 - i * 12) * intensity).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(w * 1.02f + sx * 12f * d, -h * 0.04f)
            canvas.rotate(ang)
            canvas.drawRect(0f, -(26f + i * 14f) * d, rayLen, (26f + i * 14f) * d, paint)
            canvas.restore()
        }
        paint.shader = null

        // فقاعات ضوئية تصعد
        for (o in orbs) {
            val py = h - (((o.phase + time * o.speed) % 1f) * (h * 1.1f)) + sy * 18f * d * o.size
            val px = o.x * w + 14f * d * sin(time * 0.6f + o.phase * 6.28f) + sx * 28f * d * o.size
            val life = ((py / h).coerceIn(0f, 1f))
            val a = (110f * o.size * (0.35f + 0.65f * sin(life * 3.14f)) * intensity).toInt().coerceIn(0, 255)
            paint.color = orbColors[o.kind]
            paint.alpha = a
            canvas.drawCircle(px, py, (1.5f + o.size * 5f) * d, paint)
        }

        // تعتيم الأطراف
        paint.shader = vignette
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun blob(canvas: Canvas, shader: Shader?, cx: Float, cy: Float) {
        paint.shader = shader
        paint.alpha = 255
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, blobR, paint)
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
