package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

/**
 * A widget with a header that when clicked shows/hides a sub-Table.
 *
 * @param title The header text, automatically translated.
 * @param fontSize Size applied to header text (only)
 * @param icon Optional icon - please use [Image][com.badlogic.gdx.scenes.scene2d.ui.Image] or [IconCircleGroup]
 * @param defaultPad Padding between content and wrapper.
 * @param headerPad Default padding for the header Table.
 * @param expanderWidth If set initializes header width
 * @param persistenceID If specified, the ExpanderTab will remember its open/closed state for the duration of one app run
 * @param onChange If specified, this will be called after the visual change for a change in [isOpen] completes (e.g. to react to changed size)
 * @param initContent Optional lambda with [innerTable] as parameter, to help initialize content.
 */
class ExpanderTab(
    title: String,
    fontSize: Int = Constants.headingFontSize,
    icon: Actor? = null,
    startsOutOpened: Boolean = true,
    defaultPad: Float = 10f,
    headerPad: Float = 10f,
    expanderWidth: Float = 0f,
    private val persistenceID: String? = null,
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

    private val header = Table(skin)  // Header with label and icon, touchable to show/hide
    private val headerLabel = title.toLabel(fontSize = fontSize)
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

    init {
        header.defaults().pad(headerPad)
        headerIcon.setSize(arrowSize, arrowSize)
        headerIcon.setOrigin(Align.center)
        headerIcon.rotation = 180f
        headerIcon.color = arrowColor
        header.background(ImageGetter.getBackground(ImageGetter.getBlue()))
        if (icon != null) header.add(icon)
        header.add(headerLabel)
        header.add(headerIcon).size(arrowSize).align(Align.center)
        header.touchable= Touchable.enabled
        header.onClick { toggle() }
        if (expanderWidth != 0f)
            defaults().minWidth(expanderWidth)
        defaults().growX()
        contentWrapper.defaults().growX().pad(defaultPad)
        innerTable.defaults().growX()
        add(header).fillY().row()
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
            headerIcon.rotation = if (isOpen) 90f else 180f
            if (!noAnimation) onChange?.invoke()
            return
        }
        val action = object: FloatAction ( 90f, 180f, animationDuration, Interpolation.linear) {
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
    }

    /** Change header label text after initialization */
    fun setText(text: String) {
        headerLabel.setText(text)
    }
}
