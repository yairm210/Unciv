package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.unciv.ui.components.UncivTooltip
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class IconCircleButton(
    texturePath: String,
    iconSize: Float,
    circleSize: Float,
    circleColor: Color,
    overColor: Color,
    disabledColor: Color = Color.GRAY
) : IconCircleGroup<ImageButton>(
    circleSize,
    getButton(texturePath, iconSize, overColor, disabledColor),
    false,
    circleColor
) {
    private var tooltip: UncivTooltip<*>? = null

    var isDisabled: Boolean
        get() = actor.isDisabled
        set(value) {
            actor.isDisabled = value
            circle.color.a = if (value) 0.5f else 1f
            saveOrRestoreTooltipListener(value)
        }

    private fun saveOrRestoreTooltipListener(disable: Boolean) {
        val listener = listeners.filterIsInstance<UncivTooltip<*>>().firstOrNull()
        if (disable && listener != null) {
            tooltip = listener
            removeListener(listener)
        } else if (!disable && listener == null && tooltip != null) {
            addListener(tooltip)
        }
    }

    companion object {
        /** Return a "close" button, visually a circle with "x" icon that goes red on mouse-over.
         *
         *  For use e.g. in the top-right corner of screens such as CivilopediaScreen.
         *  Automatically binds the BACK key to the [action].
         */
        fun getCloseButton(
            size: Float = 50f,
            iconSize: Float = size - 20f,
            circleColor: Color = BaseScreen.skinStrings.skinConfig.baseColor,
            overColor: Color = Color.RED,
            action: () -> Unit
        ): IconCircleButton {
            val closeButton = IconCircleButton("OtherIcons/Close", iconSize, size, circleColor, overColor)
            closeButton.onActivation(action)
            closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
            return closeButton
        }

        private fun getButton(texturePath: String, iconSize: Float, overColor: Color, disabledColor: Color): ImageButton {
            val style = ImageButton.ImageButtonStyle()
            val image = ImageGetter.getDrawable(texturePath)
            // ImageButton.ImageButtonStyle(actor.style).apply { imageDisabled = imageUp.tint }
            style.imageUp = image
            style.imageOver = image.tint(overColor)
            style.disabled = image.tint(disabledColor)
            val button = ImageButton(style)
            button.setSize(iconSize, iconSize)
            return button
        }
    }
}
