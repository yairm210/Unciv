package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Colors
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants

/**
 * Base class for all Popups, i.e. Tables that get rendered in the middle of a screen and on top of everything else
 */
open class Popup(val screen: CameraStageBaseScreen): Table(CameraStageBaseScreen.skin) {
    val innerTable = Table(CameraStageBaseScreen.skin)
    init {
        background = ImageGetter.getBackground(Color.GRAY.cpy().apply { a=.5f })
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        innerTable.pad(20f)
        innerTable.defaults().pad(5f)
        super.add(innerTable)

        this.isVisible = false
        touchable = Touchable.enabled // don't allow clicking behind
        setFillParent(true)
    }

    /**
     * Displays the Popup on the screen. If another popup is already open, this one will display after the other has
     * closed. Use [force] = true if you want to open this popup above the other one anyway.
     */
    fun open(force: Boolean = false) {
        if (force || !screen.hasOpenPopups()) {
            this.isVisible = true
        }

        screen.stage.addActor(this)
        innerTable.pack()
        pack()
        center(screen.stage)
    }

    open fun close() {
        remove()
        val nextPopup = screen.stage.actors.firstOrNull { it is Popup }
        if (nextPopup != null) nextPopup.isVisible = true
    }

    /** All additions to the popup are to the inner table - we shouldn't care that there's an inner table at all */
    override fun <T : Actor?> add(actor: T) = innerTable.add(actor)
    override fun row() = innerTable.row()
    fun addSeparator() = innerTable.addSeparator()

    fun addGoodSizedLabel(text: String, size:Int=18): Cell<Label> {
        val label = text.toLabel(fontSize = size)
        label.wrap = true
        label.setAlignment(Align.center)
        return add(label).width(screen.stage.width / 2)
    }

    fun addButton(text: String, action: () -> Unit): Cell<TextButton> {
        val button = text.toTextButton().apply { color = ImageGetter.getBlue() }
        button.onClick(action)
        return add(button).apply { row() }
    }


    fun addCloseButton(action: (()->Unit)? = null): Cell<TextButton> {
        return if (action==null)
            addButton(Constants.close) { close() }
        else
            addButton(Constants.close) { close(); action() }
    }
}

fun CameraStageBaseScreen.hasOpenPopups(): Boolean = stage.actors.any { it is Popup && it.isVisible }
fun CameraStageBaseScreen.closeAllPopups() = popups.forEach { it.close() }
fun CameraStageBaseScreen.closeOneVisiblePopup() = popups.lastOrNull { it.isVisible }?.apply { close() }?.name

val CameraStageBaseScreen.popups: List<Popup>
    get() = stage.actors.filterIsInstance<Popup>()

