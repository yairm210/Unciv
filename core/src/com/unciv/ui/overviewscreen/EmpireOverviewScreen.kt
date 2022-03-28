package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.overviewscreen.EmpireOverviewTab.EmpireOverviewTabPersistableData
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.TabbedPager

class EmpireOverviewScreen(
    private var viewingPlayer: CivilizationInfo,
    defaultPage: String = ""
) : BaseScreen() {
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
        val page =
            if (defaultPage != "") {
                game.settings.lastOverviewPage = defaultPage
                defaultPage
            }
            else game.settings.lastOverviewPage
        val iconSize = Constants.defaultFontSize.toFloat()

        onBackButtonClicked { game.setWorldScreen() }

        tabbedPager = TabbedPager(
            stage.width, stage.width,
            centerAreaHeight, centerAreaHeight,
            separatorColor = Color.WHITE,
            keyPressDispatcher = keyPressDispatcher,
            capacity = EmpireOverviewCategories.values().size)

        tabbedPager.addPage(Constants.close) {
            _, _ -> game.setWorldScreen()
        }
        tabbedPager.getPageButton(0).setColor(0.75f, 0.1f, 0.1f, 1f)

        for (category in EmpireOverviewCategories.values()) {
            val tabState = category.stateTester(viewingPlayer)
            if (tabState == EmpireOverviewTabState.Hidden) continue
            val icon = if (category.iconName.isEmpty()) null else ImageGetter.getImage(category.iconName)
            val pageObject = category.factory(viewingPlayer, this, persistState?.get(category))
            pageObject.pad(10f, 0f, 10f, 0f)
            pageObjects[category] = pageObject
            val index = tabbedPager.addPage(
                caption = category.name,
                content = pageObject,
                icon, iconSize,
                disabled = tabState != EmpireOverviewTabState.Normal,
                shortcutKey = category.shortcutKey,
                scrollAlign = category.scrollAlign,
                fixedContent = pageObject.getFixedContent(),
                onDeactivation = { _, _, scrollY -> pageObject.deactivated(scrollY) } 
            ) {
                index, name ->
                val scrollY = pageObject.activated()
                if (scrollY != null) tabbedPager.setPageScrollY(index, scrollY)
                if (name == "Stats")
                    game.settings.addCompletedTutorialTask("See your stats breakdown")
                game.settings.lastOverviewPage = name
            }
            if (category.name == page)
                tabbedPager.selectPage(index)
        }

        tabbedPager.setFillParent(true)
        stage.addActor(tabbedPager)
   }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            updatePersistState(pageObjects)
            game.setScreen(EmpireOverviewScreen(viewingPlayer, game.settings.lastOverviewPage))
            dispose()
        }
    }

    fun resizePage(tab: EmpireOverviewTab) {
        val category = (pageObjects.entries.find { it.value == tab } ?: return).key
        tabbedPager.replacePage(category.name, tab, tab.getFixedContent())
    }
}
