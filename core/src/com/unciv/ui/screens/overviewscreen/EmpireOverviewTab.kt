package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.screens.basescreen.BaseScreen

abstract class EmpireOverviewTab (
    val viewingPlayer: Civilization,
    val overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    open class EmpireOverviewTabPersistableData {
        open fun isEmpty() = true
    }
    open val persistableData = persistedData ?: EmpireOverviewTabPersistableData()

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        overviewScreen.game.settings.lastOverviewPage =
                // shouldn't throw because EmpireOverviewScreen takes the TabbedPager
                // captions directly from EmpireOverviewCategories.name
                EmpireOverviewCategories.valueOf(caption)
    }

    /** Override if the tab can _select_ something specific.
     *  @return non-null to set that tab's ScrollPane.scrollY
     */
    open fun select(selection: String): Float? = null

    val gameInfo = viewingPlayer.gameInfo

}
