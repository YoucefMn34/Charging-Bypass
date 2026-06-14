package com.youcefm.bypassctrl

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.google.android.material.button.MaterialButton

fun TextView.animateColor(toColor: Int, duration: Long = 300) {
    val fromColor = this.currentTextColor
    ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
        this.duration = duration
        addUpdateListener { animator ->
            this@animateColor.setTextColor(animator.animatedValue as Int)
        }
        start()
    }
}

fun MaterialButton.animateBackgroundTint(toColor: Int, duration: Long = 300) {
    val fromColor = this.backgroundTintList?.defaultColor ?: toColor
    ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
        this.duration = duration
        addUpdateListener { animator ->
            this@animateBackgroundTint.backgroundTintList =
                ColorStateList.valueOf(animator.animatedValue as Int)
        }
        start()
    }
}

fun TextView.animateNumber(oldValue: Float, newValue: Float, format: String, duration: Long = 600) {
    if (kotlin.math.abs(oldValue - newValue) < 0.01f || oldValue == 0f) {
        this.text = String.format(format, newValue)
        return
    }
    ValueAnimator.ofFloat(oldValue, newValue).apply {
        this.duration = duration
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            this@animateNumber.text = String.format(format, animator.animatedValue as Float)
        }
        start()
    }
}

fun View.fadeIn(duration: Long = 250) {
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setDuration(duration).start()
}

fun View.fadeOut(duration: Long = 250) {
    animate().alpha(0f).setDuration(duration).withEndAction {
        visibility = View.GONE
    }.start()
}
