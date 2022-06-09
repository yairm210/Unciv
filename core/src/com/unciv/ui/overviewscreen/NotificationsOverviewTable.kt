package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toTextButton
import com.unciv.ui.worldscreen.WorldScreen

class NotificationsOverviewTable(
    val worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    val notificationLog = viewingPlayer.notificationsLog

    private val leftSideTable = Table(BaseScreen.skin)

    private val rightSideTable = Table(BaseScreen.skin)

    val scaleFactor = 0.3f
    val inverseScaleFactor = 1f / scaleFactor
    val maxWidthOfStage = 0.333f
    private val maxEntryWidth = worldScreen.stage.width * maxWidthOfStage * inverseScaleFactor

    val iconSize = 20f

    init {
        val tablePadding = 30f
        defaults().pad(tablePadding).top()

        generateTurnButtons()

        add(leftSideTable)
        add(rightSideTable)
    }

    private fun generateTurnButtons() {
        if (viewingPlayer.notifications.isNotEmpty()) {
            leftSideTable.add(createTurnButton(viewingPlayer.gameInfo.turns.toString() + " (Current)", viewingPlayer.notifications)).row()
        }

        for (index in notificationLog.indices) {
            val invertedIndex = notificationLog.lastIndex - index
            leftSideTable.add(createTurnButton(notificationLog[invertedIndex].turn.toString(), notificationLog[invertedIndex].notifications)).row()
        }
    }

    private fun createTurnButton(text: String, notifications: ArrayList<Notification>): TextButton {
        val btn = text.toTextButton()
        btn.onClick {
            layout()
            notificationsArrayTable(notifications)
        }
        return btn
    }

    private fun notificationsArrayTable(notifications: ArrayList<Notification>) {
        rightSideTable.clear()

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

            rightSideTable.add(notification).pad(5f)
            rightSideTable.padTop(20f).row()
        }
        rightSideTable.padTop(20f).row()
    }
}
