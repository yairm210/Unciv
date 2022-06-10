package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.worldscreen.WorldScreen

class NotificationsOverviewTable(
    val worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    val notificationLog = viewingPlayer.notificationsLog

    private val notificationTable = Table(BaseScreen.skin)

    val scaleFactor = 0.3f
    val inverseScaleFactor = 1f / scaleFactor
    private val maxWidthOfStage = 0.333f
    private val maxEntryWidth = worldScreen.stage.width * maxWidthOfStage * inverseScaleFactor

    val iconSize = 20f

    init {
        val tablePadding = 30f
        defaults().pad(tablePadding).top()

        generateNotificationTable()

        add(notificationTable)
    }

    private fun generateNotificationTable() {
        if (viewingPlayer.notifications.isNotEmpty())
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
            turnTable.add("Current turn").row()

        for (notification in notifications) {
            val notificationTable = Table(BaseScreen.skin)

            val labelWidth = maxEntryWidth * notification.icons.size - 10f
            val label = WrappableLabel(notification.text, labelWidth, Color.BLACK, 20)

            notificationTable.add(label)
            notificationTable.background = ImageGetter.getRoundedEdgeRectangle()
            notificationTable.touchable = Touchable.enabled
            notificationTable.onClick {
                UncivGame.Current.resetToWorldScreen()
                notification.action?.execute(worldScreen)
            }

            notification.addNotificationIcons(worldScreen.gameInfo.ruleSet, iconSize, notificationTable)

            turnTable.add(notificationTable)
            turnTable.padTop(20f).row()
        }
        turnTable.padTop(20f).row()
        return turnTable
    }
}
