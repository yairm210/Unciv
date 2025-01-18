package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A widget with a header that when clicked shows/hides a sub-Table.
 *
 * @param title The header text, automatically translated.
 * @param fontSize Size applied to header text (only)
 * @param icon Optional icon - please use [Image][com.badlogic.gdx.scenes.scene2d.ui.Image] or [IconCircleGroup]
 * @param defaultPad Padding between content and wrapper.
 * @param headerPad Default padding for the header Table.
 * @param expanderWidth If set initializes header width
 * @param expanderHeight If set initializes header height
 * @param persistenceID If specified, the ExpanderTab will remember its open/closed state for the duration of one app run
 * @param onChange If specified, this will be called after the visual change for a change in [isOpen] completes (e.g. to react to changed size)
 * @param initContent Optional lambda with [innerTable] as parameter, to help initialize content.
 */
class ExpanderTab(
    val title: String,
    fontSize: Int = Constants.headingFontSize,
    icon: Actor? = null,
    startsOutOpened: Boolean = true,
    defaultPad: Float = 10f,
    headerPad: Float = 10f,
    expanderWidth: Float = 0f,
    expanderHeight: Float = 0f,
    private val persistenceID: String? = null,
    toggleKey: KeyboardBinding = KeyboardBinding.None,
    private val onChange: (() -> Unit)? = null,
    initContent: ((Table) -> Unit)? = null
): Table(BaseScreen.skin) {
    private companion object {
        const val arrowSize = 18f
        const val arrowImage = "OtherIcons/BackArrow"
        val arrowColor = Color(1f,0.96f,0.75f,1f)
        const val animationDuration = 0.2f

        val persistedStates = HashMap<String, Boolean>()
    }

    val header = Table(skin)  // Header with label and icon, touchable to show/hide
    val headerContent = Table()
    private val headerLabel = title.toLabel(fontSize = fontSize, hideIcons = true)
    private val headerIcon = ImageGetter.getImage(arrowImage)
    private val contentWrapper = Table()  // Wrapper for innerTable, this is what will be shown/hidden

    /** The container where the client should add the content to toggle */
    val innerTable = Table()

    /** Indicates whether the contents are currently shown, changing this will animate the widget */
    // This works because a HashMap _could_ store an entry for the null key but we cannot actually store one when declaring as HashMap<String, Boolean>
    var isOpen = persistedStates[persistenceID] ?: startsOutOpened
        private set(value) {
            if (value == field) return
            field = value
            update()
        }

    var isHeaderIconVisible: Boolean
        get() = headerIcon.isVisible
        set(value) { headerIcon.isVisible = value }

    init {
        header.defaults().pad(headerPad)
        if (expanderHeight > 0f)
            header.defaults().height(expanderHeight)
        headerIcon.setSize(arrowSize, arrowSize)
        headerIcon.setOrigin(Align.center)
        headerIcon.rotation = 0f
        headerIcon.color = arrowColor
        header.background(
            BaseScreen.skinStrings.getUiBackground(
                "General/ExpanderTab",
                tintColor = BaseScreen.skinStrings.skinConfig.baseColor
            )
        )
        if (icon != null) header.add(icon)
        header.add(headerLabel)
        header.add(headerContent).growX()
        header.add(headerIcon).size(arrowSize).align(Align.center)
        header.touchable= Touchable.enabled
        header.onActivation { toggle() }
        header.keyShortcuts.add(toggleKey)  // Using the onActivation parameter adds a tooltip, which often does not look too good
        if (expanderWidth != 0f)
            defaults().minWidth(expanderWidth)
        defaults().growX()
        contentWrapper.defaults().growX().pad(defaultPad)
        innerTable.defaults().growX()
        add(header).fill().row()
        add(contentWrapper)
        contentWrapper.add(innerTable)      // update will revert this
        initContent?.invoke(innerTable)
        if (expanderWidth == 0f) {
            // Measure content width incl. pad, set header to same width
            if (innerTable.needsLayout()) contentWrapper.pack()
            getCell(header).minWidth(contentWrapper.width)
        }
        update(noAnimation = true)
    }

    private fun update(noAnimation: Boolean = false) {
        if (persistenceID != null)
            persistedStates[persistenceID] = isOpen
        if (noAnimation || !UncivGame.Current.settings.continuousRendering) {
            contentWrapper.clear()
            if (isOpen) contentWrapper.add(innerTable)
            headerIcon.rotation = if (isOpen) 90f else 0f
            if (!noAnimation) onChange?.invoke()
            return
        }
        val action = object: FloatAction ( 90f, 0f, animationDuration, Interpolation.linear) {
            override fun update(percent: Float) {
                super.update(percent)
                headerIcon.rotation = this.value
                if (this.isComplete) {
                    contentWrapper.clear()
                    if (isOpen) contentWrapper.add(innerTable)
                    onChange?.invoke()
                }
            }
        }.apply { isReverse = isOpen }
        addAction(action)
    }

    /** Toggle [isOpen], animated */
    fun toggle() {
        isOpen = !isOpen

        // In the common case where the expander is hosted in a Table within a ScrollPane...
        // try scrolling our header so it is visible (when toggled by keyboard)
        if (parent is Table && parent.parent is ScrollPane)
            tryAutoScroll(parent.parent as ScrollPane)
        // But - our Actor.addBorder extension can ruin that, so cater for that special case too...
        else if (testForBorderedTable())
            tryAutoScroll(parent.parent.parent as ScrollPane)
    }

    private fun testForBorderedTable(): Boolean {
        if (parent !is Table) return false
        val borderTable = parent.parent as? Table ?: return false
        if (parent.parent.parent !is ScrollPane) return false
        return borderTable.cells.size == 1 && borderTable.background != null && borderTable.padTop == 2f
    }

    private fun tryAutoScroll(scrollPane: ScrollPane) {
        if (scrollPane.isScrollingDisabledY) return

        // As the "opening" is animated, and right now the animation has just started,
        // a scroll-to-visible won't work, so limit it to showing the header for now.
       val heightToShow = header.height

        // Coords as seen by "this" expander relative to parent and as seen by scrollPane may differ by the border size
        // Also make area to show relative to top
        val yToShow = this.y + this.height - heightToShow +
            (if (scrollPane.actor == this.parent) 0f else parent.y)

        // If ever needed - how to check whether scrollTo would not need to scroll (without testing for heightToShow > scrollHeight)
//         val relativeY =  scrollPane.actor.height - yToShow - scrollPane.scrollY
//         if (relativeY >= heightToShow && relativeY <= scrollPane.scrollHeight) return

        // scrollTo does the y axis inversion for us, and also will do nothing if the requested area is already fully visible
        scrollPane.scrollTo(0f, yToShow, header.width, heightToShow)
    }

    /** Change header label text after initialization (does not auto-translate) */
    fun setText(text: String) {
        headerLabel.setText(text)
    }
}
