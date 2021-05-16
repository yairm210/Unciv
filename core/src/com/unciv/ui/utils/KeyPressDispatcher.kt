package com.unciv.ui.utils

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
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

    @ExperimentalStdlibApi
    override fun toString(): String {
        // debug helper
        return when {
            char == Char.MIN_VALUE -> Input.Keys.toString(code)
            char < ' ' -> "Ctrl-" + Char(char.toInt()+64)
            else -> "\"$char\""
        }
    }
}

class KeyPressDispatcher: HashMap<KeyCharAndCode, (() -> Unit)>() {
    private var checkpoint: Set<KeyCharAndCode> = setOf()

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

    override fun clear() {
        checkpoint = setOf()
        super.clear()
    }
    fun setCheckpoint() {
        checkpoint = keys.toSet()
    }
    fun revertToCheckPoint() {
        keys.minus(checkpoint).forEach { remove(it) }
    }
}
