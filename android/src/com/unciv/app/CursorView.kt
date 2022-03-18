package com.unciv.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat

class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
    private val paint = Paint().apply {
        color = ResourcesCompat.getColor(context.resources, android.R.color.white, null)
        alpha = (0xff * 0.8).toInt()
        style = Paint.Style.FILL
    }
    val cursor by lazy { PointF(width / 2f, height / 2f) }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cursor.x, cursor.y, 10f, paint)
    }
}