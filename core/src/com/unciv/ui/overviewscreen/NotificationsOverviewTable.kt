package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.WorldScreen

class NotificationsOverviewTable(
    worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    val worldScreen = worldScreen
    val notificationLog = viewingPlayer.notificationsLog
    private val notificationTable = Table(BaseScreen.skin)

    init {
        val tablePadding = 30f  // Padding around each of the stat tables
        defaults().pad(tablePadding).top()

        generateNotificationTable()

        add(notificationTable)
    }

    private fun generateNotificationTable() {
        val thisTurnTable = Table(BaseScreen.skin)
        thisTurnTable.add("This turn").row()
        for (index2 in viewingPlayer.notifications.indices) {
            thisTurnTable.add(viewingPlayer.notifications[index2].text)
            thisTurnTable.touchable = Touchable.enabled
            thisTurnTable.onClick {
                UncivGame.Current.resetToWorldScreen()
                viewingPlayer.notifications[index2].action?.execute(worldScreen)
            }
            thisTurnTable.padTop(20f).row()
        }
        notificationTable.add(thisTurnTable).row()

        for (index in notificationLog.indices) {
            notificationTable.add(notificationsArrayTable(notificationLog.lastIndex - index))
            notificationTable.padTop(20f).row()
        }
    }

    private fun notificationsArrayTable(index: Int): Table {
        val turnTable = Table(BaseScreen.skin)

        turnTable.add("Turn $index").row()
        for (index2 in notificationLog[index].notifications.indices) {
            turnTable.add(notificationLog[index].notifications[index2].text)
            turnTable.touchable = Touchable.enabled
            turnTable.onClick {
                UncivGame.Current.resetToWorldScreen()
                notificationLog[index].notifications[index2].action?.execute(worldScreen)
            }
            turnTable.padTop(20f).row()
        }
        turnTable.padTop(20f).row()
        return turnTable
    }
}
