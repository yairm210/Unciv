package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.abs

/**
 * A widget with a header that when clicked shows/hides a sub-Table.
 *
 * @param title The header text, automatically translated.
 * @param fontSize Size applied to header text (only)
 * @param icon Optional icon - please use [Image] or [IconCircleGroup] and make sure size is set
 * @param startsOutOpened Default initial "open" state if no [persistenceID] set or no persistes state found
 * @param defaultPad Padding between content and wrapper.
 * @param headerPad Default padding for the header Table.
 * @param headerAlign How the header content aligns - use [Align] constants.
 * @param expanderWidth If set initializes cell minWidth and wrapper width
 * @param persistenceID If specified, the ExpanderTab will remember its open/closed state for the duration of one app run
 * @param animated Controls whether opening/closing is animated, defaults to the [continuousRendering][GameSettings.continuousRendering] setting.
 * @param content An [Actor] supporting [Layout] with the content to display in expanded state. Will be `pack()`ed!
 * @param onChange If specified, this will be called on any visual change: repeatedly during animation if enabled, otherwise once after each change to [isOpen]. (e.g. to react to changed size)
 */
class ExpanderTab(
    title: String,
    fontSize: Int = Constants.headingFontSize,
    icon: Actor? = null,
    startsOutOpened: Boolean = true,
    defaultPad: Float = 10f,
    headerPad: Float = 10f,
    headerAlign: Int = Align.center,
    private val expanderWidth: Float = 0f,
    private val persistenceID: String? = null,
    animated: Boolean? = null,
    private val content: WidgetGroup,
    private val onChange: (() -> Unit)? = null
) : Table(BaseScreen.skin) {
    /** Alternate builder-style constructor for an [ExpanderTab]
     *
     * @param initContent A lambda with the future [content] as parameter, to help initialize. Will be `pack()`ed when done!
     */
    constructor(
        title: String,
        fontSize: Int = Constants.headingFontSize,
        icon: Actor? = null,
        startsOutOpened: Boolean = true,
        defaultPad: Float = 10f,
        headerPad: Float = 10f,
        headerAlign: Int = Align.center,
        expanderWidth: Float = 0f,
        persistenceID: String? = null,
        animated: Boolean? = null,
        onChange: (() -> Unit)? = null,
        initContent: ((Table) -> Unit)
    ) : this (
        title, fontSize, icon, startsOutOpened, defaultPad,
        headerPad, headerAlign, expanderWidth, persistenceID, animated,
        Table(BaseScreen.skin).apply {
            defaults().growX()
            initContent(this)
        },
        onChange
    )

    companion object {
        private const val arrowSize = 18f
        private const val arrowImage = "OtherIcons/BackArrow"
        private val arrowColor = Color(1f,0.96f,0.75f,1f)
        private const val animationDurationForStageHeight = 0.5f  // also serves as maximum

        private val persistedStates = HashMap<String, Boolean>()
    }

    // _Please_ don't make header, wrapper or content public. Makes tweaking this widget harder.
    // If more control is needed and the parameter count gets too high, consider using a Style class
    // or open class / protected fun createHeader() or dedicated setters instead.
    private val header = Table(skin)  // Header with label and icon, touchable to show/hide
    private val headerLabel = title.toLabel(fontSize = fontSize)
    private val arrowIcon = ImageGetter.getImage(arrowImage)
    private val headerCell: Cell<Table>

    private val wrapper: Container<WidgetGroup>
    private val wrapperCell: Cell<Container<WidgetGroup>>
    private var wrapperWidth: Float = 0f
    private var wrapperHeight: Float = 0f

    private var currentPercent = 0f
    private val noAnimation = !(animated ?: UncivGame.Current.settings.continuousRendering)

    /** Indicates whether the contents are currently shown, changing this will animate the widget */
    // This works because a HashMap _could_ store an entry for the null key but we cannot actually store one when declaring as HashMap<String, Boolean>
    var isOpen = persistedStates[persistenceID] ?: startsOutOpened
        private set(value) {
            if (value == field) return
            field = value
            update()
        }

    init {
        setLayoutEnabled(false)

        header.align(headerAlign)
        header.defaults().pad(headerPad)
        arrowIcon.setSize(arrowSize, arrowSize)
        arrowIcon.setOrigin(Align.center)
        arrowIcon.rotation = 180f
        arrowIcon.color = arrowColor
        header.background(
            BaseScreen.skinStrings.getUiBackground(
                "General/ExpanderTab",
                tintColor = BaseScreen.skinStrings.skinConfig.baseColor
            )
        )
        if (icon != null) header.add(icon)
        header.add(headerLabel)
        header.add(arrowIcon).size(arrowSize).align(Align.center)
        header.touchable= Touchable.enabled
        header.onClick { toggle() }

        content.pack()
        measureContent()

        wrapper = Container(content).apply {
                setRound(false)
                bottom()  // controls what is seen first on opening!
                setSize(wrapperWidth, 0f)
            }

        defaults().growX()
        headerCell = add(header).minWidth(wrapperWidth)
        row()
        wrapperCell = add(wrapper).size(wrapperWidth, 0f).pad(defaultPad)

        setLayoutEnabled(true)
        update(fromInit = true)
    }

    override fun getPrefHeight() = header.prefHeight + wrapperHeight * currentPercent

    override fun layout() {
        // Critical magic here! Key to allow dynamic content.
        // However, I can't explain why an invalidated header also needs to trigger it. Without, the
        // WorldScreenMusicPopup's expanders, which are width-controlled by their outer cell's fillX/expandX,
        // start aligned and same width, but will slightly misalign by some 10f on opening/closing some of them.
        if (content.needsLayout() || header.needsLayout())
            contentHasChanged()
        super.layout()
    }

    private fun contentHasChanged() {
        val oldWidth = wrapperWidth
        val oldHeight = wrapperHeight
        content.pack()
        measureContent()
        if (wrapperWidth == oldWidth && wrapperHeight == oldHeight) return
        headerCell.minWidth(wrapperWidth)
        currentPercent *= oldHeight / wrapperHeight  // to animate smoothly to new height, >1f should work too
        update()
    }

    private fun measureContent() {
        wrapperWidth = if (expanderWidth > 0f) expanderWidth else content.width
        wrapperHeight = content.height
    }

    private fun update(fromInit: Boolean = false) {
        if (persistenceID != null)
            persistedStates[persistenceID] = isOpen

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
        wrapperCell.size(wrapperWidth, height)  // needed for layout
        wrapper.setSize(wrapperWidth, height)   // needed for clipping
        arrowIcon.rotation = 90f * (2f - percent)
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

    private inner class ExpandAction : FloatAction() {
        init {
            start = currentPercent  // start from wherever we were if turned around midway
            end = if (isOpen) 1f else 0f
            // Duration: shorter if less content height...
            val heightFactor = stage?.run { wrapperHeight.coerceAtMost(height) / height } ?: 0.5f
            // ... and shorter if turned around midway
            val distanceFactor = abs(end - currentPercent)
            duration = (animationDurationForStageHeight * heightFactor)
                .coerceAtLeast(0.15f) * distanceFactor
        }

        override fun begin() {
            super.begin()
            wrapper.clip(true)
            wrapper.isVisible = true
        }

        override fun update(percent: Float) {
            super.update(percent)
            updateContentVisibility(value)
            onChange?.invoke()
        }

        override fun end() {
            wrapper.clip(false)
            wrapper.isVisible = isOpen   // allows turning clip off in closed state
        }
    }
}
