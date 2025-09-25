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
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.UncivGame
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.Log
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import androidx.core.graphics.createBitmap

class AndroidFont : FontImplementation {

    private val fontList by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) emptySet()
        else SystemFonts.getAvailableFonts()
    }
    private val paint: Paint = getPaintInstance()
    private var currentFontFamily: String? = null

    private fun getPaintInstance() = Paint().apply {
        isAntiAlias = true
        strokeWidth = 0f
        setARGB(255, 255, 255, 255)
    }

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        paint.textSize = size.toFloat()

        // Don't have to reload typeface if font-family didn't change
        if (currentFontFamily == fontFamilyData.invariantName) return
        currentFontFamily = fontFamilyData.invariantName

        paint.typeface =
            if (fontFamilyData.filePath != null) // Mod font
                createTypefaceCustom(fontFamilyData.filePath!!)
            else // System font
                createTypefaceSystem(fontFamilyData.invariantName)
    }

    private fun createTypefaceSystem(name: String): Typeface {
        if (name.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val font = fontList.mapNotNull {
                val distanceToRegular = it.matchesFamily(name)
                if (distanceToRegular == Int.MAX_VALUE) null else it to distanceToRegular
            }.minByOrNull { it.second }?.first

            if (font != null)
                return Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                    .setSystemFallback(name).build()
        }
        return Typeface.create(name, Typeface.NORMAL)
    }

    private fun createTypefaceCustom(path: String): Typeface {
        return try {
            Typeface.createFromFile(UncivGame.Current.files.getLocalFile(path).file())
        } catch (e: Exception) {
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

    override fun getCharPixmap(symbolString: String): Pixmap {
        val metric = getMetrics()  // Use our interpretation instead of paint.fontMetrics because it fixes some bad metrics
        var width = paint.measureText(symbolString).toInt()
        var height = ceil(metric.height).toInt()
        if (width == 0) {
            height = getFontSize()
            width = height
        }

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawText(symbolString, 0f, metric.leading + metric.ascent + 1f, paint)

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

    override fun getMetrics(): FontMetricsCommon {
        val ascent = -paint.fontMetrics.ascent  // invert to get distance
        val descent = paint.fontMetrics.descent
        val top = -paint.fontMetrics.top  // invert to get distance
        val bottom = paint.fontMetrics.bottom
        val height = top + bottom
        val leading = top - ascent
        val ascentDescentHeight = ascent + descent

        // Corrections: Some Android fonts report bullshit
        // Examples: "ComingSoon" and "NotoSansSymbols" fonts on Android "S"
        // See https://github.com/yairm210/Unciv/issues/10308

        // Hardcode values for the worst of them - I've seen NotoSansSymbols returning (42.4, 11.1, 68.1, 11.1),
        // or (53.4, 14.6, 117.7, 28.8): looks off, or (53.4, 14.6, 53.4, -11.1) - top below ascent
        // By discarding and re-instantiating our Paint instance on every setFontFamily you can get Noto to almost,
        // but not quite, report exclusively the "off" metrics. Curiously, soft start (Unciv exited through its own dialog,
        // but not removed from android's "recents list") or hard start (dev-tools kill, reinstall or swipe out of recents)
        // do seem to have an effect on the metrics reported by Noto (and only by Noto), though I haven't been able to
        // prove a reliable pattern. These hardcoded values are empiric.
        if (currentFontFamily == "NotoSansSymbols")
            return FontMetricsCommon(53.4f, 14.6f, 100f, 33f)

        if (height >= 1.02f * ascentDescentHeight)
            // maximum dimensions at least 2% larger than recommended ascender top to descender bottom distance:
            // looks sensible, keep those metrics
            return FontMetricsCommon(ascent, descent, height, leading)

        // When recommended size equals maximum size...
        if (height >= 0.98f * ascentDescentHeight)
            // "ComingSoon" reports top==ascent and bottom==descent: give it some room.
            // Note: It still looks way off with all virtual leading at the top, but that's unavoidable -
            // it **really has** those monster descenders (the 'y') and you gotta put them somewhere.
            return FontMetricsCommon(ascent, descent, ascentDescentHeight * 1.25f, ascentDescentHeight * 0.25f)

        // recommended size bigger than maximum size - O|O - swap inner and outer metrics
        return FontMetricsCommon(top, bottom, ascentDescentHeight, -leading)
    }
}
