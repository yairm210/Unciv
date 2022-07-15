package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Notification
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.extensions.onClick
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

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
    private var endOfTableSpacerCell: Cell<*>? = null

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
        maxNotificationsHeight: Float,
        tileInfoTableHeight: Float
    ) {

        val previousScrollY = scrollY

        updateContent(notifications)
        updateLayout(maxNotificationsHeight, tileInfoTableHeight)

        scrollY = previousScrollY
        updateVisualScroll()
    }

    private fun updateContent(notifications: MutableList<Notification>) {
        // no news? - keep our list as it is, especially don't reset scroll position
        val newHash = notifications.hashCode()
        if (notificationsHash == newHash) return
        notificationsHash = newHash

        notificationsTable.clearChildren()
        endOfTableSpacerCell = null

        for (notification in notifications.asReversed().toList()) { // toList to avoid concurrency problems
            val listItem = Table()
            listItem.background = ImageGetter.getRoundedEdgeRectangle()

            val labelWidth = maxEntryWidth - iconSize * notification.icons.size - 10f
            val label = WrappableLabel(notification.text, labelWidth, Color.BLACK, 30)
            label.setAlignment(Align.center)
            if (label.prefWidth > labelWidth * scaleFactor) {  // can't explain why the comparison needs scaleFactor
                label.wrap = true
                listItem.add(label).maxWidth(label.optimizePrefWidth()).padRight(10f)
            } else {
                listItem.add(label).padRight(10f)
            }

            notification.addNotificationIcons(worldScreen.gameInfo.ruleSet, iconSize, listItem)

            // using a large click area with no gap in between each message item.
            // this avoids accidentally clicking in between the messages, resulting in a map click
            val clickArea = Table().apply {
                add(listItem).pad(3f)
                touchable = Touchable.enabled
                onClick { notification.action?.execute(worldScreen) }
            }

            notificationsTable.add(clickArea).right().row()
        }

        notificationsTable.pack()  // needed to get height - prefHeight is set and close but not quite the same value
    }

    private fun updateLayout(maxNotificationsHeight: Float, tileInfoTableHeight: Float) {
        val newHeight = min(notificationsTable.height, maxNotificationsHeight * inverseScaleFactor)

        sizeScrollingSpacer(tileInfoTableHeight)

        pack()
        if (height == newHeight) return
        height = newHeight  // after this, maxY is still incorrect until layout()
        invalidateHierarchy()
    }

    /** Add some empty space that can be scrolled under the TileInfoTable which is covering our lower part */
    private fun sizeScrollingSpacer(tileInfoTableHeight: Float) {
        if (endOfTableSpacerCell == null) {
            endOfTableSpacerCell = notificationsTable.add().pad(5f)
            notificationsTable.row()
        }
        val scaledHeight = tileInfoTableHeight * inverseScaleFactor
        endOfTableSpacerCell!!.height(scaledHeight)
        notificationsTable.invalidate() // looks redundant but isn't
        // (the flags it sets are already on when inspected in debugger, but when omitting it the
        // ScrollPane will not properly scroll down to the new maxY when TileInfoTable changes to a smaller height)
    }

    fun setTopRight (right: Float, top: Float) {
        setPosition(right - width * scaleFactor, top - height * scaleFactor)
    }
}
