package com.youcefm.bypassctrl

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class PowerFlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val dashEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    private val dp = resources.displayMetrics.density

    var isBypass = false
    var isPlugged = true

    init {
        textPaint.textSize = 9f * dp
        textPaint.color = context.getColor(R.color.md_theme_onSurface)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val nodeR = 10f * dp

        val plugX = w * 0.18f
        val phoneX = w * 0.5f
        val battX = w * 0.82f

        val activeColor = context.getColor(R.color.charge_active)
        val inactiveColor = context.getColor(R.color.md_theme_surfaceVariant)
        val bypassColor = context.getColor(R.color.bypass_active)

        nodePaint.color = if (isPlugged) activeColor else inactiveColor
        canvas.drawCircle(plugX, cy, nodeR, nodePaint)
        canvas.drawText("AC", plugX, cy + nodeR + textPaint.textSize + 2f * dp, textPaint)

        nodePaint.color = if (isPlugged) activeColor else inactiveColor
        canvas.drawCircle(phoneX, cy, nodeR, nodePaint)
        canvas.drawText("Phone", phoneX, cy + nodeR + textPaint.textSize + 2f * dp, textPaint)

        nodePaint.color = if (isPlugged && !isBypass) activeColor else if (isBypass) bypassColor else inactiveColor
        canvas.drawCircle(battX, cy, nodeR, nodePaint)
        canvas.drawText("Batt", battX, cy + nodeR + textPaint.textSize + 2f * dp, textPaint)

        arrowPaint.strokeWidth = 3f * dp
        arrowPaint.pathEffect = null
        arrowPaint.color = if (isPlugged) activeColor else inactiveColor
        drawArrow(canvas, plugX + nodeR, phoneX - nodeR, cy)

        if (isBypass) {
            arrowPaint.color = bypassColor
            arrowPaint.pathEffect = dashEffect
        } else {
            arrowPaint.color = if (isPlugged) activeColor else inactiveColor
            arrowPaint.pathEffect = null
        }
        drawArrow(canvas, phoneX + nodeR, battX - nodeR, cy)
    }

    private fun drawArrow(canvas: Canvas, x1: Float, x2: Float, y: Float) {
        canvas.drawLine(x1, y, x2, y, arrowPaint)
        val s = 6f * dp
        path.reset()
        path.moveTo(x2 - s, y - s / 2)
        path.lineTo(x2, y)
        path.lineTo(x2 - s, y + s / 2)
        path.close()
        canvas.drawPath(path, arrowPaint)
    }
}
