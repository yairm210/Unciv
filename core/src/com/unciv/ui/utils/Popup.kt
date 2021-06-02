package com.unciv.ui.utils

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.Constants

/**
 * Base class for all Popups, i.e. Tables that get rendered in the middle of a screen and on top of everything else
 */
@Suppress("MemberVisibilityCanBePrivate")
open class Popup(val screen: CameraStageBaseScreen): Table(CameraStageBaseScreen.skin) {
    // This exists to differentiate the actual popup (the inner table)
    // from the 'screen blocking' part of the popup (which covers the entire screen)
    val innerTable = Table(CameraStageBaseScreen.skin)

    /** The [KeyPressDispatcher] for the popup - Key handlers from the parent screen are inactive
     * while the popup is active through the [hasOpenPopups][CameraStageBaseScreen.hasOpenPopups] mechanism.
     * @see [KeyPressDispatcher.install]
     */
    val keyPressDispatcher = KeyPressDispatcher(this.javaClass.simpleName)

    init {
        // Set actor name for debugging
        name = javaClass.simpleName

        background = ImageGetter.getBackground(Color.GRAY.cpy().apply { a=.5f })
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))

        innerTable.pad(20f)
        innerTable.defaults().pad(5f)
        super.add(innerTable)

        this.isVisible = false
        touchable = Touchable.enabled // don't allow clicking behind
        this.setFillParent(true)
    }

    /**
     * Displays the Popup on the screen. If another popup is already open, this one will display after the other has
     * closed. Use [force] = true if you want to open this popup above the other one anyway.
     */
    fun open(force: Boolean = false) {
        screen.stage.addActor(this)
        innerTable.pack()
        pack()
        center(screen.stage)
        if (force || !screen.hasOpenPopups()) {
            show()
        }
    }

    /** Subroutine for [open] handles only visibility and [keyPressDispatcher] */
    private fun show() {
        this.isVisible = true
        val currentCount = screen.countOpenPopups()
        // the lambda is for stacked key dispatcher precedence:
        keyPressDispatcher.install(screen.stage) { screen.countOpenPopups() > currentCount }
    }

    /**
     * Close this popup and - if any other popups are pending - display the next one.
     */
    open fun close() {
        keyPressDispatcher.uninstall()
        remove()
        val nextPopup = screen.stage.actors.firstOrNull { it is Popup }
        if (nextPopup != null) (nextPopup as Popup).show()
    }

    /* All additions to the popup are to the inner table - we shouldn't care that there's an inner table at all */
    override fun <T : Actor?> add(actor: T): Cell<T> = innerTable.add(actor)
    override fun row(): Cell<Actor> = innerTable.row()
    fun addSeparator() = innerTable.addSeparator()

    /**
     * Adds a [caption][text] label: A label with word wrap enabled over half the stage width.
     * Will be larger than normal text if the [size] parameter is set to >18f.
     * @param text The caption text.
     * @param size The font size for the label.
     */
    fun addGoodSizedLabel(text: String, size:Int=18): Cell<Label> {
        val label = text.toLabel(fontSize = size)
        label.wrap = true
        label.setAlignment(Align.center)
        return add(label).width(screen.stage.width / 2)
    }

    /**
     * Adds an inline [TextButton].
     * @param text The button's caption.
     * @param key Associate a key with this button's action.
     * @param action A lambda to be executed when the button is clicked.
     * @return The new [Cell]
     */
    fun addButtonInRow(text: String, key: KeyCharAndCode? = null, action: () -> Unit): Cell<TextButton> {
        val button = text.toTextButton().apply { color = ImageGetter.getBlue() }
        button.onClick(action)
        if (key != null) {
            keyPressDispatcher[key] = action
        }
        return add(button)
    }
    fun addButtonInRow(text: String, key: Char, action: () -> Unit)
        = addButtonInRow(text, KeyCharAndCode(key), action)
    fun addButtonInRow(text: String, key: Int, action: () -> Unit)
        = addButtonInRow(text, KeyCharAndCode(key), action)

    /**
     * Adds a [TextButton] and ends the current row.
     * @param text The button's caption.
     * @param key Associate a key with this button's action.
     * @param action A lambda to be executed when the button is clicked.
     * @return The new [Cell]
     */
    fun addButton(text: String, key: KeyCharAndCode? = null, action: () -> Unit)
        = addButtonInRow(text, key, action).apply { row() }
    /** @link [addButton] */
    fun addButton(text: String, key: Char, action: () -> Unit)
        = addButtonInRow(text, key, action).apply { row() }
    fun addButton(text: String, key: Int, action: () -> Unit)
        = addButtonInRow(text, key, action).apply { row() }

    /**
     * Adds a [TextButton] that closes the popup.
     * @param text The button's caption, defaults to "Close".
     * @param additionalKey An additional key that should close the popup, Back and ESC are assigned by default.
     * @param action A lambda to be executed after closing the popup when the button is clicked.
     * @return The new [Cell]
     */
    fun addCloseButton(
        text: String = Constants.close,
        additionalKey: KeyCharAndCode? = null,
        action: (()->Unit)? = null
    ): Cell<TextButton> {
        val closeAction = { close(); if(action!=null) action()  }
        keyPressDispatcher[Input.Keys.BACK] = closeAction
        return addButton(text, additionalKey, closeAction)
    }

    /**
     * Sets or retrieves the [Actor] that currently has keyboard focus.
     *
     * Setting focus on a [TextField] will select all contained text unless a
     * [FocusListener][com.badlogic.gdx.scenes.scene2d.utils.FocusListener] cancels the event.
     */
    var keyboardFocus: Actor?
        get() = screen.stage.keyboardFocus
        set(value) {
            if (screen.stage.setKeyboardFocus(value))
                (value as? TextField)?.selectAll()
        }
}

/**
 * Checks if there are visible [Popup]s.
 * @return `true` if any were found.
 */
fun CameraStageBaseScreen.hasOpenPopups(): Boolean = stage.actors.any { it is Popup && it.isVisible }

/**
 * Counts number of visible[Popup]s.
 * 
 * Used for key dispatcher precedence.
 */
fun CameraStageBaseScreen.countOpenPopups() = stage.actors.count { it is Popup && it.isVisible }

/** Closes all [Popup]s. */
fun CameraStageBaseScreen.closeAllPopups() = popups.forEach { it.close() }

/**
 * Closes the topmost visible [Popup].
 * @return The [name][Popup.name] of the closed [Popup] if any popup was closed and if it had a name.
 */
fun CameraStageBaseScreen.closeOneVisiblePopup() = popups.lastOrNull { it.isVisible }?.apply { close() }?.name

/** @return A [List] of currently active or pending [Popup] screens. */
val CameraStageBaseScreen.popups: List<Popup>
    get() = stage.actors.filterIsInstance<Popup>()

