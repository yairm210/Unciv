package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skin

internal abstract class OptionsPopupTab(
    private val optionsPopup: OptionsPopup,
) : Table(skin), TabbedPager.IPageExtensions, OptionsPopupHelpers {
    override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth
    val settings by optionsPopup::settings
    val game by optionsPopup::game

    val activePage get() = optionsPopup.tabs.activePage
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
