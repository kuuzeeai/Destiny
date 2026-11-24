package ai.opencode.assist

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The island-style animation: bars while listening or speaking, pulsing dots while thinking. */
class AssistBars(context: Context) : View(context) {
    enum class Mode { Idle, Listening, Thinking, Speaking }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var mode = Mode.Listening
    private var t = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    fun setMode(next: Mode) {
        mode = next
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun bars(canvas: Canvas, color: Int, speed: Float, amp: Float) {
        paint.color = color
        val n = 5
        val w = width.toFloat() / (n * 2 - 1)
        for (i in 0 until n) {
            val level = 0.2f + 0.8f * amp * abs(sin(2 * PI * (t * speed + i * 0.17)).toFloat())
            val h = height * level
            val x = i * 2 * w
            rect.set(x, (height - h) / 2, x + w, (height + h) / 2)
            canvas.drawRoundRect(rect, w / 2, w / 2, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        when (mode) {
            Mode.Listening -> bars(canvas, 0xFF4DA3FF.toInt(), 2f, 1f)
            Mode.Speaking -> bars(canvas, 0xFF59D98E.toInt(), 1f, 0.8f)
            Mode.Idle -> bars(canvas, 0xFF77777F.toInt(), 0f, 0.1f)
            Mode.Thinking -> {
                for (i in 0 until 3) {
                    val k = 0.65f + 0.35f * sin(2 * PI * (t - i * 0.2)).toFloat()
                    paint.color = 0xFFFFC857.toInt()
                    paint.alpha = (255 * k).toInt()
                    canvas.drawCircle(width * (0.2f + 0.3f * i), height / 2f, height * 0.16f * k, paint)
                }
            }
        }
    }
}
