package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.screens.basescreen.BaseScreen

abstract class EmpireOverviewTab (
    val viewingPlayer: Civilization,
    val overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    /** Abstract container for persistable data
     *  - default class does nothing
     *  - If persistence should end when quitting the game - do not override [isEmpty] and [EmpireOverviewCategories.getPersistDataClass].
     *  - For persistence in GameSettings.json, override **both**.
     */
    open class EmpireOverviewTabPersistableData {
        /** Used by serialization to detect when a default state can be omitted */
        open fun isEmpty() = true
    }
    open val persistableData = persistedData ?: EmpireOverviewTabPersistableData()

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (caption.isEmpty()) return // called from EmpireOverviewScreen.resume()
        overviewScreen.persistState.last = EmpireOverviewCategories.entries.toTypedArray()[index]  // Change this if categories are ever reordered or filtered
    }

    /** Override if the tab can _select_ something specific.
     *  @return non-null to set that tab's ScrollPane.scrollY
     */
    open fun select(selection: String): Float? = null

    val gameInfo = viewingPlayer.gameInfo
}
