package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** A widget with a header that when clicked shows/hides a sub-Table. */
class ExpanderTab private constructor(
    title: String,
    fontSize: Int,
    icon: Actor?,
    startsOutOpened: Boolean,
    defaultPad: Float,
    headerPad: Float,
    expanderWidth: Float,
    private val persistenceID: String?,
    maxContentHeight: Float,
    content: Table,
    initContent: ((Table) -> Unit),
    private val onChange: (() -> Unit)?
): Table(BaseScreen.skin) {
    /**
     * A widget with a header that when clicked shows/hides a sub-Table.
     * Alternate constructor that creates an empty [innerTable] and calls [initContent] to let the client fill it.
     *
     * @param title The header text, automatically translated.
     * @param fontSize Size applied to header text (only)
     * @param icon Optional icon - please use [Image] or [IconCircleGroup] and make sure size is set
     * @param defaultPad Padding between content and wrapper.
     * @param headerPad Default padding for the header Table.
     * @param expanderWidth If set initializes header width
     * @param maxContentHeight If set and content measures higher, the wrapper will be a ScrollPane set to this height
     * @param persistenceID If specified, the ExpanderTab will remember its open/closed state for the duration of one app run
     * @param onChange If specified, this will be called after the visual change for a change in [isOpen] completes (e.g. to react to changed size)
     * @param initContent A lambda with [innerTable] as parameter, to help initialize content. Will be `pack()`ed when done!
     */
    constructor(
        title: String,
        fontSize: Int = Constants.headingFontSize,
        icon: Actor? = null,
        startsOutOpened: Boolean = true,
        defaultPad: Float = 10f,
        headerPad: Float = 10f,
        expanderWidth: Float = 0f,
        persistenceID: String? = null,
        maxContentHeight: Float = 0f,
        onChange: (() -> Unit)? = null,
        initContent: ((Table) -> Unit)
    ) : this(title, fontSize, icon, startsOutOpened, defaultPad, headerPad, expanderWidth,
        persistenceID, maxContentHeight, getDefaultInnerTable(), initContent, onChange
    )
    /**
     * A widget with a header that when clicked shows/hides a sub-Table.
     * Constructor that uses a pre-filled content Table.
     *
     * @param title The header text, automatically translated.
     * @param fontSize Size applied to header text (only)
     * @param icon Optional icon - please use [Image] or [IconCircleGroup] and make sure size is set
     * @param defaultPad Padding between content and wrapper.
     * @param headerPad Default padding for the header Table.
     * @param expanderWidth If set initializes header width
     * @param maxContentHeight If set and content measures higher, the wrapper will be a ScrollPane set to this height
     * @param persistenceID If specified, the ExpanderTab will remember its open/closed state for the duration of one app run
     * @param content A Table with final content in place to display in expanded state. Will be `pack()`ed _and measured_ by the constructor!
     * @param onChange If specified, this will be called after the visual change for a change in [isOpen] completes (e.g. to react to changed size)
     */
    constructor(
        title: String,
        fontSize: Int = Constants.headingFontSize,
        icon: Actor? = null,
        startsOutOpened: Boolean = true,
        defaultPad: Float = 10f,
        headerPad: Float = 10f,
        expanderWidth: Float = 0f,
        persistenceID: String? = null,
        maxContentHeight: Float = 0f,
        content: Table,
        onChange: (() -> Unit)? = null
    ) : this(title, fontSize, icon, startsOutOpened, defaultPad, headerPad, expanderWidth,
        persistenceID, maxContentHeight, content, {}, onChange
    )

    private companion object {
        const val arrowSize = 18f
        const val arrowImage = "OtherIcons/BackArrow"
        val arrowColor = Color(1f,0.96f,0.75f,1f)
        const val animationDurationForStageHeight = 0.5f  // also serves as maximum

        const val forceScroll = false

        val persistedStates = HashMap<String, Boolean>()

        fun getDefaultInnerTable() = Table().apply {
            defaults().growX()
        }
    }

    val header = Table(skin)  // Header with label and icon, touchable to show/hide
    private val headerLabel = title.toLabel(fontSize = fontSize)
    private val headerIcon = ImageGetter.getImage(arrowImage)

    private val wrapper: WidgetGroup
    private val wrapperCell: Cell<WidgetGroup>
    private val wrapperWidth: Float
    private val wrapperHeight: Float

    private var currentPercent = 0f
    private val noAnimation = !UncivGame.Current.settings.continuousRendering
    private var savedScrollY = 0f

    /** The container where the client should add the content to toggle */
    val innerTable: Table = content

    /** Indicates whether the contents are currently shown, changing this will animate the widget */
    // This works because a HashMap _could_ store an entry for the null key but we cannot actually store one when declaring as HashMap<String, Boolean>
    var isOpen = persistedStates[persistenceID] ?: startsOutOpened
        private set(value) {
            if (value == field) return
            field = value
            update()
        }

    init {
        header.defaults().pad(headerPad)
        headerIcon.setSize(arrowSize, arrowSize)
        headerIcon.setOrigin(Align.center)
        headerIcon.rotation = 180f
        headerIcon.color = arrowColor
        header.background(
            BaseScreen.skinStrings.getUiBackground(
                "General/ExpanderTab",
                tintColor = BaseScreen.skinStrings.skinConfig.baseColor
            )
        )
        if (icon != null) header.add(icon)
        header.add(headerLabel)
        header.add(headerIcon).size(arrowSize).align(Align.center)
        header.touchable= Touchable.enabled
        header.onClick { toggle() }

        if (expanderWidth != 0f)
            defaults().minWidth(expanderWidth)
        defaults().growX()

        initContent(innerTable)
        innerTable.pack()
        val contentWidth = innerTable.width
        val contentHeight = innerTable.height

        wrapperWidth = if (expanderWidth > 0f && contentWidth > expanderWidth)
            expanderWidth else contentWidth
        wrapperHeight = if (maxContentHeight > 0f && contentHeight > maxContentHeight)
            maxContentHeight else contentHeight

        if (wrapperHeight == 0f)
            // Sorry, can't deal with that
            throw IllegalStateException("ExpanderTab created with empty content")

        wrapper = if (contentHeight > wrapperHeight || contentWidth > wrapperWidth || forceScroll)
                AutoScrollPane(innerTable, skin).apply {
                    setOverscroll(false, false)
                    setScrollingDisabled(true, false)
                }
            else
                Container(innerTable).apply {
                    bottom()  // controls what is seen first on opening!
                }

        add(header).width(wrapperWidth).fillY().row()
        wrapperCell = add(wrapper).maxHeight(wrapperHeight).pad(defaultPad)  // maxHeight only needed for ScrollPane wrapper

        update(fromInit = true)
    }

    override fun getPrefHeight() = header.prefHeight + wrapperHeight * currentPercent

    private fun update(fromInit: Boolean = false) {
        if (persistenceID != null)
            persistedStates[persistenceID] = isOpen

        if (wrapper is ScrollPane && currentPercent == 1f)
            savedScrollY = wrapper.scrollY

        if (noAnimation || fromInit) {
            updateContentVisibility(if (isOpen) 1f else 0f)
            wrapper.isVisible = isOpen
            if (!fromInit) onChange?.invoke()
            return
        }

        clearActions()
        addAction(ExpandAction())
    }

    private fun updateContentVisibility(percent: Float) {
        currentPercent = percent
        val height = percent * wrapperHeight
        wrapperCell.height(height)  // needed for layout
        wrapper.setSize(wrapperWidth, height)  // needed for clipping
        if (wrapper is ScrollPane) {
            // This simulates what Container.bottom() does
            wrapper.scrollY = savedScrollY + (1f - currentPercent) * wrapperHeight
            wrapper.updateVisualScroll()
        }
        headerIcon.rotation = 90f * (2f - percent)
        invalidateHierarchy()
    }

    /** Toggle [isOpen], animated */
    fun toggle() {
        isOpen = !isOpen
    }

    /** Change header label text after initialization - **no** auto-translation! */
    fun setText(text: String) {
        headerLabel.setText(text)
    }

    inner class ExpandAction : FloatAction() {
        init {
            start = currentPercent  // start from wherever we were if turned around midway
            end = if (isOpen) 1f else 0f
            duration = animationDurationForStageHeight *
                (stage?.run { wrapperHeight.coerceAtMost(height) / height } ?: 0.5f) *  // shorter if less content height
                (if (isOpen) 1f - currentPercent else currentPercent)                   // shorter if turned around midway
        }

        override fun begin() {
            super.begin()
            (wrapper as? Container<*>)?.clip(true)
            wrapper.isVisible = true
        }

        override fun update(percent: Float) {
            super.update(percent)
            updateContentVisibility(value)
        }

        override fun end() {
            // super.end() _is_ empty...
            (wrapper as? Container<*>)?.clip(false)
            wrapper.isVisible = isOpen   // allows turning clip off in closed state
            onChange?.invoke()
        }
    }
}
