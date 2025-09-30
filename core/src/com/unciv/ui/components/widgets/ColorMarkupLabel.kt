@file:Suppress("unused")

package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Colors
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.FontRulesetIcons
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.basescreen.BaseScreen

/** A Label allowing Gdx markup
 *
 *  This constructor does _not_ auto-translate or otherwise preprocess [text]
 *  See also [Color Markup Language](https://libgdx.com/wiki/graphics/2d/fonts/color-markup-language)
 */
class ColorMarkupLabel private constructor(
    fontSize: Int,  // inverted order so it can be differentiated from the translating constructor
    text: String,
) : Label(text, BaseScreen.skin) {

    /** A Label allowing Gdx markup, auto-translated.
     *
     *  Since Gdx markup markers are interpreted and removed by translation, use «» instead.
     *
     *  @param defaultColor the color text starts with - will be converted to markup, not actor tint
     *  @param hideIcons passed to translation to prevent auto-insertion of symbols for gameplay names
     */
    constructor(
        text: String,
        fontSize: Int = Constants.defaultFontSize,
        defaultColor: Color = Color.WHITE,
        hideIcons: Boolean = false
    ) : this(fontSize, mapMarkup(text, defaultColor, hideIcons))

    /** A Label automatically applying Gdx markup colors to symbols and rest of text separately -
     *  _**after**_ translating [text].
     *
     *  Use to easily color text without also coloring the icons which translation inserts as
     *  characters for recognized gameplay names.
     *
     *  @see FontRulesetIcons.charToRulesetImageActor
     */
    constructor(
        text: String,
        textColor: Color,
        symbolColor: Color = Color.WHITE,
        fontSize: Int = Constants.defaultFontSize
    ) : this (fontSize, prepareText(text, textColor, symbolColor))

    /** Only if wrap was turned on, this is the prefWidth before.
     *  Used for getMaxWidth as better estimate than the default 0. */
    private var unwrappedPrefWidth = 0f

    init {
        if (fontSize != Constants.defaultFontSize) {
            val labelStyle = LabelStyle(style) // clone otherwise all default-styled Labels affected
            style = labelStyle
            style.font = Fonts.font
            setFontScale(fontSize / Fonts.ORIGINAL_FONT_SIZE)
        }
    }

    override fun layout() {
        val originalMarkupEnabled = style.font.data.markupEnabled
        style.font.data.markupEnabled = true
        super.layout()
        style.font.data.markupEnabled = originalMarkupEnabled
    }

    override fun computePrefSize(layout: GlyphLayout?) {
        val originalMarkupEnabled = style.font.data.markupEnabled
        style.font.data.markupEnabled = true
        super.computePrefSize(layout)
        style.font.data.markupEnabled = originalMarkupEnabled
    }

    override fun getPrefWidth(): Float {
        if (!wrap) return super.getPrefWidth()
        // Label has a Quirk that together with bad choices in Table become a bug:
        // Label.getPrefWidth will always return 0 if wrap is on, and Table will NOT
        // interpret that as "unknown" like it should but as "I want to be 0 wide".
        super.getPrefHeight()  // Ensure scaleAndComputePrefSize has been run
        // private field prefWidth now has the correct value. However, there is no way
        // to get it because temporarily turning wrap off (to circumvent the bad check)
        // will invalidate which will run scaleAndComputePrefSize again without wrap...
        val field = Label::class.java.getDeclaredField("prefWidth")
        field.isAccessible = true
        val result = field.getFloat(this)
        // That prefWidth we got still might have to be wrapped in some background metrics
        if (style.background == null) return result
        return style.background.run { (result + leftWidth + rightWidth).coerceAtLeast(minWidth) }
    }

    override fun setWrap(wrap: Boolean) {
        if (!this.wrap)
            unwrappedPrefWidth = super.getPrefWidth()
        super.setWrap(wrap)
    }
    override fun getMinWidth() = 48f
    override fun getMaxWidth() = unwrappedPrefWidth  // If unwrapped, we return 0 same as super

    companion object {
        private val inverseColorMap = Colors.getColors().associate { it.value to it.key }
        private fun Color.toMarkup(): String {
            val mapEntry = inverseColorMap[this]
            if (mapEntry != null) return mapEntry
            if (a < 1f) return "#" + toString()
            return "#" + toString().substring(0,6)
        }

        private fun mapMarkup(text: String, defaultColor: Color, hideIcons: Boolean): String {
            val translated = if (defaultColor == Color.WHITE) text.tr(hideIcons)
                else "[${defaultColor.toMarkup()}]${text.tr(hideIcons)}[]"
            if ('«' !in translated) return translated
            return translated.replace('«', '[').replace('»', ']')
        }

        fun prepareText(text: String, textColor: Color, symbolColor: Color): String {
            val translated = text.tr()
            if ((textColor == Color.WHITE && symbolColor == Color.WHITE) || translated.isBlank())
                return translated
            val tc = textColor.toMarkup()
            if (textColor == symbolColor)
                return "[$tc]$translated[]"
            val sc = symbolColor.toMarkup()

            val sb = StringBuilder(translated.length + 42)
            var currentColor = ' '
            for (char in translated) {
                val newColor = if (char in Fonts.allSymbols || char in FontRulesetIcons.charToRulesetImageActor) 'S' else 'T'
                if (newColor != currentColor) {
                    if (currentColor != ' ') sb.append("[]")
                    sb.append('[')
                    sb.append((if (newColor == 'S') sc else tc))
                    sb.append(']')
                    currentColor = newColor
                }
                sb.append(char)
            }
            if (currentColor != ' ') sb.append("[]")
            return sb.toString()
        }
    }
}
