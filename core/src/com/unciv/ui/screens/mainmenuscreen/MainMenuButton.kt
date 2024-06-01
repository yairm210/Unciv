package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** Create one **Main Menu Button** including onClick/key binding.
 *  - Note this does **not** inherit `Button`, so there's no mouse hover style, and disabling is hardcoded by coloring the element widgets.
 *  @param screen    The [MainMenuScreen] - used to cancel background jobs on activation
 *  @param text      The text to display on the button
 *  @param iconName  The path of the icon to display on the button
 *  @param binding   keyboard binding
 *  @param function  Action to invoke when the button is activated
 */
internal class MainMenuButton(
    private val screen: MainMenuScreen,
    text: String,
    iconName: String,
    private val binding: KeyboardBinding,
    private val function: () -> Unit
): Table() {
    private val icon: Image
    private val label: Label

    private companion object {
        val disabledColor = Color(0x606060ff)
    }

    init {
        pad(15f, 30f, 15f, 30f)
        background = BaseScreen.skinStrings.getUiBackground(
            "MainMenuScreen/MenuButton",
            BaseScreen.skinStrings.roundedEdgeRectangleShape,
            BaseScreen.skinStrings.skinConfig.baseColor
        )

        icon = ImageGetter.getImage(iconName)
        add(icon).size(50f).padRight(20f)
        label = text.toLabel(fontSize = 30, alignment = Align.left)
        add(label).expand().left().minWidth(200f)

        touchable = Touchable.enabled
        onActivation(binding = binding) {
            screen.stopBackgroundMapGeneration()
            function()
        }

        pack()
    }

    fun setText(text: String) {
        label.setText(text.tr())
    }

    fun enable() {
        icon.color = Color.WHITE
        label.color = Color.WHITE
        touchable = Touchable.enabled
    }

    fun disable() {
        icon.color = disabledColor
        label.color = disabledColor
        touchable = Touchable.disabled
    }
}
