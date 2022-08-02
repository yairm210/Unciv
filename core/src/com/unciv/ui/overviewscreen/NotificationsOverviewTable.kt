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
import com.unciv.ui.utils.TabbedPager
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen

class NotificationsOverviewTable(
    val worldScreen: WorldScreen,
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class NotificationsTabPersistableData(
            var scrollY: Float? = null
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = scrollY == null
    }
    override val persistableData = (persistedData as? NotificationsTabPersistableData) ?: NotificationsTabPersistableData()
    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (persistableData.scrollY != null)
            pager.setPageScrollY(index, persistableData.scrollY!!)
        super.activated(index, caption, pager)
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        persistableData.scrollY = pager.getPageScrollY(index)
    }

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

        for (notification in notificationLog.asReversed()) {
            notificationTable.add(notificationsArrayTable(notification.turn.toString(), notification.notifications))
            notificationTable.padTop(20f).row()
        }
    }

    private fun notificationsArrayTable(index: String, notifications: ArrayList<Notification>): Table {
        val turnTable = Table(BaseScreen.skin)
        val turnLabel = if (index != "Current")
                            "Turn [$index]".toLabel()
                        else
                            "Current turn".toLabel()
        turnTable.add(turnLabel).row()

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

            turnTable.add(notificationTable).padTop(5f)
            turnTable.padTop(20f).row()
        }
        turnTable.padTop(20f).row()
        return turnTable
    }
}
