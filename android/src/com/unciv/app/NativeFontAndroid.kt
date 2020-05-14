package com.unciv.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.utils.NativeFontImplementation

/**
 * Created by tian on 2016/10/2.
 */
class NativeFontAndroid(val size: Int) : NativeFontImplementation {
    override fun getFontSize(): Int {
        return size
    }

    override fun getCharPixmap(char: Char): Pixmap {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textSize = size.toFloat()
        val metric = paint.fontMetrics
        var width = paint.measureText(char.toString()).toInt()
        var height = (metric.descent - metric.ascent).toInt()
        if (width == 0) {
            height = size
            width = height
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.strokeWidth = 0f
        paint.setARGB(255, 255, 255, 255)
        canvas.drawText(char.toString(), 0f, -metric.ascent, paint)
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        val data = IntArray(width * height)
        bitmap.getPixels(data, 0, width, 0, 0, width, height)
        for (i in 0 until width) {
            for (j in 0 until height) {
                pixmap.setColor(Integer.rotateLeft(data[i + (j * width)], 8))
                pixmap.drawPixel(i, j)
            }
        }
        bitmap.recycle()
        return pixmap
    }
}