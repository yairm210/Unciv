package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.floor

/** A [Label] that unlike the original participates correctly in layout
 *
 *  Major feature: Distribute wrapping points so the overall width is minimized without triggering additional breaks.
 *  Caveat: You still need to turn wrap on _after_ instantiation, doing it here in init leads to hell.
 *
 *  @param text Automatically translated text
 *  @param expectedWidth Upper limit for the preferred width the Label will report
 */
class WrappableLabel(
    text: String,
    private val expectedWidth: Float,
    fontColor: Color = Color.WHITE,
    private val fontSize: Int = Constants.defaultFontSize,
    hideIcons: Boolean = false
) : Label(text.tr(hideIcons, hideIcons), BaseScreen.skin) {
    private var _measuredWidth = 0f
    private var optimizedWidth = Float.MAX_VALUE

    init {
        if (fontColor != Color.WHITE || fontSize!=Constants.defaultFontSize) {
            val style = LabelStyle(this.style)
            style.fontColor = fontColor
            if (fontSize != Constants.defaultFontSize) {
                style.font = Fonts.font
                setFontScale(fontSize / Fonts.ORIGINAL_FONT_SIZE)
            }
            setStyle(style)
        }
    }

    override fun setWrap(wrap: Boolean) {
        _measuredWidth = super.getPrefWidth()
        super.setWrap(wrap)
    }

    private fun getMeasuredWidth(): Float = if (wrap) _measuredWidth else super.getPrefWidth()

    override fun getMinWidth() = 48f  // ~ 2 chars
    override fun getPrefWidth() = minOf(getMeasuredWidth(), expectedWidth, optimizedWidth)
    override fun getMaxWidth() = getMeasuredWidth()

    /** If the label can wrap and needs to, try to determine the minimum width that will still wrap
     *  to the least number of lines possible. Return that value, and set as new prefWidth.  */
    fun optimizePrefWidth(): Float {
        if (!wrap) return _measuredWidth

        val labelRows = floor(_measuredWidth / expectedWidth) + 1f
        var optimizedWidth = _measuredWidth / labelRows
        var lineWidth = 0f
        for (word in text.split(Regex("\\b"))) {
            if (word.isEmpty()) continue
            val wordWidth = WrappableLabel(word, Float.MAX_VALUE, fontSize = fontSize).prefWidth
            lineWidth += wordWidth
            if (lineWidth > optimizedWidth) {
                if (word.isNotBlank()) optimizedWidth = lineWidth
                lineWidth = 0f
            }
        }
        this.optimizedWidth = optimizedWidth.coerceAtMost(expectedWidth)
        return optimizedWidth
    }
}
