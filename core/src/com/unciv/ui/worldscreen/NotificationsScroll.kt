package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Notification
import com.unciv.ui.utils.*
import kotlin.math.min

class NotificationsScroll(internal val worldScreen: WorldScreen) : ScrollPane(null) {
    private var notificationsTable = Table()

    init {
        actor = notificationsTable.right()
        touchable = Touchable.childrenOnly
    }

    internal fun update(notifications: MutableList<Notification>) {
        notificationsTable.clearChildren()
        for (notification in notifications.toList()) { // tolist to avoid concurrecy problems
            val label = notification.text.toLabel().setFontColor(Color.BLACK)
                    .setFontSize(14)
            val listItem = Table()

            listItem.add(ImageGetter.getCircle()
                    .apply { color=notification.color }).size(10f).pad(5f)
            listItem.background(ImageGetter.getDrawable("OtherIcons/civTableBackground.png"))
            listItem.add(label).pad(5f).padRight(10f)

            // using a large click area with no gap in between each message item.
            // this avoids accidentally clicking in between the messages, resulting in a map click
            val clickArea = Table().apply {
                add(listItem).pad(3f)
                touchable = Touchable.enabled
                onClick {
                    if (notification.locations.isNotEmpty()) {
                        var index = notification.locations.indexOf(worldScreen.tileMapHolder.selectedTile?.position)
                        index = ++index % notification.locations.size // cycle through locations
                        worldScreen.tileMapHolder.setCenterPosition(notification.locations[index])
                    }
                }
            }

            notificationsTable.add(clickArea).right().row()
        }
        notificationsTable.pack()
        pack()
        height = min(notificationsTable.height,worldScreen.stage.height/3)
    }

}
