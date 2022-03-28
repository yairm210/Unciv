package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage

/*
 * For now, many combination keys cannot easily be expressed.
 * Pressing Ctrl-Letter will arrive one event for Input.Keys.CONTROL_LEFT and one for the ASCII control code point
 *      so Ctrl-R can be handled using KeyCharAndCode('\u0012') or KeyCharAndCode.ctrl('R')
 * Pressing Alt-Something likewise will fire once for Alt and once for the unmodified keys with no indication Alt is held
 *      (Exception: international keyboard AltGr-combos)
 * An update supporting easy declarations for any modifier combos would need to use Gdx.input.isKeyPressed()
 * Gdx seems to omit support for a modifier mask (e.g. Ctrl-Alt-Shift) so we would need to reinvent this
 * 
 * Note: It is important that KeyCharAndCode is an immutable data class to support usage as HashMap key
 */

/**
 * Represents a key for use in an InputListener keyTyped() handler
 *
 * Example: KeyCharAndCode('R'), KeyCharAndCode(Input.Keys.F1)
 */
data class KeyCharAndCode(val char: Char, val code: Int) {
    /** helper 'cloning constructor' to allow feeding both fields from a factory function */
    private constructor(from: KeyCharAndCode): this(from.char, from.code)
    /** Map keys from a Char - will detect by keycode if one can be mapped, by character otherwise */
    constructor(char: Char): this(mapChar(char))
    /** express keys that only have a keyCode like F1 */
    constructor(code: Int): this(Char.MIN_VALUE, code)

    override fun toString(): String {
        // debug helper, but also used for tooltips
        fun fixedKeysToString(code: Int) = when (code) {
            Input.Keys.BACKSPACE -> "Backspace"  // Gdx displaying this as "Delete" is Bullshit!
            Input.Keys.FORWARD_DEL -> "Del"      // Likewise
            else -> Input.Keys.toString(code)
        }
        return when {
            char == Char.MIN_VALUE -> fixedKeysToString(code)
            this == ESC -> "ESC"
            char < ' ' -> "Ctrl-" + (char.toCode() + 64).makeChar()
            else -> "\"$char\""
        }
    }

