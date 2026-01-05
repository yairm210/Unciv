package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class NotificationsOverviewTable(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class NotificationsTabPersistableData(
        var scrollY: Float? = null
    ) : EmpireOverviewTabPersistableData()
    override val persistableData = (persistedData as? NotificationsTabPersistableData) ?: NotificationsTabPersistableData()
    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (persistableData.scrollY != null)
            pager.setPageScrollY(index, persistableData.scrollY!!)
        super.activated(index, caption, pager)
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        persistableData.scrollY = pager.getPageScrollY(index)
    }


    private val stageWidth = overviewScreen.stage.width

    private val notificationLog = viewingPlayer.notificationsLog
    private val notificationTable = Table(BaseScreen.skin)

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
            notificationTable.add(
                notificationsArrayTable(
                    notification.turn.tr(),
                    notification.notifications
                )
            )
            notificationTable.padTop(20f).row()
        }
    }

    private fun notificationsArrayTable(index: String, notifications: ArrayList<Notification>): Table {
        val turnTable = Table(BaseScreen.skin)
        val turnLabel = if (index != "Current")
                            "Turn [$index]".toLabel()
                        else
                            "Current turn".toLabel()
        turnTable.add(Table().apply {
            add(ImageGetter.getWhiteDot()).minHeight(2f).width(stageWidth / 4)
            add(turnLabel).pad(3f)
            add(ImageGetter.getWhiteDot()).minHeight(2f).width(stageWidth / 4)
        }).row()

        for (category in Notification.NotificationCategory.entries) {
            val categoryNotifications = notifications.filter { it.category == category }
            if (categoryNotifications.isEmpty()) continue

            if (category != NotificationCategory.General)
                turnTable.add(category.name.toLabel()).pad(3f).row()

            for (notification in categoryNotifications) {
                val notificationTable = Table(BaseScreen.skin)

                val label = ColorMarkupLabel(notification.text, ImageGetter.CHARCOAL, fontSize = 20)
                    .apply { wrap = true }

                notificationTable.add(label).width(stageWidth / 2 - iconSize * notification.icons.size)
                notificationTable.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/NotificationOverviewTable/Notification", BaseScreen.skinStrings.roundedEdgeRectangleShape)
                notificationTable.touchable = Touchable.enabled
                if (notification.actions.isNotEmpty())
                    notificationTable.onClick { overviewScreen.showOneTimeNotification(notification) }

                notification.addNotificationIconsTo(notificationTable, gameInfo.ruleset, iconSize)

                turnTable.add(notificationTable).padTop(5f)
                turnTable.padTop(20f).row()
            }
        }
        turnTable.padTop(20f).row()
        return turnTable
    }
}
