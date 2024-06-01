package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LoadingImage
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

    private var loadingClickCallback: (MainMenuButton.() -> Unit)? = null

    // Will hold a LoadingImage on demand, and when finished it is freed/nulled
    private var loading: LoadingImage? = null
        set(value) {
            field?.remove()
            if (value != null) {
                value.onClick {
                    loadingClickCallback?.invoke(this)
                    hideLoading()
                }
                value.setPosition(5f, 5f)
                addActor(value)
            }
            field = value
        }

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
            screen.autoUpdater?.cancel()
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

    /** Proxy for [onActivation] that stops background jobs and leaves key binding and tooltip unchanged */
    fun setActivationHandler(function: () -> Unit) {
        clearActivationActions(ActivationTypes.Tap)
        onActivation(ActivationTypes.Tap) {
            screen.stopBackgroundMapGeneration()
            function()
        }
    }

    /** Reverts activation handler to the original one (from constructor) */
    fun revertActivationHandler() = setActivationHandler(function)

    private fun getNewLoadingImage() = run {
        val loadingStyle = LoadingImage.Style(circleColor = Color.DARK_GRAY, loadingColor = Color.FIREBRICK, minShowTime = 250)
        val loading = LoadingImage(36f, loadingStyle)
        this.loading = loading
        loading
    }

    /** @param onClick What to do when the loading indicator is clicked: default hides the indicator, but you can disable that by passing `null`.
     *         To hide and do additional work, simply call [hideLoading] in your handler, `this` [MainMenuButton] is the callback's receiver. */
    fun showLoading(onClick: (MainMenuButton.() -> Unit)? = { hideLoading() }) {
        val loading = this.loading ?: getNewLoadingImage()
        loading.actions.clear()
        loadingClickCallback = onClick
        loading.show()
    }

    fun hideLoading(final: Boolean = true) {
        val actor = loading ?: return
        actor.hide {
            actor.remove()
            if (final) loading = null
        }
    }
}
