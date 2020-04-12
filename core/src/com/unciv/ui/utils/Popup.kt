package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.worldscreen.WorldScreen

/**
 * Base class for all Popups, i.e. Tables that get rendered in the middle of a screen and on top of everything else
 */
open class Popup(val screen: CameraStageBaseScreen): Table(CameraStageBaseScreen.skin) {

    private var keyListener: InputListener = getKeyboardListener()
    private val keyPressDispatcher: HashMap<Char, ()->Unit > = hashMapOf()

    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        this.pad(20f)
        this.defaults().pad(5f)

        this.isVisible = false
    }

    private fun getKeyboardListener(): InputListener = object : InputListener() {
        override fun keyTyped(event: InputEvent?, character: Char): Boolean {
            if (character.toLowerCase() !in keyPressDispatcher) return super.keyTyped(event, character)
            keyPressDispatcher[character.toLowerCase()]?.invoke()
            return true
        }
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
        stage.addListener(keyListener)
    }

    open fun close() {
        stage?.removeListener(keyListener)
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
        return addButton(text, null, action)
    }
    fun addButton(text: String, key: Char?, action: () -> Unit): Cell<TextButton> {
        val textTranslated = text.tr()
        val button = TextButton(textTranslated, skin).apply { color = ImageGetter.getBlue() }
        button.onClick(action)
        if (key == null)
            registerKeyHandler (textTranslated, action)
        else
            registerKeyHandler (key, action)
        return add(button).apply { row() }
    }

    fun addSquareButton(text: String, action: () -> Unit): Cell<Table> {
        val textTranslated = text.tr()
        val button = Table()
        button.add(Label(textTranslated,skin))
        button.onClick(action)
        registerKeyHandler(textTranslated,action)
        button.touchable = Touchable.enabled
        return add(button).apply { row() }
    }

    fun addInlineButton(text: String, action: () -> Unit): Cell<TextButton> {
        val textTranslated = text.tr()
        val button = TextButton (textTranslated, skin)
        button.onClick(action)
        registerKeyHandler(textTranslated,action)
        return add(button)
    }

    private fun registerKeyHandler (key: Char?, action: () -> Unit) {
        if (key == null || key.toLowerCase() in keyPressDispatcher) return
        keyPressDispatcher[key.toLowerCase()] = action
    }
    private fun registerKeyHandler (text: String, action: () -> Unit) {
        val key = text.firstOrNull { it.isLetterOrDigit() && it.toLowerCase() !in keyPressDispatcher }
        registerKeyHandler (key, action)
    }

    fun addCloseButton() = addButton("Close", Constants.asciiEscape) { close() }
    fun addEnterAction(action: () -> Unit) { keyPressDispatcher['\r'] = action }
}

fun CameraStageBaseScreen.hasOpenPopups(): Boolean = stage.actors.any { it is Popup && it.isVisible }
fun CameraStageBaseScreen.closeAllPopups() = popups.forEach { it.close() }
fun CameraStageBaseScreen.closeOneVisiblePopup() = popups.lastOrNull { it.isVisible }?.apply { close() }?.name

val CameraStageBaseScreen.popups: List<Popup>
    get() = stage.actors.filterIsInstance<Popup>()

