package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skin

/**
 *  Common base for the page content Tables.
 *
 *  Provides the [OptionsPopupHelpers] functions and adds some shortcut accessors.
 *
 *  Shortcuts: [settings], [game], [activePage], [selectPage], [replacePage]
 *
 *  Sets table padding to 10 and cell default padding to 5.
 *
 *  TODO: Implementations should NOT keep a reference to [optionsPopup] but move the usecase to a helper here.
 */
internal abstract class OptionsPopupTab(
    internal val optionsPopup: OptionsPopup,
) : Table(skin), OptionsPopupHelpers {
    override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth

    val settings by optionsPopup::settings
    val game by optionsPopup::game

    val activePage get() = optionsPopup.tabs.activePage
    fun selectPage(name: String) = optionsPopup.tabs.selectPage(name)
    fun replacePage(factory: (OptionsPopup) -> OptionsPopupTab) =
        optionsPopup.tabs.replacePage(activePage, factory(optionsPopup))

    init {
        pad(10f)
        defaults().pad(5f)
    }
}
