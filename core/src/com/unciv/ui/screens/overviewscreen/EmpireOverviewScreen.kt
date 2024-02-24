package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories.EmpireOverviewTabState
import com.unciv.ui.screens.overviewscreen.EmpireOverviewTab.EmpireOverviewTabPersistableData

class EmpireOverviewScreen(
    private var viewingPlayer: Civilization,
    defaultCategory: EmpireOverviewCategories? = null,
    selection: String = ""
) : BaseScreen(), RecreateOnResize {
    // 50 normal button height + 2*10 topTable padding + 2 Separator + 2*5 centerTable padding
    // Since a resize recreates this screen this should be fine as a val
    internal val centerAreaHeight = stage.height - 82f

    private val tabbedPager: TabbedPager
    private val pageObjects = HashMap<EmpireOverviewCategories, EmpireOverviewTab>()

    companion object {
        // This is what keeps per-tab states between overview invocations
        var persistState: Map<EmpireOverviewCategories, EmpireOverviewTabPersistableData>? = null

        private fun updatePersistState(pageObjects: HashMap<EmpireOverviewCategories, EmpireOverviewTab>) {
            persistState = pageObjects.mapValues { it.value.persistableData }.filterNot { it.value.isEmpty() }
        }
    }

    override fun dispose() {
        tabbedPager.selectPage(-1)
        updatePersistState(pageObjects)
        super.dispose()
    }

    init {
        val selectCategory = defaultCategory ?: EmpireOverviewCategories.values().firstOrNull { it.name == game.settings.lastOverviewPage }
        val iconSize = Constants.defaultFontSize.toFloat()

        tabbedPager = TabbedPager(
            stage.width, stage.width,
            centerAreaHeight, centerAreaHeight,
            separatorColor = Color.WHITE,
            capacity = EmpireOverviewCategories.values().size)

        for (category in EmpireOverviewCategories.values()) {
            val tabState = category.testState(viewingPlayer)
            if (tabState == EmpireOverviewTabState.Hidden) continue
            val icon = if (category.iconName.isEmpty()) null else ImageGetter.getImage(category.iconName)
            val pageObject = category.createTab(viewingPlayer, this, persistState?.get(category))
            pageObject.pad(10f, 0f, 10f, 0f)
            pageObjects[category] = pageObject
            val index = tabbedPager.addPage(
                caption = category.name,
                content = pageObject,
                icon, iconSize,
                disabled = tabState != EmpireOverviewTabState.Normal,
                shortcutKey = category.shortcutKey,
                scrollAlign = category.scrollAlign
            )
            if (category == selectCategory) {
                tabbedPager.selectPage(index)
                select(pageObject, selection)
            }
        }

        val closeButton = getCloseButton(50f) { game.popScreen() }
        tabbedPager.decorateHeader(closeButton, leftSide = false, fixed = true)

        tabbedPager.setFillParent(true)
        stage.addActor(tabbedPager)

//         closeButton.setPosition(stage.width - 10f, stage.height - 10f, Align.topRight)
//         stage.addActor(closeButton)
   }

    override fun recreate(): BaseScreen {
        tabbedPager.selectPage(-1)  // trigger deselect on _old_ instance so the tabs can persist their stuff
        updatePersistState(pageObjects)
        return EmpireOverviewScreen(viewingPlayer,
            EmpireOverviewCategories.values().firstOrNull { it.name == game.settings.lastOverviewPage })
    }

    fun resizePage(tab: EmpireOverviewTab) {
        val category = (pageObjects.entries.find { it.value == tab } ?: return).key
        tabbedPager.replacePage(category.name, tab)
    }

    fun select(category: EmpireOverviewCategories, selection: String) {
        tabbedPager.selectPage(category.name)
        select(pageObjects[category], selection)
    }
    private fun select(tab: EmpireOverviewTab?, selection: String) {
        if (tab == null) return
        val scrollY = tab.select(selection) ?: return
        tabbedPager.setPageScrollY(tabbedPager.activePage, scrollY)
    }

    /** Helper to show the world screen with a temporary "one-time" notification */
    // Here because it's common to notification history, resource finder, and city WLTK demanded resource
    internal fun showOneTimeNotification(notification: Notification?) {
        if (notification == null) return  // Convenience - easier than a return@lambda for a caller
        val worldScreen = GUI.getWorldScreen()
        worldScreen.notificationsScroll.oneTimeNotification = notification
        GUI.resetToWorldScreen()
        notification.resetExecuteRoundRobin()
        notification.execute(worldScreen)
    }

    override fun resume() {
        // This is called by UncivGame.popScreen - e.g. after City Tab opened a City and the user closes that CityScreen...
        // Notify the current tab via its IPageExtensions.activated entry point so it can refresh if needed
        val index = tabbedPager.activePage
        val category = EmpireOverviewCategories.values().getOrNull(index - 1) ?: return
        pageObjects[category]?.activated(index, "", tabbedPager) // Fake caption marks this as popScreen-triggered
    }
}
