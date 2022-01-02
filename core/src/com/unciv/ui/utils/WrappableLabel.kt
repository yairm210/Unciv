package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.models.translations.tr
import kotlin.math.min

/** A [Label] that unlike the original participates correctly in layout
 *  Caveat: You still need to turn wrap on _after_ instantiation, doing it here in init leads to hell.
 *
 *  @param text Automatically translated text
 *  @param expectedWidth Upper limit for the preferred width the Label will report
 */
class WrappableLabel(
    text: String,
    private val expectedWidth: Float,
    fontColor: Color = Color.WHITE,
    fontSize: Int = 18
) : Label(text.tr(), BaseScreen.skin) {
    private var _measuredWidth = 0f

    init {
        if (fontColor != Color.WHITE || fontSize!=18) {
            val style = LabelStyle(this.style)
            style.fontColor = fontColor
            if (fontSize != 18) {
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
    override fun getPrefWidth() = min(getMeasuredWidth(), expectedWidth)
    override fun getMaxWidth() = getMeasuredWidth()
}