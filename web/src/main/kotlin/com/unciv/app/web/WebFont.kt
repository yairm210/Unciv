package com.unciv.app.web

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.UncivGame
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.Log
import org.teavm.jso.JSObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WebFont : FontImplementation {
    private var fontSize = Fonts.ORIGINAL_FONT_SIZE.toInt()
    private var fontFamily = "Arial, Helvetica, sans-serif"
    private var nextCustomFamilyId = 0
    private var activeFontPath: String? = null
    private var activeCustomFont: RegisteredFont? = null
    private lateinit var metrics: FontMetricsCommon

    private data class RegisteredFont(val familyName: String, val face: JSObject)

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        require(size > 0) { "Font size must be positive" }
        val path = fontFamilyData.filePath
        var newFont: RegisteredFont? = null
        val selectedFont = path?.let { filePath ->
            try {
                val bytes = UncivGame.Current.files.getLocalFile(filePath).readBytes()
                RegisteredFont(
                    "UncivModFont${++nextCustomFamilyId}",
                    WebFontRasterizer.registerFont(bytes, "UncivModFont$nextCustomFamilyId")
                ).also { newFont = it }
            } catch (ex: Exception) {
                Log.error("Failed to load mod font $filePath; using the browser sans font", ex)
                null
            }
        }
        val candidateFamily = selectedFont?.let { "'${it.familyName}', Arial, Helvetica, sans-serif" }
            ?: "Arial, Helvetica, sans-serif"

        val candidateMetrics = try {
            val measured = WebFontRasterizer.measureMetrics(size, candidateFamily)
            check(measured.size == 4) { "Canvas font metrics returned ${measured.size} values; expected 4" }
            FontMetricsCommon(
                ascent = measured[0],
                descent = measured[1],
                height = measured[2],
                leading = measured[3],
            ).also { metrics ->
                check(metrics.ascent.isFinite() && metrics.descent.isFinite() &&
                    metrics.height.isFinite() && metrics.leading.isFinite() && metrics.height > 0f
                ) { "Canvas font metrics were invalid: ${measured.contentToString()}" }
            }
        } catch (ex: Exception) {
            newFont?.let { WebFontRasterizer.removeFont(it.face) }
            throw ex
        }

        if (newFont != null || path != activeFontPath || selectedFont == null) {
            activeCustomFont?.let { WebFontRasterizer.removeFont(it.face) }
            activeCustomFont = newFont
            activeFontPath = if (selectedFont == null) null else path
        }
        fontSize = size
        fontFamily = candidateFamily
        metrics = candidateMetrics
    }

    override fun getFontSize(): Int = fontSize

    override fun getCharPixmap(symbolString: String): Pixmap {
        val rasterized = WebFontRasterizer.rasterizeGlyphAligned(
            symbolString,
            fontSize,
            fontFamily,
            metrics.ascent,
            metrics.height,
            metrics.leading,
        )
        check(rasterized.size >= 2) { "Canvas glyph rasterizer returned no dimensions for '$symbolString'" }
        val width = rasterized[0]
        val height = rasterized[1]
        check(width > 0 && height > 0) { "Canvas glyph rasterizer returned invalid dimensions ${width}x$height for '$symbolString'" }
        val expectedLength = 2L + width.toLong() * height.toLong() * 4L
        check(expectedLength == rasterized.size.toLong()) {
            "Canvas glyph rasterizer returned ${rasterized.size} values for ${width}x$height glyph '$symbolString'"
        }
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        try {
            var src = 2
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = rasterized[src++] and 0xFF
                    val g = rasterized[src++] and 0xFF
                    val b = rasterized[src++] and 0xFF
                    val a = rasterized[src++] and 0xFF
                    pixmap.drawPixel(x, y, (r shl 24) or (g shl 16) or (b shl 8) or a)
                }
            }
        } catch (ex: Exception) {
            pixmap.dispose()
            throw ex
        }
        return pixmap
    }

    override fun getSystemFonts(): Sequence<FontFamilyData> = sequenceOf(FontFamilyData.default)

    override fun getModFonts(modsDir: FileHandle): Flow<FontFamilyData> = flow {
        val supportedExtensions = setOf("ttf", "otf")
        val fileSystemTypes = setOf(Files.FileType.Local, Files.FileType.Absolute, Files.FileType.External)
        if (modsDir.type() !in fileSystemTypes || !modsDir.isDirectory) return@flow
        val modsDirName = modsDir.name()
        for (mod in modsDir.list()) {
            if (!mod.isDirectory) continue
            val fontsDir = mod.child("fonts")
            if (!fontsDir.exists() || !fontsDir.isDirectory) continue
            for (font in fontsDir.list()) {
                if (font.isDirectory) continue
                if (font.extension().lowercase() !in supportedExtensions) continue
                emit(FontFamilyData(
                    "${font.nameWithoutExtension()} (${mod.name()})",
                    "$modsDirName/${mod.name()}/fonts/${font.name()}",
                    // Relative to the data folder, as the font loaders resolve it with UncivFiles.getLocalFile
                    "$modsDirName/${mod.name()}/fonts/${font.name()}"
                ))
            }
        }
    }

    override fun getMetrics() = metrics
}
