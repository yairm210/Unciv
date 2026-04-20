package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skinStrings
import com.unciv.utils.Concurrency
import com.unciv.utils.delayMillis
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.toGdxArray

class NotificationsOverviewTable(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class NotificationsTabPersistableData(
        var scrollY: Float? = null,
        var lastCount: Int? = null,
        var lastTurn: Int? = null,
        val closedTurns: MutableSet<Int> = mutableSetOf()
    ) : EmpireOverviewTabPersistableData()
    override val persistableData = (persistedData as? NotificationsTabPersistableData) ?: NotificationsTabPersistableData()

    private val notificationLog = viewingPlayer.notificationsLog
    private val stageWidth = overviewScreen.stage.width
    private val notificationWidth = stageWidth / 2
    /** Color for notifications that are new this turn but which we have already seen.
     *  Defaults to 'Eggshell'. */
    private val highlightColor1 = skinStrings.getUIColor("OverviewScreen/NotificationLog/HighlightColor1", Color.valueOf("#f0ead6"))
    /** Color for notifications that are new and unseen this turn
     *  Defaults to HSV(33,40,96) intensified 'Bisque' */
    // Not using NotificationsScroll.oneTimeNotificationColor (#fceea8) here, not enough contrast to highlight 1
    private val highlightColor2 = skinStrings.getUIColor("OverviewScreen/NotificationLog/HighlightColor2", Color.valueOf("#f5c993"))
    private val highlightCount1: Int
    private val highlightCount2: Int
    private val expanders = mutableMapOf<Int, ExpanderTab>()
    private val selectItems = mutableListOf<SelectItem>()
    private var selectWidth = 0f
    private val selectBox: SelectBox<SelectItem>

    companion object {
        private const val iconSize = 20f
        private const val tablePadding = 30f
        private const val currentTurn = "Current turn"
    }

    init {
        defaults().space(tablePadding).top()
        add().row()

        val currentNotificationCount = viewingPlayer.notifications.size
        highlightCount1 = viewingPlayer.notificationCountAtStartTurn ?: currentNotificationCount
        highlightCount2 = persistableData.lastCount.takeIf { persistableData.lastTurn == gameInfo.turns }
            ?: highlightCount1
        generateNotificationTable()
        persistableData.lastCount = currentNotificationCount
        persistableData.lastTurn = gameInfo.turns

        selectBox = getSelectBox()

        add().row()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (persistableData.scrollY != null)
            pager.setPageScrollY(index, persistableData.scrollY!!)
        super.activated(index, caption, pager)
        selectBox.remove()
        selectBox.setPosition(stageWidth - 10f, overviewScreen.centerAreaHeight, Align.topRight)
        selectBox.color.a = 0f
        overviewScreen.stage.addActor(selectBox)
        // `activated` can be called too soon, and `selectBox` ends up at the bottom of z-order
        Concurrency.run {
            delayMillis(10)
            launchOnGLThread {
                selectBox.zIndex = Int.MAX_VALUE
                selectBox.addAction(Actions.fadeIn(0.2f))
            }
        }
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        persistableData.scrollY = pager.getPageScrollY(index)
        persistableData.closedTurns.clear()
        expanders.filterNot { it.value.isOpen }.mapTo(persistableData.closedTurns) { it.key }
        selectBox.remove()
    }

    private fun generateNotificationTable() {
        if (viewingPlayer.notifications.isNotEmpty())
            add(oneTurnTable(gameInfo.turns, viewingPlayer.notifications, doHighlight = true)).row()

        for (turnNotifications in notificationLog.asReversed()) {
            add(oneTurnTable(turnNotifications.turn, turnNotifications.notifications, doHighlight = false)).row()
        }
    }

    private fun oneTurnTable(turn: Int, notifications: List<Notification>, doHighlight: Boolean): Table {
        val open = (doHighlight && highlightCount2 < viewingPlayer.notifications.size) || turn !in persistableData.closedTurns
        val expander = ExpanderTab("", startsOutOpened = open) { turnTable -> // Getting label centered is harder than using headerContent, see below
            for (category in Notification.NotificationCategory.entries) {
                val categoryNotifications = notifications
                    .withIndex().filter { it.value.category == category }
                if (categoryNotifications.isEmpty()) continue

                if (category != NotificationCategory.General)
                    turnTable.add(getCategoryTable(category)).center().padTop(10f).row()

                for ((index, notification) in categoryNotifications) {
                    val notificationTable = getNotificationTable(index, notification, doHighlight)
                    turnTable.add(notificationTable).padTop(5f).row()
                }
            }
        }

        val turnLabel = getTurnLabel(turn)
        expander.headerContent.add(turnLabel).center()
        expanders[turn] = expander
        val selectLabel = if (turn != gameInfo.turns) turnLabel
            else currentTurn.toLabel()
        selectItems += SelectItem(turn, selectLabel.text.toString())
        selectWidth = selectWidth.coerceAtLeast(selectLabel.prefWidth)

        return expander
    }

    private fun getTurnLabel(turn: Int): Label {
        val turnOffset = turn - gameInfo.turns // Yes, 0 or negative
        val yearText = YearTextUtil.toYearText(
            gameInfo.getYear(turnOffset),
            viewingPlayer.isLongCountDisplay()
        )
        val text = (if (turnOffset == 0) "{$currentTurn}\u2004|\u2004" else "") +
            "${Fonts.turn}\u2004{$turn}\u2004|\u2004$yearText" // U+2004: Three-Per-Em Space
        return text.toLabel()
    }

    private fun getCategoryTable(category: Notification.NotificationCategory) = Table().apply {
        val categoryLabel = category.name.toLabel()
        val lineLength = (notificationWidth - 6f - categoryLabel.prefWidth) / 2
        add(ImageGetter.getWhiteDot()).minHeight(2f).width(lineLength)
        add(categoryLabel).pad(3f)
        add(ImageGetter.getWhiteDot()).minHeight(2f).width(lineLength)
    }

    private fun getNotificationTable(index: Int, notification: Notification, doHighlight: Boolean) = Table(BaseScreen.skin).apply {
        val label = ColorMarkupLabel(notification.text, ImageGetter.CHARCOAL, fontSize = 20)
            .apply { wrap = true }
        add(label).width(notificationWidth - iconSize * notification.icons.size)

        val tintColor = when {
            !doHighlight -> null
            index >= highlightCount2 -> highlightColor2
            index >= highlightCount1 -> highlightColor1
            else -> null
        }
        background = skinStrings.getUiBackground("OverviewScreen/NotificationOverviewTable/Notification", skinStrings.roundedEdgeRectangleShape, tintColor)

        touchable = Touchable.enabled
        if (notification.actions.isNotEmpty())
            onClick { overviewScreen.showOneTimeNotification(notification) }

        notification.addNotificationIconsTo(this, gameInfo.ruleset, iconSize)
    }

    private class SelectItem(val turn: Int, val label: String) {
        override fun toString() = label
    }

    private fun getSelectBox() = SelectBox<SelectItem>(skin).apply {
        items = selectItems.toGdxArray()
        onChange {
            val selectTurn = selected.turn
            val selectedExpander = expanders[selectTurn] ?: return@onChange
            for ((turn, expander) in expanders)
                expander.isOpen = turn == selectTurn
            scrollTo(selectTurn, selectedExpander)
        }
        val bgWidth = skin[SelectBox.SelectBoxStyle::class.java].background.run { leftWidth + rightWidth }
        width = selectWidth + bgWidth + 10f // some extra so the text is not glued to the arrow (Gdx fails to respect its width for its layout)
    }

    private fun scrollTo(turn: Int, expander: ExpanderTab) {
        val scroll = parent as? ScrollPane ?: return
        // scrollTo didn't fit - a: Expander now starts animating, layout not final, b: doesn't prioritize upper edge
        // Use the knowledge that all expanders above the target are closed and have the same height as target's header
        val itemHeight = expander.header.height + tablePadding
        scroll.scrollY = expanders.keys.indexOf(turn) * itemHeight
    }
}
