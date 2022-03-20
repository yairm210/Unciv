package com.unciv.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.SystemFonts
import android.os.Build
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.utils.FontData
import com.unciv.ui.utils.NativeFontImplementation

/**
 * Created by tian on 2016/10/2.
 */
class NativeFontAndroid(private val size: Int, private val fontFamily: String) :
    NativeFontImplementation {
    private val paint = Paint().apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val font = fontList.firstOrNull {
                it.file?.nameWithoutExtension == fontFamily
            }
            if (font != null) {
                Typeface.CustomFallbackBuilder(
                    FontFamily.Builder(font).build()
                ).setSystemFallback(fontFamily).build()
            } else {
                Typeface.create(fontFamily, Typeface.NORMAL)
            }
        } else {
            Typeface.create(fontFamily, Typeface.NORMAL)
        }
        isAntiAlias = true
        textSize = size.toFloat()
        strokeWidth = 0f
        setARGB(255, 255, 255, 255)
    }

    private val fontList: List<Font>
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) emptyList()
        else SystemFonts.getAvailableFonts().toList()

    override fun getFontSize(): Int {
        return size
    }

    override fun getCharPixmap(char: Char): Pixmap {
        val metric = paint.fontMetrics
        var width = paint.measureText(char.toString()).toInt()
        var height = (metric.descent - metric.ascent).toInt()
        if (width == 0) {
            height = size
            width = height
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
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

    override fun getAvailableFont(): Collection<FontData> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SystemFonts.getAvailableFonts().mapNotNull {
                it.file?.nameWithoutExtension
            }.map { FontData(it) }.toSet()
        } else {
            listOf(FontData("sans-serif"), FontData("serif"), FontData("mono"))
        }
    }
}