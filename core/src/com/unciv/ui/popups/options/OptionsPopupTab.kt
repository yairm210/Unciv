package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skin

/**
 *  Common base for the page content Tables.
 *
 *  Provides the [OptionsPopupHelpers] functions and adds some shortcut accessors.
 *
 *  Shortcuts: [settings], [game], [activePage], [selectPage], [replacePage]
 *
 *  Sets table padding to 10 and cell default padding to 5.
 */
internal abstract class OptionsPopupTab(
    internal val optionsPopup: OptionsPopup,
) : Table(skin), TabbedPager.IPageExtensions, OptionsPopupHelpers {
    override val rightWidgetMinWidth by optionsPopup::rightWidgetMinWidth
    override val activePage get() = optionsPopup.tabs.activePage

    val settings by optionsPopup::settings
    val game by optionsPopup::game

    fun selectPage(name: String) = optionsPopup.tabs.selectPage(name)
    fun replacePage(factory: (OptionsPopup) -> OptionsPopupTab) =
        optionsPopup.tabs.replacePage(activePage, factory(optionsPopup))

    private var isInitialized = false

    init {
        pad(10f)
        defaults().pad(5f)
    }

    open fun lateInitialize() {
        isInitialized = true
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (!isInitialized) lateInitialize()
    }
}
