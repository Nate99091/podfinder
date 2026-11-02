package dev.podfinder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * A radial "how close are you" gauge: a thick arc that fills from empty to full as the signal
 * strengthens, tinted cold-blue to hot-red. Dependency-free custom view so the app stays light.
 */
class SignalMeterView(context: Context) : View(context) {

    /** 0f (far / no signal) .. 1f (right here). */
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** When false the gauge is drawn dim (signal lost). */
    var active: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#22808080")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val stroke = width * STROKE_FRACTION
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke

        val inset = stroke / 2 + width * PADDING_FRACTION
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        oval.set(cx - size / 2 + inset, cy - size / 2 + inset, cx + size / 2 - inset, cy + size / 2 - inset)

        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, trackPaint)
        if (level <= 0f) return

        arcPaint.color = if (active) warmthColor(level) else Color.parseColor("#55808080")
        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE * level, false, arcPaint)
    }

    private fun warmthColor(level: Float): Int {
        // Blue (cold) -> cyan -> green -> amber -> red (hot).
        val hue = 210f - 210f * level // 210 (blue) down to 0 (red)
        return Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 210f), 0.85f, 0.95f))
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
        const val STROKE_FRACTION = 0.085f
        const val PADDING_FRACTION = 0.04f
    }
}
