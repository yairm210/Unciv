package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Notification
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.setFontColor

class NotificationsScroll(private val notifications: List<Notification>, internal val worldScreen: WorldScreen) : ScrollPane(null) {
    private var notificationsTable = Table()

    init {
        widget = notificationsTable
    }

    internal fun update() {
        notificationsTable.clearChildren()
        for (notification in notifications) {
            val label = Label(notification.text, CameraStageBaseScreen.skin).setFontColor(Color.BLACK)
            label.setFontScale(1.2f)

            val minitable = Table()

            minitable.add(ImageGetter.getImage("OtherIcons/Circle.png")
                    .apply { color=notification.color }).size(10f).pad(5f)
            minitable.background(ImageGetter.getDrawable("skin/civTableBackground.png"))
            minitable.add(label).pad(3f).padRight(10f)

            if (notification.location != null) {
                minitable.addClickListener {
                    worldScreen.tileMapHolder.setCenterPosition(notification.location!!)
                }
            }

            notificationsTable.add(minitable).pad(3f)
            notificationsTable.row()
        }
        notificationsTable.pack()
        pack()
    }

}