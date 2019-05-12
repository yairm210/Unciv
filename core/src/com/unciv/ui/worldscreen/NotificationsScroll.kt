package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Notification
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import kotlin.math.min

class NotificationsScroll(internal val worldScreen: WorldScreen) : ScrollPane(null) {
    private var notificationsTable = Table()

    init {
        widget = notificationsTable
    }

    internal fun update(notifications: MutableList<Notification>) {
        notificationsTable.clearChildren()
        for (notification in notifications.toList()) { // tolist to avoid concurrecy problems
            val label = notification.text.toLabel().setFontColor(Color.BLACK)
                    .setFontSize(14)
            val minitable = Table()

            minitable.add(ImageGetter.getCircle()
                    .apply { color=notification.color }).size(10f).pad(5f)
            minitable.background(ImageGetter.getDrawable("OtherIcons/civTableBackground.png"))
            minitable.add(label).pad(5f).padRight(10f)

            if (notification.location != null) {
                minitable.onClick {
                    worldScreen.tileMapHolder.setCenterPosition(notification.location!!)
                }
            }

            notificationsTable.add(minitable).pad(3f)
            notificationsTable.row()
        }
        notificationsTable.pack()
        pack()
        height = min(notificationsTable.height,worldScreen.stage.height/3)
    }

}
