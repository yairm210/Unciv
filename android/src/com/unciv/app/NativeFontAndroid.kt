package com.unciv.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import android.os.Build
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.utils.FontFamilyData
import com.unciv.ui.utils.NativeFontImplementation
import java.util.*
import kotlin.math.abs

/**
 * Created by tian on 2016/10/2.
 */
class NativeFontAndroid(
    private val size: Int,
    private val fontFamily: String
) : NativeFontImplementation {
    private val fontList by lazy{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) emptySet()
        else SystemFonts.getAvailableFonts()
    }

    private val paint by lazy{ createPaint() }
    fun createPaint() = Paint().apply {
        typeface = if (fontFamily.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Helper within the VERSION_CODES.Q gate: Evaluate a Font's desirability (lower = better) for a given family.
            fun Font.matchesFamily(family: String): Int {
                val name = file?.nameWithoutExtension ?: return Int.MAX_VALUE
                if (name == family) return 0
                if (!name.startsWith("$family-")) return Int.MAX_VALUE
                if (style.weight == FontStyle.FONT_WEIGHT_NORMAL && style.slant == FontStyle.FONT_SLANT_UPRIGHT) return 1
                return 2 +
                        abs(style.weight - FontStyle.FONT_WEIGHT_NORMAL) / 100 +
                        abs(style.slant - FontStyle.FONT_SLANT_UPRIGHT)
            }
            val font = fontList.mapNotNull {
                val distanceToRegular = it.matchesFamily(fontFamily)
                if (distanceToRegular == Int.MAX_VALUE) null else it to distanceToRegular
            }.minByOrNull { it.second }?.first
            if (font != null) {
                Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                    .setSystemFallback(fontFamily).build()
            } else Typeface.create(fontFamily, Typeface.NORMAL)
        } else Typeface.create(fontFamily, Typeface.NORMAL)

        isAntiAlias = true
        textSize = size.toFloat()
        strokeWidth = 0f
        setARGB(255, 255, 255, 255)
    }

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

    override fun getAvailableFontFamilies(): Sequence<FontFamilyData> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return sequenceOf(FontFamilyData("sans-serif"), FontFamilyData("serif"), FontFamilyData("mono"))

        fun String.stripFromFirstDash(): String {
            val dashPos = indexOf('-')
            if (dashPos < 0) return this
            return this.substring(0, dashPos)
        }

        // To get _all_ Languages a user has in their Android settings, we would need more help
        // from the launcher: (Activity).resources.configuration.locales
        val languageTag = Locale.getDefault().toLanguageTag()  // e.g. he-IL, corresponds to the _first_ Language in Android settings
        val supportedLocales = arrayOf(languageTag, "en-US")
        val supportedLanguages = supportedLocales.map { it.take(2) }
        return fontList.asSequence()
            .mapNotNull {
                if (it.file == null) return@mapNotNull null
                val fontLocale = it.localeList.getFirstMatch(supportedLocales)
                val fontScriptToLanguage = fontLocale?.script?.take(2)?.lowercase()
                // The font localeList contains locales that have nothing to do with the system locales
                // their language and country fields are empty - so **guess** that the first two letters
                // of their Script (coming in at 4 chars) corresponds to the first two of the default Locale toLanguageTag:
                if (!it.localeList.isEmpty && fontScriptToLanguage !in supportedLanguages)
                    return@mapNotNull null
                // The API talks about FontFamily, but I see no methods to ask for the family of a Font instance.
                // No displayName either. So, again, infer from the file name:
                it.file!!.nameWithoutExtension.stripFromFirstDash()
            }.distinct()
            .map { FontFamilyData(it, it) }
    }
}
