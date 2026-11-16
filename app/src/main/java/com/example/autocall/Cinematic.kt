package com.example.autocall

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * خلفية التطبيق: تدرّج نيلي هادئ، بقع لونية ناعمة، حلقات متحدة المركز، شبكات نقاط،
 * أشكال هندسية عائمة (مربعات مستديرة ودوائر) وموجتان في الأسفل. كلها بألوان خافتة غير لامعة
 * وتتحرك ببطء مع تفاعل خفيف لميلان الهاتف.
 */
class CinematicBackground(ctx: Context) : View(ctx), SensorEventListener {

    private class Shape(
        val x: Float, val y: Float, val size: Float, val rot: Float,
        val speed: Float, val kind: Int, val color: Int, val depth: Float
    )

    /** شدّة الزخارف (1 = كاملة). */
    var intensity: Float = 1f

    private val indigo = Color.parseColor("#6B7BDB")
    private val teal = Color.parseColor("#4C9AA8")
    private val rose = Color.parseColor("#C8808C")
    private val sand = Color.parseColor("#CDAE78")
    private val lav = Color.parseColor("#9A8FD9")
    private val palette = intArrayOf(indigo, teal, rose, sand, lav)

    private val rnd = Random(23)
    private val shapes = Array(12) {
        Shape(
            rnd.nextFloat(), rnd.nextFloat() * 0.85f, 14f + rnd.nextFloat() * 26f, rnd.nextFloat() * 6.28f,
            0.15f + rnd.nextFloat() * 0.35f, rnd.nextInt(3), palette[rnd.nextInt(palette.size)], 0.3f + rnd.nextFloat()
        )
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val d = ctx.resources.displayMetrics.density

    private var base: Shader? = null
    private var vignette: Shader? = null
    private var blobIndigo: Shader? = null
    private var blobTeal: Shader? = null
    private var blobRose: Shader? = null
    private var blobSand: Shader? = null
    private var blobR = 1f

    private var tx = 0f
    private var ty = 0f
    private var sx = 0f
    private var sy = 0f
    private var time = 0f
    private val t0 = SystemClock.uptimeMillis()

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = (SystemClock.uptimeMillis() - t0) / 1000f
            sx += (tx - sx) * 0.06f
            sy += (ty - sy) * 0.06f
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val wf = max(w, 1).toFloat()
        val hf = max(h, 1).toFloat()
        base = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#0D1322"), Color.parseColor("#18223E"), Color.parseColor("#0E1525")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        blobR = max(wf, hf) * 0.55f
        blobIndigo = RadialGradient(0f, 0f, blobR, Color.parseColor("#4D5D6BDB"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        blobTeal = RadialGradient(0f, 0f, blobR * 0.9f, Color.parseColor("#3A4C9AA8"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        blobRose = RadialGradient(0f, 0f, blobR * 0.75f, Color.parseColor("#26C8808C"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        blobSand = RadialGradient(0f, 0f, blobR * 0.6f, Color.parseColor("#1ECDAE78"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        vignette = RadialGradient(
            wf / 2, hf / 2, max(wf, hf) * 0.85f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#66000000")),
            floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = base
        canvas.drawRect(0f, 0f, w, h, paint)

        // بقع لونية ناعمة تنجرف ببطء
        blob(canvas, blobIndigo, w * (0.85f + 0.06f * sin(time * 0.12f)) + sx * 20f * d, h * (0.15f + 0.05f * sin(time * 0.1f)))
        blob(canvas, blobTeal, w * (0.1f + 0.07f * sin(time * 0.09f + 1f)) + sx * 14f * d, h * (0.5f + 0.06f * sin(time * 0.08f)))
        blob(canvas, blobRose, w * (0.9f + 0.05f * sin(time * 0.1f + 2f)) - sx * 16f * d, h * (0.72f + 0.05f * sin(time * 0.11f)))
        blob(canvas, blobSand, w * (0.25f + 0.06f * sin(time * 0.07f + 3f)), h * (0.95f + 0.02f * sin(time * 0.13f)))

        // حلقات متحدة المركز في زاويتين
        rings(canvas, w * 0.95f + sx * 10f * d, h * 0.09f + sy * 8f * d, 70f * d, 4)
        rings(canvas, w * 0.04f + sx * 8f * d, h * 0.9f + sy * 8f * d, 60f * d, 3)

        // شبكتا نقاط
        dots(canvas, w * 0.07f, h * 0.13f, 6, 5)
        dots(canvas, w * 0.97f - 5 * 16f * d, h * 0.62f, 6, 5)

        // أشكال هندسية عائمة
        for (s in shapes) {
            val px = s.x * w + sx * s.depth * 24f * d + 12f * d * sin(time * s.speed + s.rot)
            val py = s.y * h + 14f * d * cos(time * s.speed * 0.8f + s.rot) + sy * s.depth * 14f * d
            val r = s.size * d
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(time * s.speed * 25f + s.rot * 57f)
            paint.color = s.color
            when (s.kind) {
                0 -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f * d
                    paint.alpha = (50f * intensity).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(-r, -r, r, r, r * 0.3f, r * 0.3f, paint)
                }
                1 -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f * d
                    paint.alpha = (44f * intensity).toInt().coerceIn(0, 255)
                    canvas.drawCircle(0f, 0f, r * 0.8f, paint)
                }
                else -> {
                    paint.style = Paint.Style.FILL
                    paint.alpha = (24f * intensity).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(-r, -r, r, r, r * 0.35f, r * 0.35f, paint)
                }
            }
            canvas.restore()
        }

        // موجتان في الأسفل
        wave(canvas, w, h, h * 0.89f + sy * 10f * d, 14f * d, 1.3f, 0.5f, 0f, teal, (34f * intensity).toInt())
        wave(canvas, w, h, h * 0.935f + sy * 8f * d, 10f * d, 1.9f, -0.4f, 1f, indigo, (40f * intensity).toInt())

        // تعتيم خفيف للأطراف
        paint.style = Paint.Style.FILL
        paint.shader = vignette
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun blob(canvas: Canvas, shader: Shader?, cx: Float, cy: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = 255
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, blobR, paint)
        canvas.restore()
        paint.shader = null
    }

    private fun rings(canvas: Canvas, cx: Float, cy: Float, baseR: Float, n: Int) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * d
        paint.color = lav
        for (i in 0 until n) {
            val r = baseR * (1f + i * 0.5f) + 4f * d * sin(time * 0.5f + i)
            paint.alpha = ((36 - i * 7) * intensity).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    private fun dots(canvas: Canvas, x0: Float, y0: Float, cols: Int, rows: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (34f * intensity).toInt().coerceIn(0, 255)
        val gap = 16f * d
        for (i in 0 until cols) for (j in 0 until rows) {
            canvas.drawCircle(x0 + i * gap + sx * 6f * d, y0 + j * gap, 1.6f * d, paint)
        }
    }

    private fun wave(
        canvas: Canvas, w: Float, h: Float, baseY: Float, amp: Float,
        freq: Float, speed: Float, phase: Float, color: Int, alpha: Int
    ) {
        path.reset()
        path.moveTo(0f, h)
        val step = 12f * d
        var x = 0f
        while (x <= w + step) {
            path.lineTo(x, baseY + amp * sin(x / w * 6.2832f * freq + time * speed + phase))
            x += step
        }
        path.lineTo(w + step, h)
        path.close()
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawPath(path, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
            // بدون مستشعر: تبقى الخلفية متحركة دون تفاعل الميلان
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