    companion object {
        // Convenience shortcuts for frequently used constants
        /** Android back, assigns ESC automatically as well */
        val BACK = KeyCharAndCode(Input.Keys.BACK)
        /** Automatically assigned for [BACK] */
        val ESC = KeyCharAndCode(Input.Keys.ESCAPE)
        /** Assigns [NUMPAD_ENTER] automatically as well */
        val RETURN = KeyCharAndCode(Input.Keys.ENTER)
        /** Automatically assigned for [RETURN] */
        val NUMPAD_ENTER = KeyCharAndCode(Input.Keys.NUMPAD_ENTER)
        val SPACE = KeyCharAndCode(Input.Keys.SPACE)
        val BACKSPACE= KeyCharAndCode(Input.Keys.BACKSPACE)
        val DEL = KeyCharAndCode(Input.Keys.FORWARD_DEL)        // Gdx "DEL" is just plain wrong!
        /** Guaranteed to be ignored by [KeyPressDispatcher.set] and never to be generated for an actual event, used as fallback to ensure no action is taken */
        val UNKNOWN = KeyCharAndCode(Input.Keys.UNKNOWN)

        // Kludges because we got crashes: java.lang.NoSuchMethodError: 'int kotlin.CharCodeKt.access$getCode$p(char)'  
        fun Char.toCode() =
            try { code } catch (ex: Throwable) { null }
                ?: try { @Suppress("DEPRECATION") toInt() } catch (ex: Throwable) { null }
                ?: 0
        fun Int.makeChar() =
            try { Char(this) } catch (ex: Throwable) { null }
                ?: try { toChar() } catch (ex: Throwable) { null }
                ?: Char.MIN_VALUE

        /** mini-factory for control codes - case insensitive */
        fun ctrl(letter: Char) = KeyCharAndCode((letter.toCode() and 31).makeChar(),0)

        /** mini-factory for control codes from keyCodes */
        fun ctrlFromCode(keyCode: Int): KeyCharAndCode {
            val name = Input.Keys.toString(keyCode)
            if (name.length != 1 || !name[0].isLetter()) return KeyCharAndCode(Char.MIN_VALUE, keyCode)
            return ctrl(name[0])
        }

        /** mini-factory for KeyCharAndCode values to be compared by character, not by code */
        fun ascii(char: Char) = KeyCharAndCode(char.lowercaseChar(), 0)

        /** factory maps a Char to a keyCode if possible, returns a Char-based instance otherwise */
        fun mapChar(char: Char): KeyCharAndCode {
            val code = Input.Keys.valueOf(char.uppercaseChar().toString())
            return if (code == -1) KeyCharAndCode(char,0) else KeyCharAndCode(Char.MIN_VALUE, code)
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
 *
 *  @param name Optional name of the container screen or popup for debugging
 */
class KeyPressDispatcher(val name: String? = null) : HashMap<KeyCharAndCode, (() -> Unit)>() {
    private var checkpoint: Set<KeyCharAndCode> = setOf()       // set of keys marking a checkpoint
    private var listener: EventListener? = null     // holds listener code, captures its params in install() function
    private var listenerInstalled = false           // flag for lazy Stage.addListener()
    private var installStage: Stage? = null         // Keep stage passed by install() for lazy addListener and uninstall

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
        if (key == KeyCharAndCode.UNKNOWN) return
        super.put(key, action)
        // Make both Enter keys equivalent
        if (key == KeyCharAndCode.RETURN)
            super.put(KeyCharAndCode.NUMPAD_ENTER, action)
        // Likewise always match Back to ESC
        if (key == KeyCharAndCode.BACK)
            super.put(KeyCharAndCode.ESC, action)
        checkInstall()
    }
    override fun remove(key: KeyCharAndCode): (() -> Unit)? {
        if (key == KeyCharAndCode.UNKNOWN) return null
        val result = super.remove(key)
        if (key == KeyCharAndCode.RETURN)
            super.remove(KeyCharAndCode.NUMPAD_ENTER)
        if (key == KeyCharAndCode.BACK)
            super.remove(KeyCharAndCode.ESC)
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
    fun install(stage: Stage, checkIgnoreKeys: (() -> Boolean)? = null) {
        if (consoleLog)
            println("$this: install")
        if (installStage != null) uninstall()
        listener =
            object : InputListener() {
                override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                    val key = when {
                        event == null ->
                            KeyCharAndCode.UNKNOWN
                        Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) ->
                            KeyCharAndCode.ctrlFromCode(event.keyCode)
                        else ->
                            KeyCharAndCode(event.keyCode)
                    }

                    // see if we want to handle this key, and if not, let it propagate
                    if (!contains(key) || (checkIgnoreKeys?.invoke() == true)) {
                        if (consoleLog)
                            println("${this@KeyPressDispatcher}: $key not handled")
                        return super.keyDown(event, keycode)
                    }

                    // try-catch mainly for debugging. Breakpoints in the vicinity can make the event fire twice in rapid succession, second time the context can be invalid
                    if (consoleLog)
                        println("${this@KeyPressDispatcher}: handling $key")
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
        if (consoleLog)
            println("$this: uninstall")
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
            listenerInstalled = false
            if (consoleLog)
                println("$this: removeListener")
            installStage!!.removeListener(listener)
            if (consoleLog)
                println("$this: Listener removed")
        } else if (!listenerInstalled && !(isEmpty() || isPaused)) {
            if (consoleLog)
                println("$this: addListener")
            installStage!!.addListener(listener)
            listenerInstalled = true
            if (consoleLog)
                println("$this: Listener added")
        }
    }

    /** Allows temporarily suspending this [KeyPressDispatcher] */
    var isPaused: Boolean = false
        set(value) {
            field = value
            checkInstall()
        }

    companion object {
        // Control debug logging
        private const val consoleLog = false

        /** Tests presence of a physical keyboard - static here as convenience shortcut only */
        val keyboardAvailable = Gdx.input.isPeripheralAvailable(Input.Peripheral.HardwareKeyboard)
    }
}
