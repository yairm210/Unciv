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
import androidx.annotation.RequiresApi
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.Log
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

class AndroidFont : FontImplementation {

    private val fontList by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) emptySet()
        else SystemFonts.getAvailableFonts()
    }
    private val paint: Paint = Paint()
    private var currentFontFamily: String? = null

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 0f
        paint.setARGB(255, 255, 255, 255)

    }

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        paint.textSize = size.toFloat()

        // Don't have to reload typeface if font-family didn't change
        if (currentFontFamily != fontFamilyData.invariantName) {
            currentFontFamily = fontFamilyData.invariantName

            // Mod font
            if (fontFamilyData.filePath != null)
            {
                paint.typeface = createTypefaceCustom(fontFamilyData.filePath!!)
            }
            // System font
            else
            {
                paint.typeface = createTypefaceSystem(fontFamilyData.invariantName)
            }

        }
    }

    private fun createTypefaceSystem(name: String): Typeface {
        if (name.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            val font = fontList.mapNotNull {
                val distanceToRegular = it.matchesFamily(name)
                if (distanceToRegular == Int.MAX_VALUE) null else it to distanceToRegular
            }.minByOrNull { it.second }?.first

            if (font != null)
            {
                return Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                    .setSystemFallback(name).build()
            }
        }
        return Typeface.create(name, Typeface.NORMAL)
    }

    private fun createTypefaceCustom(path: String): Typeface {
        return try
        {
            Typeface.createFromFile(Gdx.files.local(path).file())
        }
        catch (e: Exception)
        {
            Log.error("Failed to create typeface, falling back to default", e)
            // Falling back to default
            Typeface.create(Fonts.DEFAULT_FONT_FAMILY, Typeface.NORMAL)
        }
    }

    /** Helper within the VERSION_CODES.Q gate: Evaluate a Font's desirability (lower = better) for a given family. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun Font.matchesFamily(family: String): Int {
        val name = file?.nameWithoutExtension ?: return Int.MAX_VALUE
        if (name == family) return 0
        if (!name.startsWith("$family-")) return Int.MAX_VALUE
        if (style.weight == FontStyle.FONT_WEIGHT_NORMAL && style.slant == FontStyle.FONT_SLANT_UPRIGHT) return 1
        return 2 +
                abs(style.weight - FontStyle.FONT_WEIGHT_NORMAL) / 100 +
                abs(style.slant - FontStyle.FONT_SLANT_UPRIGHT)
    }

    override fun getFontSize(): Int {
        return paint.textSize.toInt()
    }

    override fun getCharPixmap(char: Char): Pixmap {
        val metric = paint.fontMetrics
        var width = paint.measureText(char.toString()).toInt()
        var height = ceil(metric.bottom - metric.top).toInt()
        if (width == 0) {
            height = getFontSize()
            width = height
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(char.toString(), 0f, -metric.top, paint)

        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        val data = IntArray(width * height)
        bitmap.getPixels(data, 0, width, 0, 0, width, height) // faster than bitmap[x, y]
        for (x in 0 until width) {
            for (y in 0 until height) {
                pixmap.drawPixel(x, y, Integer.rotateLeft(data[x + (y * width)], 8))
            }
        }
        bitmap.recycle()
        return pixmap
    }

    override fun getSystemFonts(): Sequence<FontFamilyData> {
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

    override fun getMetrics() = FontMetricsCommon(
        ascent = -paint.fontMetrics.ascent,
        descent = paint.fontMetrics.descent,
        height = paint.fontMetrics.bottom - paint.fontMetrics.top,
        leading = paint.fontMetrics.ascent - paint.fontMetrics.top
    )
}
