package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.ui.components.ColorMarkupLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.WrappableLabel
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import kotlin.math.min
import com.unciv.ui.components.AutoScrollPane as ScrollPane

class NotificationsScroll(
    private val worldScreen: WorldScreen
) : ScrollPane(null) {
    private companion object {
        /** Scale the entire ScrollPane by this factor */
        const val scaleFactor = 0.5f
        /** Complement of [scaleFactor] because multiplication is cheaper */
        const val inverseScaleFactor = 1f / scaleFactor
        /** Limit width by wrapping labels to this percentage of the stage */
        const val maxWidthOfStage = 0.333f
        /** Logical size of the notification icons - note font size is coded separately */
        const val iconSize = 30f
    }

    private var notificationsHash: Int = 0

    private var notificationsTable = Table()

    private val maxEntryWidth = worldScreen.stage.width * maxWidthOfStage * inverseScaleFactor

    init {
        actor = notificationsTable.right()
        touchable = Touchable.childrenOnly
        setScale(scaleFactor)
    }

    /**
     * Update widget contents if necessary and recalculate layout
     * @param notifications Data to display
     * @param maxNotificationsHeight Total height in world screen coordinates
     * @param tileInfoTableHeight Height of the portion that may be covered on the bottom - make sure we can scroll up far enough so the bottom entry is visible above this
     */
    internal fun update(
        notifications: MutableList<Notification>,
        maxNotificationsHeight: Float
    ) {
        val previousScrollY = scrollY

        updateContent(notifications)
        updateLayout(maxNotificationsHeight)

        scrollY = previousScrollY
        updateVisualScroll()
    }

    private fun updateContent(notifications: MutableList<Notification>) {
        // no news? - keep our list as it is, especially don't reset scroll position
        val newHash = notifications.hashCode()
        if (notificationsHash == newHash) return
        notificationsHash = newHash

        notificationsTable.clearChildren()

        val reversedNotifications = notifications.asReversed().toList() // toList to avoid concurrency problems
        for (category in NotificationCategory.values()){

            val categoryNotifications = reversedNotifications.filter { it.category == category.name }
            if (categoryNotifications.isEmpty()) continue

            val backgroundDrawable = BaseScreen.skinStrings.getUiBackground("WorldScreen/Notification", BaseScreen.skinStrings.roundedEdgeRectangleShape)

            if (category != NotificationCategory.General)
                notificationsTable.add(Table().apply {
                    add(ImageGetter.getWhiteDot()).minHeight(2f).width(worldScreen.stage.width/8)
                    add(Table().apply {
                        background = backgroundDrawable
                        add(ColorMarkupLabel(category.name, Color.BLACK, fontSize = 30))
                    }).pad(3f)
                    add(ImageGetter.getWhiteDot()).minHeight(2f).width(worldScreen.stage.width/8)
                }).row()

            for (notification in categoryNotifications) {
                val listItem = Table()
                listItem.background = backgroundDrawable

                val labelWidth = maxEntryWidth - iconSize * notification.icons.size - 10f
                val label = WrappableLabel(notification.text, labelWidth, Color.BLACK, 30)
                label.setAlignment(Align.center)
                if (label.prefWidth > labelWidth * scaleFactor) {  // can't explain why the comparison needs scaleFactor
                    label.wrap = true
                    listItem.add(label).maxWidth(label.optimizePrefWidth()).padRight(10f)
                } else {
                    listItem.add(label).padRight(10f)
                }

                notification.addNotificationIcons(worldScreen.gameInfo.ruleset, iconSize, listItem)

                // using a large click area with no gap in between each message item.
                // this avoids accidentally clicking in between the messages, resulting in a map click
                val clickArea = Table().apply {
                    add(listItem).pad(3f)
                    touchable = Touchable.enabled
                    onClick { notification.action?.execute(worldScreen) }
                }

                notificationsTable.add(clickArea).right().row()
            }
        }

        notificationsTable.pack()  // needed to get height - prefHeight is set and close but not quite the same value
    }

    private fun updateLayout(maxNotificationsHeight: Float) {
        val newHeight = min(notificationsTable.height, maxNotificationsHeight * inverseScaleFactor)

        pack()
        height = newHeight  // after this, maxY is still incorrect until layout()
        layout()
    }

    fun setTopRight (right: Float, top: Float) {
        setPosition(right - width * scaleFactor, top - height * scaleFactor)
    }
}
