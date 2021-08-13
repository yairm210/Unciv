package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Notification
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class NotificationsScroll(internal val worldScreen: WorldScreen) : ScrollPane(null) {

    var notificationsHash: Int = 0

    private var notificationsTable = Table()

    init {
        actor = notificationsTable.right()
        touchable = Touchable.childrenOnly
        setScale(.5f)
    }

    internal fun update(notifications: MutableList<Notification>) {

        // no news? - keep our list as it is, especially don't reset scroll position
        if (notificationsHash == notifications.hashCode())
            return
        notificationsHash = notifications.hashCode()

        notificationsTable.clearChildren()
        for (notification in notifications.toList().reversed()) { // toList to avoid concurrency problems
            val label = notification.text.toLabel(Color.BLACK, 30)
            val listItem = Table()

            val iconSize = 30f
            if (notification.icons.isNotEmpty()) {
                val ruleset = worldScreen.gameInfo.ruleSet
                for (icon in notification.icons) {
                    val image: Actor = when {
                        ruleset.technologies.containsKey(icon) -> ImageGetter.getTechIcon(icon)
                        ruleset.nations.containsKey(icon) -> ImageGetter.getNationIndicator(ruleset.nations[icon]!!, iconSize)
                        ruleset.units.containsKey(icon) -> ImageGetter.getUnitIcon(icon)
                        else -> ImageGetter.getImage(icon)
                    }
                    listItem.add(image).size(iconSize).padRight(5f)
                }
            }
            listItem.background = ImageGetter.getRoundedEdgeRectangle()
            listItem.add(label)

            // using a large click area with no gap in between each message item.
            // this avoids accidentally clicking in between the messages, resulting in a map click
            val clickArea = Table().apply {
                add(listItem).pad(3f)
                touchable = Touchable.enabled
                onClick { notification.action?.execute(worldScreen) }
            }

            notificationsTable.add(clickArea).right().row()
        }
        notificationsTable.pack()
        pack()
        height = min(notificationsTable.height, worldScreen.stage.height * 2 / 3 - 15f)
    }

}