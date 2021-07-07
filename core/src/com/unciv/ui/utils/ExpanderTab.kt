package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame

/**
 * A widget with a header that when clicked shows/hides a sub-Table.
 * 
 * @param title The header text, automatically translated.
 * @param fontSize Size applied to header text (only).
 * @param icon Optional icon - please use [Image][com.badlogic.gdx.scenes.scene2d.ui.Image] or [IconCircleGroup].
 * @param defaultPad Padding between content and wrapper. Header padding is currently not modifiable.
 * @param expanderWidth If set initializes header width, otherwise the content width after initContent() is set as header width.
 * @param persistPrefix Additional prefix for the persistence category key (top level into
 *                  [uiSettings.expanderTabStates][com.unciv.ui.utils.UISettings.expanderTabStates]).
 *                  If `null` this ExpanderTab is not persisted.
 * @param initContent Optional lambda with [innerTable] as parameter, to help initialize content.
 */
class ExpanderTab(
    private val title: String,
    fontSize: Int = 24,
    icon: Actor? = null,
    startsOutOpened: Boolean = true,
    defaultPad: Float = 10f,
    expanderWidth: Float = 0f,
    persistPrefix: String? = "",
    initContent: ((Table) -> Unit)? = null
): Table(CameraStageBaseScreen.skin) {
    private companion object {
        const val arrowSize = 18f
        const val arrowImage = "OtherIcons/BackArrow"
        val arrowColor = Color(1f,0.96f,0.75f,1f)
        const val animationDuration = 0.2f
    }

    private val header = Table(skin)  // Header with label and icon, touchable to show/hide
    private val headerLabel = title.toLabel(fontSize = fontSize)
    private val headerIcon = ImageGetter.getImage(arrowImage)
    private val contentWrapper = Table()  // Wrapper for innerTable, this is what will be shown/hidden

    // key under which to persist this ExpanderTab's state, null if persistPrefix is null.
    // Taken from call stack and looks like "CityInfoTable.addCategory"
    // Exists to distinguish e.g. "Wonder" expander in Constructions from "Wonder" expander in City Info
    // methodName included to be safe in case of nested expanders
    // - "Wonder" category containing a modded wonder named "Wonder" can still be distinguished
    private val persistenceContainer: String? =
        if (persistPrefix == null) null else
            Exception().stackTrace.firstOrNull {
                it.className.startsWith("com.unciv.ui.") && !it.className.endsWith(".ExpanderTab")
            }?.let {
                persistPrefix + (if (persistPrefix.isEmpty()) "" else ".") +
                it.className.split('.').last()+ "." + it.methodName
            }

    /** The container where the client should add the content to toggle */
    val innerTable = Table()

    /** Indicates whether the contents are currently shown, changing this will animate the widget */
    var isOpen = getPersistedStartsOpened(startsOutOpened)
        private set(value) {
            if (value == field) return
            field = value
            update()
        }

    init {
        header.defaults().pad(10f)
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

    private fun getPersistedStartsOpened(startsOutOpened: Boolean): Boolean {
        if (persistenceContainer == null) return startsOutOpened
        val states = UncivGame.Current.uiSettings.expanderTabStates[persistenceContainer] ?: return startsOutOpened
        return title !in states
    } 

    private fun update(noAnimation: Boolean = false) {
        if (noAnimation || !UncivGame.Current.settings.continuousRendering) {
            contentWrapper.clear()
            if (isOpen) contentWrapper.add(innerTable)
            headerIcon.rotation = if (isOpen) 90f else 180f
            return
        }
        val action = object: FloatAction ( 90f, 180f, animationDuration, Interpolation.linear) {
            override fun update(percent: Float) {
                super.update(percent)
                headerIcon.rotation = this.value
                if (this.isComplete) {
                    contentWrapper.clear()
                    if (isOpen) contentWrapper.add(innerTable)
                }
            }
        }.apply { isReverse = isOpen }
        addAction(action)
    }

    /** Toggle [isOpen], animated */
    fun toggle() {
        isOpen = !isOpen
    }

    /** store open/closed state to persistence storage */
    fun saveState() {
        if (persistenceContainer == null) return
        val states = UncivGame.Current.uiSettings.expanderTabStates[persistenceContainer] ?: run {
            val newSet = UISettings.ExpanderStateSet()
            UncivGame.Current.uiSettings.expanderTabStates[persistenceContainer!!] = newSet
            newSet
        }
        if (isOpen == (title !in states)) return
        if (isOpen) states.remove(title) else states.add(title)
    }
}
