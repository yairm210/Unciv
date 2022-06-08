package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.NotificationsScroll
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

    val maxWidthOfStage = 0.333f

    val iconSize = 20f

    private val maxEntryWidth = worldScreen.stage.width * maxWidthOfStage * inverseScaleFactor

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
            if (notificationLog[turnCounter].notifications.isNotEmpty()) {
                notificationTable.add(notificationsArrayTable(turnCounter.toString(), notificationLog[turnCounter].notifications))
                notificationTable.padTop(20f).row()
            }
        }
    }

    private fun notificationsArrayTable(index: String, notifications: ArrayList<Notification>): Table {
        val turnTable = Table(BaseScreen.skin)

        if (index != "Current")
            turnTable.add("Turn $index").row()
        else
            turnTable.add("$index turn").row()

        for (index2 in notifications.indices) {
            val notification = Table (BaseScreen.skin)

            val labelWidth = maxEntryWidth * notifications[index2].icons.size - 10f
            val label = WrappableLabel(notifications[index2].text, labelWidth, Color.BLACK, 20)

            notification.add(label)
            notification.background = ImageGetter.getRoundedEdgeRectangle()
            notification.touchable = Touchable.enabled
            notification.onClick {
                UncivGame.Current.resetToWorldScreen()
                notifications[index2].action?.execute(worldScreen)
            }

            if (notifications[index2].icons.isNotEmpty()) {
                val ruleset = worldScreen.gameInfo.ruleSet
                for (icon in notifications[index2].icons.reversed()) {
                    val image: Actor = when {
                        ruleset.technologies.containsKey(icon) -> ImageGetter.getTechIcon(icon)
                        ruleset.nations.containsKey(icon) -> ImageGetter.getNationIndicator(ruleset.nations[icon]!!,
                            iconSize
                        )
                        ruleset.units.containsKey(icon) -> ImageGetter.getUnitIcon(icon)
                        else -> ImageGetter.getImage(icon)
                    }
                    notification.add(image).size(iconSize).padRight(5f)
                }
            }

            turnTable.add(notification).pad(5f)
            turnTable.padTop(20f).row()
        }
        turnTable.padTop(20f).row()

        return turnTable
    }
}
