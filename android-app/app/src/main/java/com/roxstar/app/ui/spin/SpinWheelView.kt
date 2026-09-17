package com.roxstar.app.ui.spin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

/**
 * Custom spinning-wheel view that renders a coloured pie chart of participants.
 *
 * Public API:
 *  - [setSegments]   – update the name list (stops & resets any running animation)
 *  - [spinToEliminate] – spin smoothly and call back with the eliminated name index
 *  - [reset]         – return to idle state
 */
class SpinWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Palette ──────────────────────────────────────────────────────────────

    private val segmentColors = listOf(
        Color.parseColor("#6366F1"), // indigo
        Color.parseColor("#EC4899"), // pink
        Color.parseColor("#10B981"), // emerald
        Color.parseColor("#F59E0B"), // amber
        Color.parseColor("#3B82F6"), // blue
        Color.parseColor("#EF4444"), // red
        Color.parseColor("#8B5CF6"), // violet
        Color.parseColor("#14B8A6"), // teal
        Color.parseColor("#F97316"), // orange
        Color.parseColor("#A855F7"), // purple
        Color.parseColor("#22D3EE"), // cyan
        Color.parseColor("#4ADE80"), // green
    )

    // ── Paints ───────────────────────────────────────────────────────────────

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E293B")
        strokeWidth = 3f
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
    }

    private val pointerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    // ── State ────────────────────────────────────────────────────────────────

    private var segments: List<String> = emptyList()
    private var currentRotation: Float = 0f
    private var animator: ValueAnimator? = null

    /** If non-null we are showing a "winner" glow on this index. */
    private var winnerIndex: Int? = null

    private val winnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.parseColor("#F59E0B")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Replace the current segment list and redraw immediately (no animation). */
    fun setSegments(names: List<String>) {
        animator?.cancel()
        segments = names
        winnerIndex = null
        invalidate()
    }

    /**
     * Spin for ~[durationMs] ms then call [onFinished] with the index of the
     * segment that lands under the pointer (top / 270°).
     */
    fun spinToEliminate(durationMs: Long = 5000L, onFinished: (eliminatedIndex: Int) -> Unit) {
        if (segments.isEmpty()) return

        animator?.cancel()

        // Pick a random winner segment; work out exactly how many degrees to
        // rotate so it lands pointing straight up (270° in standard math coords).
        val targetIndex = (segments.indices).random()
        val sliceDeg = 360f / segments.size
        val targetAngle = -(targetIndex * sliceDeg + sliceDeg / 2f) + 270f
        // Add at least 5 full spins for drama
        val extraSpins = (5 + (2..4).random()) * 360f
        val totalRotation = currentRotation + extraSpins + ((targetAngle - currentRotation) % 360f + 360f)

        val startRotation = currentRotation
        animator = ValueAnimator.ofFloat(startRotation, totalRotation).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(2.5f)
            addUpdateListener { anim ->
                currentRotation = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Callback after animation ends
        animator!!.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                currentRotation = totalRotation % 360f
                winnerIndex = targetIndex
                invalidate()
                onFinished(targetIndex)
            }
        })
    }

    /** Clear winner highlight and stop animations. */
    fun reset() {
        animator?.cancel()
        winnerIndex = null
        currentRotation = 0f
        invalidate()
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val pointerH = w * 0.07f          // pointer triangle height
        val radius   = (min(w, h) / 2f) - pointerH - 8f
        val innerR   = radius * 0.22f     // center-circle radius

        if (segments.isEmpty()) {
            drawEmptyState(canvas, cx, cy, radius)
            return
        }

        // ── Glow outer ring ───────────────────────────────────────────────────
        val glowShader = SweepGradient(cx, cy,
            intArrayOf(
                Color.parseColor("#6366F1"),
                Color.parseColor("#EC4899"),
                Color.parseColor("#F59E0B"),
                Color.parseColor("#6366F1")
            ), null
        )
        outerRingPaint.shader = glowShader
        canvas.drawCircle(cx, cy, radius + 4f, outerRingPaint)

        // ── Pie segments ──────────────────────────────────────────────────────
        val sliceDeg = 360f / segments.size
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        for (i in segments.indices) {
            val startAngle = currentRotation + i * sliceDeg - 90f
            segmentPaint.color = segmentColors[i % segmentColors.size]
            segmentPaint.style = Paint.Style.FILL

            // Winner highlight
            if (i == winnerIndex) {
                segmentPaint.color = Color.parseColor("#F59E0B")
            }

            canvas.drawArc(oval, startAngle, sliceDeg, true, segmentPaint)
            canvas.drawArc(oval, startAngle, sliceDeg, true, borderPaint)

            // Label: only draw if slice is wide enough
            if (sliceDeg > 15f) {
                val midAngleRad = Math.toRadians((startAngle + sliceDeg / 2f).toDouble())
                val labelR = radius * 0.65f
                val lx = cx + (cos(midAngleRad) * labelR).toFloat()
                val ly = cy + (sin(midAngleRad) * labelR).toFloat()

                canvas.save()
                canvas.rotate((startAngle + sliceDeg / 2f + 90f), lx, ly)

                val name = segments[i]
                val maxChars = when {
                    sliceDeg >= 60 -> 10
                    sliceDeg >= 30 -> 6
                    else           -> 4
                }
                val label = if (name.length > maxChars) name.take(maxChars - 1) + "…" else name
                textPaint.textSize = (radius * 0.1f).coerceIn(10f, 20f)
                canvas.drawText(label, lx, ly + textPaint.textSize / 3f, textPaint)
                canvas.restore()
            }
        }

        // ── Winner glow arc ───────────────────────────────────────────────────
        winnerIndex?.let { wi ->
            val startAngle = currentRotation + wi * sliceDeg - 90f
            winnerPaint.alpha = ((sin(System.currentTimeMillis() / 300.0) * 127 + 128)).toInt()
            canvas.drawArc(oval, startAngle, sliceDeg, false, winnerPaint)
            postInvalidateDelayed(50) // keep pulsing
        }

        // ── Center circle ─────────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, innerR, centerCirclePaint)
        borderPaint.strokeWidth = 4f
        borderPaint.color = Color.parseColor("#334155")
        canvas.drawCircle(cx, cy, innerR, borderPaint)
        borderPaint.strokeWidth = 3f
        borderPaint.color = Color.parseColor("#1E293B")

        centerTextPaint.textSize = innerR * 0.38f
        canvas.drawText("🎡", cx, cy + centerTextPaint.textSize / 3f, centerTextPaint)

        // ── Pointer (top) ─────────────────────────────────────────────────────
        val pw = pointerH * 0.7f
        val pTop = cy - radius - 8f
        val path = Path().apply {
            moveTo(cx, pTop)
            lineTo(cx - pw / 2f, pTop + pointerH)
            lineTo(cx + pw / 2f, pTop + pointerH)
            close()
        }
        // shadow
        canvas.save()
        canvas.translate(3f, 3f)
        canvas.drawPath(path, pointerShadowPaint)
        canvas.restore()
        canvas.drawPath(path, pointerPaint)
    }

    private fun drawEmptyState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        segmentPaint.color = Color.parseColor("#1E293B")
        segmentPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, segmentPaint)
        borderPaint.color = Color.parseColor("#334155")
        canvas.drawCircle(cx, cy, radius, borderPaint)

        textPaint.textSize = radius * 0.18f
        textPaint.color = Color.parseColor("#64748B")
        canvas.drawText("Join a room", cx, cy - radius * 0.1f, textPaint)
        canvas.drawText("to see the wheel", cx, cy + radius * 0.15f, textPaint)
        textPaint.color = Color.WHITE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the wheel square
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}
