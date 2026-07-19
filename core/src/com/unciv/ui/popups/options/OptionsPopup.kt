package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * The Options (Settings) Popup
 * @param screen The caller - note if this is a [WorldScreen] or [MainMenuScreen] they will be rebuilt when major options change.
 */
//region Fields
class OptionsPopup(
    screen: BaseScreen,
    private val selectPage: OptionsPopupPages = defaultPage,
    withDebug: Boolean = false,
    private val onClose: () -> Unit = {},
    private val subSelect: String? = null
) : Popup(screen.stage, /** [TabbedPager] handles scrolling */ scrollable = Scrollability.None), OptionsPopupHelpers {

    val game = screen.game
    val settings = screen.game.settings
    val tabs: TabbedPager
    private val pageIndex = HashMap<OptionsPopupPages, OptionsPopupTab>()
    override val activePage get() = OptionsPopupPages[tabs.activePage]
    override val rightWidgetMinWidth: Float
    internal val tabMinWidth: Float

    //endregion

    companion object {
        val defaultPage = OptionsPopupPages.Gameplay
    }

    init {
        clickBehindToClose = true

        if (settings.addCompletedTutorialTask("Open the options table"))
            (screen as? WorldScreen)?.shouldUpdate = true

        innerTable.pad(0f)

        val tabMaxWidth: Float
        val tabMaxHeight: Float
        screen.run {
            rightWidgetMinWidth = if (stage.width < 600f) 200f else 240f
            tabMaxWidth = if (isPortrait()) stage.width - 10f else 0.8f * stage.width
            tabMinWidth = 0.6f * stage.width
            tabMaxHeight = 0.8f * stage.height
        }
        // Since all pages now initialize their content late, on activation, we can't measure their preferred size anymore -> use tabMaxHeight for tabMinHeight
        // That's not really bad, the tabs are long enough so some will always need scrolling even on the largest UI size setting.
        tabs = TabbedPager(
            tabMinWidth, tabMaxWidth, tabMaxHeight, tabMaxHeight,
            headerFontSize = 21, backgroundColor = Color.CLEAR, capacity = 8
        )
        add(tabs).pad(0f).grow().row()

        for (page in OptionsPopupPages.entries) {
            if (!page.visible(withDebug)) continue
            val content = page.getContent(this)
            tabs.addPage(page.label, content, page.getIcon(settings.language), 24f)
            pageIndex[page] = content
        }

        tabs.decorateHeader(getCloseButton { close() })

        pack() // Needed to show the background.
        center(screen.stage)
    }

    override fun close() {
        game.musicController.onChange(null)
        center(stageToShowOn)
        tabs.selectPage(-1, false)
        settings.save()
        onClose() // activate the passed 'on close' callback
        super.close()
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) return
        if (tabs.activePage >= 0) return
        pageIndex[selectPage]?.subSelect(subSelect)
        tabs.selectPage(selectPage.ordinal)
    }
}
