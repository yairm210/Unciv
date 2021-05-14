package com.unciv.ui.utils

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import java.util.HashMap

/*
 * For now, combination keys cannot easily be expressed.
 * Pressing Ctrl-Letter will arrive one event for Input.Keys.CONTROL_LEFT and one for the ASCII control code point
 *      so Ctrl-R can be handled using KeyCharAndCode('\u0012')
 * Pressing Alt-Something likewise will fire once for Alt and once for the unmodified keys with no indication Alt is held
 *      (Exception: international keyboard AltGr-combos)
 * An update supporting easy declarations for any modifier combos would need to use Gdx.input.isKeyPressed()
 * Gdx seems to omit support for a modifier mask (e.g. Ctrl-Alt-Shift) so we would need to reinvent this
 */

/**
 * Represents a key for use in an InputListener keyTyped() handler
 *
 * Example: KeyCharAndCode('R'), KeyCharAndCode(Input.Keys.F1)
 */
data class KeyCharAndCode(val char: Char, val code: Int) {
    // express keys with a Char value
    constructor(char: Char): this(char.toLowerCase(), 0)
    // express keys that only have a keyCode like F1
    constructor(code: Int): this(Char.MIN_VALUE, code)
    // helper for use in InputListener keyTyped()
    constructor(event: InputEvent?, character: Char)
            : this (
                character.toLowerCase(),
                if (character == Char.MIN_VALUE && event!=null) event.keyCode else 0
            )

    // From Kotlin 1.5 on the Ctrl- line will need Char(char.code+64)
    // see https://github.com/Kotlin/KEEP/blob/master/proposals/stdlib/char-int-conversions.md
    override fun toString(): String {
        // debug helper
        return when {
            char == Char.MIN_VALUE -> Input.Keys.toString(code)
            char < ' ' -> "Ctrl-" + (char.toInt()+64).toChar()
            else -> "\"$char\""
        }
    }
}


/** A manager for a [keyTyped][InputListener.keyTyped] [InputListener], based on [HashMap].
 *  Uses [KeyCharAndCode] as keys to express bindings for both Ascii and function keys.
 *
 *  [install] and [uninstall] handle adding the listener to a [Stage].
 *  Use indexed assignments to react to specific keys, e.g.:
 *  ```
 *      keyPressDispatcher[Input.Keys.F1] = { showHelp() }
 *      keyPressDispatcher['+'] = { zoomIn() }
 *  ```
 *  Optionally use [setCheckpoint] and [revertToCheckPoint] to remember and restore one state.
 */
class KeyPressDispatcher: HashMap<KeyCharAndCode, (() -> Unit)>() {
    private var checkpoint: Set<KeyCharAndCode> = setOf()       // set of keys marking a checkpoint
    private var listener: EventListener? = null     // holds listener code, captures its params in install() function
    private var listenerInstalled = false           // flag for lazy Stage.addListener()
    private var installStage: Stage? = null         // Keep stage passed by install() for lazy addListener and uninstall
    var name: String? = null                        // optional debug label
        private set

    // access by Char
    operator fun get(char: Char) = this[KeyCharAndCode(char)]
    operator fun set(char: Char, action: () -> Unit) {
        this[KeyCharAndCode(char)] = action
    }
    operator fun contains(char: Char) = this.contains(KeyCharAndCode(char))
    fun remove(char: Char) = this.remove(KeyCharAndCode(char))

    // access by Int keyCodes
    operator fun get(code: Int) = this[KeyCharAndCode(code)]
    operator fun set(code: Int, action: () -> Unit) {
        this[KeyCharAndCode(code)] = action
    }
    operator fun contains(code: Int) = this.contains(KeyCharAndCode(code))
    fun remove(code: Int) = this.remove(KeyCharAndCode(code))

    // access by KeyCharAndCode
    operator fun set(key: KeyCharAndCode, action: () -> Unit) {
        super.put(key, action)
        // On Android the Enter key will fire with Ascii code `Linefeed`, on desktop as `Carriage Return`
        if (key == KeyCharAndCode('\r'))
            super.put(KeyCharAndCode('\n'), action)
        // Likewise always match Back to ESC
        if (key == KeyCharAndCode(Input.Keys.BACK))
            super.put(KeyCharAndCode('\u001B'), action)
        checkInstall()
    }
    override fun remove(key: KeyCharAndCode): (() -> Unit)? {
        val result = super.remove(key)
        if (key == KeyCharAndCode('\r'))
            super.remove(KeyCharAndCode('\n'))
        if (key == KeyCharAndCode(Input.Keys.BACK))
            super.remove(KeyCharAndCode('\u001B'))
        checkInstall()
        return result
    }

    override fun toString(): String {
        return (if (name==null) "" else "$name.") +
            "KeyPressDispatcher(" + keys.joinToString(limit = 6){ it.toString() } + ")"
    }

    /** Removes all of the mappings, including a checkpoint if set. */
    override fun clear() {
        checkpoint = setOf()
        super.clear()
        checkInstall()
    }

    /** Set a checkpoint: The current set of keys will not be removed on a subsequent [revertToCheckPoint] */
    fun setCheckpoint() {
        checkpoint = keys.toSet()
    }
    /** Revert to a checkpoint: Remove all mappings except those that existed on a previous [setCheckpoint] call.
     * If no checkpoint has been set, this is equivalent to [clear] */
    fun revertToCheckPoint() {
        keys.minus(checkpoint).forEach { remove(it) }
        checkInstall()
    }

    /** install our [EventListener] on a stage with optional inhibitor
     * @param   stage The [Stage] to add the listener to
     * @param   checkIgnoreKeys An optional lambda - when it returns true all keys are ignored
     */
    fun install(stage: Stage, name: String? = null, checkIgnoreKeys: (() -> Boolean)? = null) {
        this.name = name
        if (installStage != null) uninstall()
        listener =
            object : InputListener() {
                override fun keyTyped(event: InputEvent?, character: Char): Boolean {
                    val key = KeyCharAndCode(event, character)

                    // see if we want to handle this key, and if not, let it propagate
                    if (!contains(key) || (checkIgnoreKeys?.invoke() == true))
                        return super.keyTyped(event, character)

                    //try-catch mainly for debugging. Breakpoints in the vicinity can make the event fire twice in rapid succession, second time the context can be invalid
                    try {
                        this@KeyPressDispatcher[key]?.invoke()
                    } catch (ex: Exception) {}
                    return true
                }
            }
        installStage = stage
        checkInstall()
    }

    /** uninstall our [EventListener] from the stage it was installed on. */
    fun uninstall() {
        checkInstall(forceRemove = true)
        listener = null
        installStage = null
    }

    /** Implements lazy hooking of the listener into the stage.
     *
     *  The listener will be added to the stage's listeners only when - and as soon as -
     *  [this][KeyPressDispatcher] contains mappings.
     *  When all mappings are removed or cleared the listener is removed from the stage.
     */
    private fun checkInstall(forceRemove: Boolean = false) {
        if (listener == null || installStage == null) return
        if (listenerInstalled && (isEmpty() || isPaused || forceRemove)) {
            println(toString() + ": Removing listener" + (if(forceRemove) " for uninstall" else ""))
            listenerInstalled = false
            installStage!!.removeListener(listener)
        } else if (!listenerInstalled && !(isEmpty() || isPaused)) {
            println(toString() + ": Adding listener")
            installStage!!.addListener(listener)
            listenerInstalled = true
        }
    }

    /** Allows temporarily suspending this [KeyPressDispatcher] */
    var isPaused: Boolean = false
        set(value) {
            field = value
            checkInstall()
        }

}
