package com.unciv.ui.images

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.floor
import kotlin.math.ceil

/**
 * Translate a [String] and make a [Button] widget from it, with control over font size, font colour, an optional icon, and custom formatting.
 *
 * @param text Text of the button.
 * @property icon If non-null, [Actor] instance for icon left of the label.
 * @param fontSize Text size for [String.toLabel]. Also used to size the [icon].
 * @param fontColor Text colour for [String.toLabel].
 */
open class IconTextButton(
    text: String,
    val icon: Actor? = null,
    fontSize: Int = Constants.defaultFontSize,
    fontColor: Color = Color.WHITE
): Button(BaseScreen.skin) {
    /** [Label] instance produced by, and with content and formatting as specified in [String.toLabel]. */
    val label = text.toLabel(fontColor, fontSize, hideIcons = true) // Since by definition we already have an icon
    /** Table cell containing the [icon] if any, or `null` (that is, when no [icon] was supplied, the Cell will exist but have no Actor). */
    val iconCell: Cell<Actor> =
        if (icon != null) {
            val size = fontSize.toFloat()
            icon.setSize(size, size)
            icon.setOrigin(Align.center)
            add(icon).size(size).padRight(size / 3.0f)
        } else {
            add().padRight(fontSize / 2f)
        }
    /** Table cell instance containing the [label]. */
    val labelCell: Cell<Label> = add(label)

    init {
        pad(10f)
        val descenderHeight = Fonts.fontImplementation.getMetrics().run {
            descent / height } * fontSize.toFloat()

        // This handles weird rounding situations slightly
        // better for some reason that I don't understand
        labelCell.padTop(9.75f)
        labelCell.padBottom(ceil(descenderHeight));
    }
}
