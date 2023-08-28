package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
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
        overviewScreen.game.settings.lastOverviewPage = caption
    }

    /** Override if the tab can _select_ something specific.
     *  @return non-null to set that tab's ScrollPane.scrollY
     */
    open fun select(selection: String): Float? = null

    val gameInfo = viewingPlayer.gameInfo

    /** Helper to show the world screen with a temporary "one-time" notification */
    // Here because it's common to notification history and resource finder
    internal fun showOneTimeNotification(notification: Notification?) {
        if (notification == null) return  // Convenience - easier than a return@lambda for a caller
        val worldScreen = GUI.getWorldScreen()
        worldScreen.notificationsScroll.oneTimeNotification = notification
        UncivGame.Current.resetToWorldScreen()
        notification.resetExecuteRoundRobin()
        notification.execute(worldScreen)
    }
}
