package com.youcefm.bypassctrl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BatteryIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    var batteryLevel = 50
    var isBypass = false
    var isCharging = false

    private val rect = RectF()
    private val dp = resources.displayMetrics.density
    private val cornerRadius = 8f * dp

    init {
        outlinePaint.color = context.getColor(R.color.md_theme_onSurfaceVariant)
        outlinePaint.strokeWidth = 3f * dp
        textPaint.color = context.getColor(R.color.md_theme_onSurface)
        textPaint.textSize = 12f * dp
    }

    override fun onDraw(canvas: Canvas) {
        val padding = 3f * dp
        val terminalH = 5f * dp
        val terminalW = width * 0.25f

        val bodyLeft = padding
        val bodyTop = padding + terminalH
        val bodyRight = width - padding
        val bodyBottom = height - padding

        if (isBypass) {
            glowPaint.color = context.getColor(R.color.bypass_active)
            glowPaint.strokeWidth = 6f * dp
            glowPaint.alpha = 100
            rect.set(bodyLeft - 3, bodyTop - 3, bodyRight + 3, bodyBottom + 3)
            canvas.drawRoundRect(rect, cornerRadius + 3, cornerRadius + 3, glowPaint)
            glowPaint.alpha = 255
        }

        rect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)

        val tLeft = width / 2f - terminalW / 2f
        val tRight = width / 2f + terminalW / 2f
        rect.set(tLeft, padding, tRight, bodyTop)
        canvas.drawRect(rect, outlinePaint)

        val fillH = (bodyBottom - bodyTop) * (batteryLevel / 100f)
        if (fillH > 0) {
            fillPaint.color = when {
                isBypass -> context.getColor(R.color.bypass_active)
                isCharging -> context.getColor(R.color.charge_active)
                else -> context.getColor(R.color.md_theme_primary)
            }
            rect.set(
                bodyLeft + outlinePaint.strokeWidth,
                bodyBottom - fillH,
                bodyRight - outlinePaint.strokeWidth,
                bodyBottom - outlinePaint.strokeWidth / 2
            )
            canvas.drawRoundRect(rect, cornerRadius / 2, cornerRadius / 2, fillPaint)
        }

        if (batteryLevel > 15) {
            canvas.drawText(
                "$batteryLevel%",
                width / 2f,
                bodyTop + (bodyBottom - bodyTop) / 2f + textPaint.textSize / 3,
                textPaint
            )
        }
    }
}
