package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.translations.tr

/**
 * Base class for all Popups, i.e. Tables that get rendered in the middle of a screen and on top of everything else
 */
open class Popup(val screen: CameraStageBaseScreen): Table(CameraStageBaseScreen.skin) {
    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        this.pad(20f)
        this.defaults().pad(5f)

        this.isVisible = false
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
        pack()
        center(screen.stage)

    }

    open fun close() {
        remove()
        if (screen.popups.isNotEmpty()) screen.popups[0].isVisible = true
    }

    fun addGoodSizedLabel(text: String, size:Int=18): Cell<Label> {
        val label = text.toLabel(fontSize = size)
        label.setWrap(true)
        label.setAlignment(Align.center)
        return add(label).width(screen.stage.width / 2)
    }

    fun addButton(text: String, action: () -> Unit): Cell<TextButton> {
        val button = text.toTextButton().apply { color = ImageGetter.getBlue() }
        button.onClick(action)
        return add(button).apply { row() }
    }

    fun addSquareButton(text: String, action: () -> Unit): Cell<Table> {
        val button = Table()
        button.add(text.toLabel())
        button.onClick(action)
        button.touchable = Touchable.enabled
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

