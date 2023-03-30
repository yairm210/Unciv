@file:Suppress("unused")

package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.screens.basescreen.BaseScreen

/** A Label allowing Gdx markup
 *
 * See also [Color Markup Language](https://libgdx.com/wiki/graphics/2d/fonts/color-markup-language)
 */
class ColorMarkupLabel private constructor(
    fontSize: Int,  // inverted order so it can be differentiated from the translating constructor
    text: String,
) : Label(text, BaseScreen.skin) {

    /** A Label allowing Gdx markup, auto-translated.
     *
     *  Since Gdx markup markers are interpreted and removed by translation, use «» instead.
     */
    constructor(text: String, fontSize: Int = Constants.defaultFontSize)
        : this(fontSize, mapMarkup(text))

    /** A Label automatically applying Gdx markup colors to symbols and rest of text separately
     *  - _after_ translating [text].
     */
    constructor(text: String,
                textColor: Color,
                symbolColor: Color = Color.WHITE,
                fontSize: Int = Constants.defaultFontSize)
        : this (fontSize, prepareText(text, textColor, symbolColor))

    private val originalMarkupEnabled: Boolean

    init {
        if (fontSize != Constants.defaultFontSize) {
            val labelStyle = LabelStyle(style) // clone otherwise all default-styled Labels affected
            style = labelStyle
            style.font = Fonts.font
            setFontScale(fontSize / Fonts.ORIGINAL_FONT_SIZE)
        }
        originalMarkupEnabled = style.font.data.markupEnabled
    }

    override fun layout() {
        style.font.data.markupEnabled = true
        super.layout()
        style.font.data.markupEnabled = originalMarkupEnabled
    }

    companion object {
        private fun mapMarkup(text: String): String {
            val translated = text.tr()
            if ('«' !in translated) return translated
            return translated.replace('«', '[').replace('»', ']')
        }

        private fun Color.toMarkup() = when {
            this == Color.CLEAR -> "CLEAR"
            this == Color.BLACK -> "BLACK"
            this == Color.BLUE -> "BLUE"
            this == Color.CYAN -> "CYAN"
            this == Color.GOLD -> "GOLD"
            this == Color.GRAY -> "GRAY"
            this == Color.GREEN -> "GREEN"
            this == Color.LIME -> "LIME"
            this == Color.NAVY -> "NAVY"
            this == Color.PINK -> "PINK"
            this == Color.RED -> "RED"
            this == Color.SKY -> "SKY"
            this == Color.TAN -> "TAN"
            this == Color.TEAL -> "TEAL"
            this == Color.WHITE -> "WHITE"
            a < 1f -> "#" + toString()
            else -> "#" + toString().substring(0,6)
        }

        private fun prepareText(text: String, textColor: Color, symbolColor: Color): String {
            val translated = text.tr()
            if (textColor == Color.WHITE && symbolColor == Color.WHITE || translated.isBlank())
                return translated
            val tc = textColor.toMarkup()
            if (textColor == symbolColor)
                return "[$tc]$translated[]"
            val sc = symbolColor.toMarkup()

            val sb = StringBuilder(translated.length + 42)
            var currentColor = ' '
            for (char in translated) {
                val newColor = if (char in Fonts.allSymbols) 'S' else 'T'
                if (newColor != currentColor) {
                    if (currentColor != ' ') sb.append("[]")
                    sb.append('[')
                    sb.append((if (newColor == 'S') sc else tc).toString())
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
