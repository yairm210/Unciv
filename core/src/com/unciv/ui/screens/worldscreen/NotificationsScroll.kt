package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.ui.components.AutoScrollPane as ScrollPane
import com.unciv.ui.components.ColorMarkupLabel
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import java.lang.ref.WeakReference

/*TODO
 *  Un-hiding the notifications when new ones arrive is a little pointless due to Categories:
 *      * try to scroll new into view? Very complicated as the "old" state is only "available" in the Widgets
 *      * Don't unless a new user option disables categories, then scroll to top?
 *  Idea: Blink or tint the button when new notifications while Hidden
 *  Idea: The little "1" on the bell - remove and draw actual count
 */

class NotificationsScroll(
    private val worldScreen: WorldScreen
) : ScrollPane(null) {
    enum class UserSetting(val static: Boolean = false) { Disabled(true), Hidden, Visible, Permanent(true) }

    private companion object {
        /** Scale the entire ScrollPane by this factor */
        const val scaleFactor = 0.5f
        /** Complement of [scaleFactor] because multiplication is cheaper */
        const val inverseScaleFactor = 1f / scaleFactor
        /** Limit width by wrapping labels to this percentage of the stage */
        const val maxWidthOfStage = 0.333f
        /** Logical size of the notification icons */
        const val iconSize = 30f
        /** Logical font size used in notification and category labels */
        const val fontSize = 30
        /** This is the spacing between categories and also the spacing between the next turn button and the first header */
        const val categoryTopPad = 15f
        /** Spacing between rightmost Label edge and right Screen limit */
        const val rightPadToScreenEdge = 10f
        /** Extra right padding when there's a selection */
        const val selectionExtraRightPad = 12f
        /** Padding within ListItem, included in clickable area, except to the right */
        const val listItemPad = 3f
        /** Top/Bottom padding within ListItem replaces [listItemPad] for the [selectedNotification] */
        const val selectedListItemPad = 15f
        /** Extra spacing between the outer edges of the category header decoration lines and
         *  the left and right edges of the widest notification label - this is the background's
         *  edge radius and looks (subjectively) nice. */
        const val categoryHorizontalPad = 13f
        /** Size of the restore button */
        const val restoreButtonSize = 42f
        /** Distance of restore button to TileInfoTable and screen edge */
        const val restoreButtonPad = 12f
        /** Background tint for [oneTimeNotification] */
        private val oneTimeNotificationColor = Color.valueOf("FCE77E")
    }

    private var notificationsHash: Int = 0

    private var notificationsTable = Table()
    private var topSpacerCell: Cell<Actor?>? = null
    private var bottomSpacerCell: Cell<Actor?>? = null

    private val maxEntryWidth = worldScreen.stage.width * maxWidthOfStage * inverseScaleFactor
    /** For category header decoration lines */
    private val minCategoryLineWidth = worldScreen.stage.width * 0.075f
    /** Show restoreButton when less than this much of the pane is left */
    private val scrolledAwayEpsilon = minCategoryLineWidth

    private val restoreButton = RestoreButton()

    private var userSetting = UserSetting.Visible
    private var userSettingChanged = false

    /** Highlight one notification if the user selects it (and it has an action) */
    private var selectedNotification: Notification? = null
    /** Detect changes in [selectedNotification] */
    private var lastSelectedNotification: WeakReference<Notification>? = null
    /** Display one additional notification once, to show from history in overview */
    var oneTimeNotification: Notification? = null

    init {
        actor = notificationsTable.right()
        touchable = Touchable.childrenOnly
        setOverscroll(false, true)
        setScale(scaleFactor)
        height = worldScreen.stage.height * inverseScaleFactor
    }

    /** Access to hidden "state" - writing it will ensure this is fully visible or hidden and the
     *  restore button shown as needed - with animation. */
    @Suppress("MemberVisibilityCanBePrivate")  // API for future use
    var isHidden: Boolean
        get () = scrollX <= scrolledAwayEpsilon
        set(value) {
            restoreButton.unblock()
            scrollX = if (value) 0f else maxX
        }

    /**
     * Update widget contents if necessary and recalculate layout.
     *
     * The 'covered' parameters are used to make sure we can scroll up or down far enough so the bottom or top entry is visible.
     *
     * @param notifications Data to display
     * @param coveredNotificationsTop Height of the portion that may be covered on the bottom w/o any padding
     * @param coveredNotificationsBottom Height of the portion that may be covered on the bottom w/o any padding
     */
    internal fun update(
        notifications: List<Notification>,
        coveredNotificationsTop: Float,
        coveredNotificationsBottom: Float
    ) {
        if (getUserSettingCheckDisabled()) return

        // Remember scroll position _relative to topRight_
        val previousScrollXinv = when {
            isScrollingDisabledX -> 0f  // switching from Permanent - scrollX and maxX are 0
            userSetting.static -> 0f  // Permanent: fully visible
            notificationsTable.hasChildren() -> maxX - scrollX  // save current scroll
            else -> maxX  // Swiching Hidden to Dynamic - animate "in" only
        }
        val previousScrollY = scrollY

        restoreButton.block()  // For the update, since ScrollPane may layout and change scrollX
        val contentChanged = updateContent(notifications, coveredNotificationsTop, coveredNotificationsBottom)
        if (contentChanged) {
            updateLayout()
        } else {
            updateSpacers(coveredNotificationsTop, coveredNotificationsBottom)
        }

        scrollX = maxX - previousScrollXinv
        scrollY = previousScrollY
        updateVisualScroll()

        applyUserSettingChange()
        if (!userSetting.static) {
            restoreButton.unblock()
            if (contentChanged && !userSettingChanged && isHidden)
                isHidden = false
        }

        // Do the positioning here since WorldScreen may also call update when just its geometry changed
        setPosition(stage.width - width * scaleFactor, 0f)
        restoreButton.setPosition(
            stage.width - restoreButtonPad,
            coveredNotificationsBottom + restoreButtonPad,
            Align.bottomRight)
    }

    private fun updateContent(
        notifications: List<Notification>,
        coveredNotificationsTop: Float,
        coveredNotificationsBottom: Float
    ): Boolean {
        // no news? - keep our list as it is
        if (oneTimeNotification == null && selectedNotification != null)
            oneTimeNotification = selectedNotification
        if (oneTimeNotification in notifications)
            oneTimeNotification = null
        if (oneTimeNotification != null)
            selectedNotification = oneTimeNotification
        val newHash = notifications.hashCode() + oneTimeNotification.hashCode() * 31
        if (notificationsHash == newHash) {
            if (lastSelectedNotification == selectedNotification) return false
            // Only selection changed - rebuild to realize visual selection
            lastSelectedNotification = WeakReference(selectedNotification)
        } else {
            // Content changed - clear selection
            selectedNotification = null
            lastSelectedNotification = null
        }
        notificationsHash = newHash

        notificationsTable.clear()
        notificationsTable.pack()  // forget last width!
        if (notifications.isEmpty() && oneTimeNotification == null) return true

        val categoryHeaders = mutableListOf<CategoryHeader>()
        val itemWidths = mutableListOf<Float>()

        topSpacerCell = notificationsTable.add()
            .height(coveredNotificationsTop * inverseScaleFactor)
        notificationsTable.row()

        val backgroundDrawable = BaseScreen.skinStrings.getUiBackground("WorldScreen/Notification", BaseScreen.skinStrings.roundedEdgeRectangleShape)

        val orderedNotifications = sequence {
                if (oneTimeNotification != null) yield(oneTimeNotification!!)
                yieldAll(notifications.asReversed())
            }.groupBy { NotificationCategory.safeValueOf(it.category) ?: NotificationCategory.General }
            .toSortedMap()  // This sorts by Category ordinal, so far intentional - the order of the grouped lists are unaffected
        for ((category, categoryNotifications) in orderedNotifications) {
            if (category == NotificationCategory.General)
                notificationsTable.add().padTop(categoryTopPad).row()  // Make sure category w/o header gets same spacing
            else {
                val header = CategoryHeader(category, backgroundDrawable)
                categoryHeaders.add(header)
                notificationsTable.add(header).right().row()
            }
            for (notification in categoryNotifications) {
                val item = ListItem(notification, backgroundDrawable)
                itemWidths.add(item.itemWidth)
                notificationsTable.add(item).right().row()
            }
        }

        // I think average looks better than max or non-equalized
        // (Note: if itemWidths ever were empty, average would return Double.NaN!)
        val newHeaderWidth = itemWidths.average().toFloat()
        for (header in categoryHeaders) {
            header.increaseWidthTo(newHeaderWidth)
        }

        bottomSpacerCell = notificationsTable.add()
            .height(coveredNotificationsBottom * inverseScaleFactor).expandY()
        notificationsTable.row()
        selectedNotification = null  // clear selection on next update unless user clicks again
        oneTimeNotification = null
        return true
    }

    private inner class CategoryHeader(
        category: NotificationCategory,
        backgroundDrawable: NinePatchDrawable
    ) : Table() {
        private val leftLineCell: Cell<Image>
        private val rightLineCell: Cell<Image>
        private val captionWidth: Float

        init {
            touchable = Touchable.enabled // stop clicks going through to map
            add().padTop(categoryTopPad).colspan(3).row()
            leftLineCell = add(ImageGetter.getWhiteDot())
                .minHeight(2f).width(minCategoryLineWidth)
            add(Table().apply {
                background = backgroundDrawable
                val label = ColorMarkupLabel(category.name, Color.BLACK, fontSize = fontSize)
                add(label)
                captionWidth = prefWidth  // of this wrapper including background rims
                captionWidth
            }).pad(listItemPad)
            val rightPad = categoryHorizontalPad + rightPadToScreenEdge + (
                    if (selectedNotification == null) 0f
                    else selectionExtraRightPad
                )
            rightLineCell = add(ImageGetter.getWhiteDot())
                .minHeight(2f).width(minCategoryLineWidth)
                .padRight(rightPad)
        }

        /** Equalizes width by adjusting length of the decoration lines.
         *  Does nothing if that would leave the lines shorter than [minCategoryLineWidth].
         *  @param newWidth maximum notification label width including background padding
         */
        fun increaseWidthTo(newWidth: Float) {
            val lineLength = (newWidth - captionWidth - 6f) * 0.5f - categoryHorizontalPad
            if (lineLength <= minCategoryLineWidth) return
            leftLineCell.width(lineLength)
            rightLineCell.width(lineLength)
        }
    }

    private inner class ListItem(
        notification: Notification,
        backgroundDrawable: NinePatchDrawable
    ) : Table() {
        /** Returns width of the visible Notification including background padding but not
         *  including outer touchable area padding */
        val itemWidth: Float

        init {
            val isSelected = notification === selectedNotification  // Notification does not implement equality contract
            val labelFontSize = if (isSelected) fontSize + fontSize/2 else fontSize
            val itemIconSize = if (isSelected) iconSize * 1.5f else iconSize
            val topBottomPad = if (isSelected) selectedListItemPad else listItemPad

            val listItem = Table()
            listItem.background = if (notification !== oneTimeNotification) backgroundDrawable else {
                BaseScreen.skinStrings.getUiBackground("WorldScreen/Notification", BaseScreen.skinStrings.roundedEdgeRectangleShape, oneTimeNotificationColor)
            }

            val maxLabelWidth = maxEntryWidth - (itemIconSize + 5f) * notification.icons.size - 10f
            val label = ColorMarkupLabel(notification.text, Color.BLACK, fontSize= labelFontSize)
            label.width = maxLabelWidth
            label.wrap = true
            label.setAlignment(Align.center)
            listItem.add(label).padRight(10f)

            notification.addNotificationIconsTo(listItem, worldScreen.gameInfo.ruleset, itemIconSize)

            itemWidth = listItem.prefWidth  // includes the background NinePatch's leftWidth+rightWidth

            // using a large click area with no gap in between each message item.
            // this avoids accidentally clicking in between the messages, resulting in a map click
            add(listItem).pad(topBottomPad, listItemPad, topBottomPad, rightPadToScreenEdge)
            touchable = Touchable.enabled
            if (notification.action != null) {
                onClick {
                    notification.action?.execute(worldScreen)
                    selectedNotification = notification
                    GUI.setUpdateWorldOnNextRender()
                }
            }
        }
    }

    /** Should only be called when updateContent just rebuilt the notificationsTable */
    private fun updateLayout() {
        // size this ScrollPane to content
        width = notificationsTable.packIfNeeded().width

        // Allow scrolling content out of the screen to the right
        topSpacerCell?.width(2 * width)
        notificationsTable.invalidate() // topSpacerCell.width _should_ have done that
        notificationsTable.pack()  // again so new topSpacerCell size is taken into account

        layout()  // This calculates maxXY so setScrollXY clamps correctly
    }

    private fun updateSpacers(coveredNotificationsTop: Float, coveredNotificationsBottom: Float) {
        topSpacerCell?.height(coveredNotificationsTop * inverseScaleFactor)
        bottomSpacerCell?.height(coveredNotificationsBottom * inverseScaleFactor)
        layout()
    }

    override fun scrollX(pixelsX: Float) {
        super.scrollX(pixelsX)
        if (maxX < 5f) return
        restoreButton.checkScrollX(pixelsX)
    }

    inner class RestoreButton : Container<IconCircleGroup>() {
        private var blockCheck = true
        private var blockAct = true
        private var active = false

        init {
            actor = ImageGetter.getImage("OtherIcons/Notifications")
                .surroundWithCircle(restoreButtonSize * 0.9f, color = BaseScreen.skinStrings.skinConfig.baseColor)
                .surroundWithCircle(restoreButtonSize, resizeActor = false)
            size(restoreButtonSize)
            color = color.cpy()  // So we don't mutate a skin element while fading
            color.a = 0f  // for first fade-in
            onClick {
                scrollX = maxX
                hide()
            }
            pack()  // `this` needs to adopt the size of `actor`, won't happen automatically (surprisingly)
        }

        fun block() {
            blockCheck = true
            blockAct = true
        }
        fun unblock() {
            blockCheck = false
            blockAct = false
        }

        fun show() {
            active = true
            clearActions()
            updateUserSetting(UserSetting.Hidden)
            if (parent == null)
                // `addActorAfter` to stay behind any popups
                worldScreen.stage.root.addActorAfter(this@NotificationsScroll, this)
            blockAct = false
            addAction(Actions.fadeIn(1f))
        }

        fun hide() {
            active = false
            clearActions()
            updateUserSetting(UserSetting.Visible)
            if (parent == null) return
            blockAct = false
            addAction(
                Actions.sequence(
                    Actions.fadeOut(0.333f),
                    Actions.run {
                        remove()
                    }
                )
            )
        }

        fun checkScrollX(scrollX: Float) {
            if (blockCheck) return
            if (active && scrollX >= scrolledAwayEpsilon * 2)
                hide()
            if (!active && scrollX <= scrolledAwayEpsilon)
                show()
        }

        override fun act(delta: Float) {
            // Actions are blocked while update() is rebuilding the UI elements - to be safe from unexpected state changes
            if (!blockAct) super.act(delta)
        }
    }

    private fun getUserSettingCheckDisabled(): Boolean {
        val settingString = GUI.getSettings().notificationScroll
        val setting = UserSetting.values().firstOrNull { it.name == settingString }
            ?: UserSetting.Visible
        userSettingChanged = false
        if (setting == userSetting)
            return setting == UserSetting.Disabled

        userSetting = setting
        userSettingChanged = true
        if (setting != UserSetting.Disabled) return false

        notificationsTable.clear()
        notificationsHash = 0
        scrollX = 0f
        updateVisualScroll()
        setScrollingDisabled(false, false)
        isVisible = false
        restoreButton.hide()
        return true
    }

    private fun applyUserSettingChange() {
        if (!userSettingChanged) return
        // Here the rebuild of content and restoring of scrollX/Y already happened
        restoreButton.block()
        val fromPermanent = isScrollingDisabledX
        setScrollingDisabled(userSetting.static, false)
        if (fromPermanent) {
            validate()
            scrollX = maxX
            updateVisualScroll()
        }
        when (userSetting) {
            UserSetting.Hidden -> {
                if (!isHidden) isHidden = true
                restoreButton.show()
            }
            UserSetting.Visible -> {
                if (isHidden) isHidden = false
                restoreButton.hide()
            }
            UserSetting.Permanent -> {
                restoreButton.hide()
            }
            else -> return
        }
        isVisible = true
    }

    private fun updateUserSetting(newSetting: UserSetting) {
        if (newSetting == userSetting || userSetting.static) return
        userSetting = newSetting
        GUI.getSettings().notificationScroll = newSetting.name
    }
}
