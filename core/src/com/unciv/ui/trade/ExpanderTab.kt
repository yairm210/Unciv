package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

/**
 * A widget with a header that when clicked shows/hides a sub-Table
 * 
 * Add content to be shown/hidden to [innerTable]!
 * 
 * @param title The header text, automatically translated
 * @param initialOpen Should it start out opened? Defaults to `true`.
 */
class ExpanderTab(
    title: String,
    initialOpen: Boolean = true,
    defaultPad: Float = 10f,
    initContent: ((Table) -> Unit)? = null
): Table(CameraStageBaseScreen.skin) {
    private companion object {
        const val fontSize = 24
        const val arrowSize = 18f
        const val arrowImage = "OtherIcons/BackArrow"
        val arrowColor = Color(1f,0.96f,0.75f,1f)
        const val animationDuration = 0.2f
    }

    private val header = Table(skin)  // Header with label and icon, touchable to show/hide
    private val headerLabel = title.toLabel(fontSize = fontSize)
    private val headerIcon = ImageGetter.getImage(arrowImage)
    private val contentWrapper = Table()  // Wrapper for innerTable, this is what will be shown/hidden

    /** The container where the client should add the content to toggle */
    val innerTable = Table()

    /** Indicates whether the contents are currently shown */
    var isOpen = initialOpen
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
        header.add(headerLabel)
        header.add(headerIcon).size(arrowSize).align(Align.center)
        header.touchable= Touchable.enabled
        header.onClick { toggle() }
        add(header).expandX().fill().row()
        contentWrapper.defaults().pad(defaultPad)
        add(contentWrapper).expandX()
        initContent?.invoke(innerTable)
        update(noAnimation = true)
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

    fun close() {
        isOpen = false
    }
    fun open() {
        isOpen = true
    }
    fun toggle() {
        isOpen = !isOpen
    }
}
