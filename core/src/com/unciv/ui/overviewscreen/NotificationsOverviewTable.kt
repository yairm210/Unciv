package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.WorldScreen

class NotificationsOverviewTable(
    val worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    val notificationLog = viewingPlayer.notificationsLog
    private val notificationTable = Table(BaseScreen.skin)

    init {
        val tablePadding = 30f
        defaults().pad(tablePadding).top()

        generateNotificationTable()

        add(notificationTable)
    }

    private fun generateNotificationTable() {
        notificationTable.add(notificationsArrayTable("Current", viewingPlayer.notifications)).row()

        for (index in notificationLog.indices) {
            val turnCounter = notificationLog.lastIndex - index
            notificationTable.add(notificationsArrayTable(turnCounter.toString(), notificationLog[index].notifications))
            notificationTable.padTop(20f).row()
        }
    }

    private fun notificationsArrayTable(index: String, notifications: ArrayList<Notification>): Table {
        val turnTable = Table(BaseScreen.skin)

        if (index != "Current")
            turnTable.add("Turn $index").row()
        else
            turnTable.add("$index turn").row()

        for (index2 in notifications.indices) {
            turnTable.add(notifications[index2].text)
            turnTable.touchable = Touchable.enabled
            turnTable.onClick {
                UncivGame.Current.resetToWorldScreen()
                notifications[index2].action?.execute(worldScreen)
            }
            turnTable.padTop(20f).row()
        }
        turnTable.padTop(20f).row()

        return turnTable
    }
}
