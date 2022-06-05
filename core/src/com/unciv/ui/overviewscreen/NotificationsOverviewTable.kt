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
    val notificationTable = Table(BaseScreen.skin)
    val notifTurnCounter = viewingPlayer.notifTurnCounter

    init {
        val tablePadding = 30f  // Padding around each of the stat tables
        defaults().pad(tablePadding).top()

        generateNotificationTable()

        add(notificationTable)
    }

    fun generateNotificationTable() {
//         notificationTable.add("This turn").row()
        for (index in notificationLog.indices) {
            notificationTable.add(notificationsTable(notificationLog.lastIndex-index))
            notificationTable.padTop(20f).row()
//             for (index2 in notifTurnCounter.indices) {
//                 if (index == notifTurnCounter[notifTurnCounter.lastIndex-index2]) {
//                     notificationTable.add("Turn $index2").padTop(20f).row()
//                 }
//             }
        }
    }

    fun notificationsTable(index: Int): Table {
        val currentNotification = notificationLog[index]
        val currentNotificationTable = Table(BaseScreen.skin)

        currentNotificationTable.add(currentNotification.text)
        currentNotificationTable.touchable = Touchable.enabled
        currentNotificationTable.onClick {
            UncivGame.Current.resetToWorldScreen()
            currentNotification.action?.execute(worldScreen)
        }

        return currentNotificationTable
    }
}
